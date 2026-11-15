package com.abyssalloot;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 実サーバー上での結合テスト (GameTest).
 * <p>
 * 実行: {@code .\gradlew.bat --no-daemon --console=plain runGameTestServer} (全テスト実行後に自動終了)。
 * ゲーム内では {@code /test runall} でも実行できる。
 * <p>
 * 静的検証 (tools/verify.py) では分からない「実機で読み込まれるか・状態遷移が正しいか」を確認する。
 */
public final class AbyssGameTests {
    private AbyssGameTests() {}

    static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, AbyssalLootMod.MODID);

    /** tools/gen_data.py が生成する床 (石 9x9 + 上空の空気) */
    private static final Identifier FLOOR = id("gametest_floor");

    private record Spec(String name, Consumer<GameTestHelper> body, int maxTicks) {}

    private static final List<Spec> SPECS = List.of(
            new Spec("recipes_loaded", AbyssGameTests::recipesLoaded, 20),
            new Spec("damage_number_is_marker", AbyssGameTests::damageNumberIsMarker, 20),
            new Spec("start_and_retire", AbyssGameTests::startAndRetire, 200),
            new Spec("floor_clear_opens_gate", AbyssGameTests::floorClearOpensGate, 200),
            new Spec("bossrush_restores_level", AbyssGameTests::bossrushRestoresLevel, 200),
            new Spec("content_items_registered", AbyssGameTests::contentItemsRegistered, 20),
            new Spec("mix_consumable_applies", AbyssGameTests::mixConsumableApplies, 40),
            new Spec("kill_drops_trophy_and_zone_material", AbyssGameTests::killDropsContent, 40));

    public static void register(IEventBus modBus) {
        for (Spec s : SPECS) {
            FUNCTIONS.register(s.name(), () -> s.body());
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(AbyssGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        for (Spec s : SPECS) {
            // 出撃系は半径 20 のアリーナを建てるため、テストごとに環境を分けて別バッチ (順番に実行) にする
            var env = event.registerEnvironment(id("env_" + s.name()));
            var fn = ResourceKey.create(Registries.TEST_FUNCTION, id(s.name()));
            event.registerTest(id(s.name()),
                    new FunctionGameTestInstance(fn, new TestData<>(env, FLOOR, s.maxTicks(), 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, path);
    }

    // ------------------------------------------------------------------ テスト本体

    /** データパックのレシピが実際に読み込まれている (拡張子漏れ・旧書式の材料で全滅した前例がある) */
    private static void recipesLoaded(GameTestHelper h) {
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (String name : List.of("art_fuse_sword", "art_fuse_howling_claw", "shard_pouch", "shard_unpack_core",
                "con_flask", "con_godspeed", "tool_breacher", "tool_axe", "heal_s", "bomb_i", "shard_from_echo")) {
            var key = ResourceKey.create(Registries.RECIPE, id(name));
            if (recipes.byKey(key).isEmpty()) {
                throw h.assertionException("レシピが読み込まれていない: %s", key.identifier());
            }
        }
        // データパック内のレシピファイルが 1 件残らず読み込まれているか (1 件でも解析失敗すると欠ける)
        var loaded = new java.util.HashSet<String>();
        recipes.getRecipes().forEach(r -> {
            if (r.id().identifier().getNamespace().equals(AbyssalLootMod.MODID)) {
                loaded.add(r.id().identifier().getPath());
            }
        });
        var missing = new java.util.TreeSet<String>();
        h.getLevel().getServer().getResourceManager()
                .listResources("recipe", loc -> loc.getNamespace().equals(AbyssalLootMod.MODID)
                        && loc.getPath().endsWith(".json"))
                .keySet().forEach(loc -> {
                    String path = loc.getPath();
                    String name = path.substring("recipe/".length(), path.length() - ".json".length());
                    if (!loaded.contains(name)) missing.add(name);
                });
        if (!missing.isEmpty()) {
            throw h.assertionException("読み込みに失敗したレシピ (%s 件): %s", missing.size(), missing);
        }
        h.succeed();
    }

    /** 表示用アーマースタンドはマーカー (当たり判定なし) で、攻撃対象から外れる */
    private static void damageNumberIsMarker(GameTestHelper h) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(h.getLevel(), EntitySpawnReason.EVENT);
        if (stand == null) throw h.assertionException("アーマースタンドを生成できない");
        stand.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
        CombatUtil.makeMarker(stand);
        stand.addTag(DamageNumbers.TAG);
        h.getLevel().addFreshEntity(stand);
        if (!stand.isMarker()) throw h.assertionException("makeMarker 後も isMarker() が false");
        if (stand.getBbWidth() > 0.01f) throw h.assertionException("マーカーなのに当たり判定の幅がある: %s", stand.getBbWidth());
        if (CombatUtil.isValidTarget(stand)) throw h.assertionException("表示用の実体が攻撃対象になっている");
        stand.discard();
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        p.teleportTo(at.x, at.y, at.z);
        return p;
    }

    private static List<Mob> abyssMobsNear(GameTestHelper h, ServerPlayer p) {
        return h.getLevel().getEntitiesOfClass(Mob.class,
                p.getBoundingBox().inflate(ArenaManager.ARENA_R + 8), e -> e.isAlive() && LootHandler.hasAbyssTag(e));
    }

    private static void check(GameTestHelper h, boolean ok, String msg, Object... args) {
        if (!ok) throw h.assertionException(msg, args);
    }

    /** 出撃で第1層のウェーブが湧き、リタイアで出撃ロックと残存MOBが片付く */
    private static void startAndRetire(GameTestHelper h) {
        ServerPlayer p = player(h);
        RunManager.startRun(p);
        check(h, p.getData(ModAttachments.RUN_LOCK.get()) == 1, "出撃ロックが立っていない");
        check(h, p.getData(ModAttachments.FLOOR.get()) == 1, "第1層になっていない: %s", p.getData(ModAttachments.FLOOR.get()));
        check(h, p.getData(ModAttachments.WAVE_TOTAL.get()) > 0, "ウェーブのノルマが 0");
        h.runAfterDelay(10, () -> {
            check(h, !abyssMobsNear(h, p).isEmpty(), "出撃後に深淵MOBが 1 体もいない");
            RunManager.retire(p);
            check(h, p.getData(ModAttachments.RUN_LOCK.get()) == 0, "リタイア後も出撃ロックが残っている");
            h.runAfterDelay(5, () -> {
                int left = abyssMobsNear(h, p).size();
                check(h, left == 0, "リタイア後に深淵MOBが %s 体残っている", left);
                h.succeed();
            });
        });
    }

    /** ウェーブを全滅させると (撃破イベントを経由しない消滅でも) 転移門が開く */
    private static void floorClearOpensGate(GameTestHelper h) {
        ServerPlayer p = player(h);
        RunManager.startRun(p);
        h.runAfterDelay(10, () -> {
            check(h, p.getData(ModAttachments.PORTAL_OPEN.get()) == 0, "敵が残っているのに門が開いている");
            RunManager.clearAbyssMobs(p);
            h.runAfterDelay(2, () -> {
                // 取りこぼし補正は 3 回連続の不足で発動する (一時的なチャンク外れでの早期開放防止)
                for (int i = 0; i < 4; i++) RunManager.tickWaveProgress(p);
                check(h, p.getData(ModAttachments.PORTAL_OPEN.get()) == 1,
                        "全滅させたのに門が開かない (討伐 %s / ノルマ %s)",
                        p.getData(ModAttachments.FLOOR_KILLS.get()), p.getData(ModAttachments.WAVE_TOTAL.get()));
                RunManager.retire(p);
                h.succeed();
            });
        });
    }

    /** bossrush の一時 Lv10 は終了時に本来のレベルへ戻る */
    private static void bossrushRestoresLevel(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setData(ModAttachments.LEVEL.get(), 3);
        p.setData(ModAttachments.MODE.get(), "bossrush");
        RunManager.startRun(p);
        check(h, p.getData(ModAttachments.BASE_LEVEL.get()) == 3, "本来のレベルが保存されていない: %s",
                p.getData(ModAttachments.BASE_LEVEL.get()));
        h.runAfterDelay(5, () -> {
            RunManager.retire(p);
            check(h, p.getData(ModAttachments.LEVEL.get()) == 3, "レベルが戻っていない: %s",
                    p.getData(ModAttachments.LEVEL.get()));
            check(h, p.getData(ModAttachments.BASE_LEVEL.get()) == 0, "保存レベルが残っている");
            p.setData(ModAttachments.MODE.get(), "standard");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ 追加コンテンツ

    /** abyssalloot_content.json の全アイテムと、区域ドロップ表の参照先が登録されている */
    private static void contentItemsRegistered(GameTestHelper h) {
        var missing = new java.util.TreeSet<String>();
        for (String id : ModItems.CONTENT_ITEMS.keySet()) {
            if (ModItems.byId(id) == null) missing.add(id);
        }
        for (var pool : Content.ZONE_DROPS.values()) {
            for (String id : pool) if (ModItems.byId(id) == null) missing.add(id);
        }
        for (var set : Content.ARMOR_SETS) {
            for (String piece : List.of("helm", "chest", "legs", "boots")) {
                String id = "abyss_" + set.id() + "_" + piece;
                if (ModItems.byId(id) == null) missing.add(id);
            }
        }
        check(h, missing.isEmpty(), "登録されていないアイテム: %s", missing);
        int total = ModItems.CONTENT_ITEMS.size() + Content.ARMOR_SETS.size() * 4;
        check(h, total == 100, "追加アイテムが %s 件 (期待: 100)", total);
        h.succeed();
    }

    /** mix: 消耗品を使うと、記述どおりの回復とバフが付く */
    private static void mixConsumableApplies(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setHealth(4f);
        check(h, Consumables.useConsumable(p, "mix:heal=50;fx=speed,60,1"), "mix 消耗品を使えなかった");
        var speed = p.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
        check(h, speed != null, "移動速度上昇が付いていない");
        check(h, speed.getAmplifier() == 1, "強度が違う: %s", speed.getAmplifier());
        check(h, speed.getDuration() > 55 * 20, "持続が短い: %s tick", speed.getDuration());
        check(h, p.getHealth() > 4f, "回復していない: %s", p.getHealth());
        // 定義済みの全消耗品が例外なく使える
        for (var c : Content.CONSUMABLES) {
            check(h, Consumables.useConsumable(p, c.kind()), "使えない消耗品: %s", c.id());
        }
        p.removeAllEffects();
        h.succeed();
    }

    /** 層主を倒すと、その敵の魔物素材と現在の区域の素材が手に入る */
    private static void killDropsContent(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setData(ModAttachments.FLOOR.get(), 12);  // 溶鉄の炉
        var slime = ModEntities.SLIME.get().create(h.getLevel(), EntitySpawnReason.EVENT);
        check(h, slime != null, "スライムを生成できない");
        ItemStack trophy = ContentHooks.trophyOf(slime);
        check(h, !trophy.isEmpty(), "スライムの魔物素材が無い");
        ContentHooks.rollKillDrops(p, slime, 3, p.getRandom());
        check(h, p.getInventory().contains(trophy), "層主撃破で魔物素材が手に入っていない");
        var forge = new java.util.HashSet<>(Content.ZONE_DROPS.get("forge"));
        int zoneMats = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (forge.contains(id)) zoneMats += st.getCount();
        }
        check(h, zoneMats == 2, "区域素材が %s 個 (期待: 層主は 2 個確定)", zoneMats);
        // どの敵にも魔物素材が定義されている
        for (String mob : Content.TROPHY_BY_MOB.keySet()) {
            check(h, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id(mob)),
                    "魔物素材の対象が存在しない敵: %s", mob);
        }
        p.getInventory().clearContent();
        slime.discard();
        h.succeed();
    }
}
