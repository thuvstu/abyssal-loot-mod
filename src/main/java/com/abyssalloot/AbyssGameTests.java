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
            new Spec("kill_drops_trophy_and_zone_material", AbyssGameTests::killDropsContent, 40),
            new Spec("endgame_craft_only", AbyssGameTests::endgameCraftOnly, 20),
            new Spec("nocturne_sequence_casts_all_skills", AbyssGameTests::nocturneSequence, 120),
            new Spec("aibou_growth_and_limit_break", AbyssGameTests::aibouGrowth, 20),
            new Spec("weapon_look_follows_forge_and_trait", AbyssGameTests::weaponLookSync, 20),
            new Spec("arena_restores_terrain_without_drops", AbyssGameTests::arenaRestoresTerrain, 60),
            new Spec("vault_overflow_and_stash", AbyssGameTests::vaultOverflowAndStash, 20),
            new Spec("gacha_pull_and_pity", AbyssGameTests::gachaPullAndPity, 20),
            new Spec("workshop_exact_stats", AbyssGameTests::workshopExactStats, 20),
            new Spec("excavator_and_lumber_axe", AbyssGameTests::excavatorAndLumberAxe, 20));

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

    /** アリーナは設置前の地形を保存し、リタイアで中身ごと戻す。整地でアイテムが散らばらない */
    private static void arenaRestoresTerrain(GameTestHelper h) {
        ServerPlayer p = player(h);
        var level = h.getLevel();
        net.minecraft.core.BlockPos base = p.blockPosition();
        net.minecraft.core.BlockPos chestPos = base.offset(3, 0, 3);
        net.minecraft.core.BlockPos stonePos = base.offset(-3, 0, 2);
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));
        }
        level.setBlock(stonePos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(stonePos.above(), net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), 3);
        RunManager.startRun(p);
        check(h, !level.getBlockState(chestPos).is(net.minecraft.world.level.block.Blocks.CHEST), "整地でチェストが消えていない");
        h.runAfterDelay(5, () -> {
            var box = new net.minecraft.world.phys.AABB(base).inflate(ArenaManager.ARENA_R + 2, 12, ArenaManager.ARENA_R + 2);
            // 設置 (5 tick 前) より後に湧いたアイテムだけを数える (別のテストの残りを数えない)
            var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                    e -> e.getAge() <= 6);
            check(h, dropped.isEmpty(), "アリーナ設置でアイテムが落ちた: %s",
                    dropped.stream().map(e -> e.getItem().toString() + "@" + e.blockPosition().subtract(base)).toList());
            RunManager.retire(p);
            check(h, level.getBlockState(chestPos).is(net.minecraft.world.level.block.Blocks.CHEST), "リタイア後にチェストが戻っていない");
            var be = level.getBlockEntity(chestPos);
            boolean kept = be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c2
                    && c2.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && c2.getItem(0).getCount() == 5;
            check(h, kept, "チェストの中身が戻っていない");
            check(h, level.getBlockState(stonePos.above()).is(net.minecraft.world.level.block.Blocks.TORCH), "松明が戻っていない");
            h.runAfterDelay(3, () -> {
                int after = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size();
                check(h, after == 0, "地形の復元でアイテムが %s 個落ちた", after);
                h.succeed();
            });
        });
    }

    /** 手持ちが満杯でも mod のアイテムは保管庫へ入る。一括収納は区分ごとに振り分け、同じ物をまとめる */
    private static void vaultOverflowAndStash(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);  // クリエイティブだと add が常に成功する
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
        LootHandler.giveOrDrop(p, new ItemStack(ModItems.ANCIENT_STONE.get(), 3));
        var box = p.getBoundingBox().inflate(4);
        int drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size();
        check(h, drops == 0, "満杯時に mod のアイテムが地面に落ちた (%s 個)", drops);
        var mats = Vault.list(p, 2);
        check(h, mats.get(0).is(ModItems.ANCIENT_STONE.get()) && mats.get(0).getCount() == 3, "保管庫の素材に入っていない: %s", mats.get(0));
        inv.setItem(20, new ItemStack(ModItems.ANCIENT_STONE.get(), 2));
        inv.setItem(21, new ItemStack(ModItems.itemOf(GameData.WeaponStyle.SWORD)));
        int moved = Vault.stashAll(p, false);
        check(h, moved == 3, "一括収納の個数が違う: %s", moved);
        check(h, inv.getItem(20).isEmpty() && inv.getItem(21).isEmpty(), "一括収納の後も手持ちに残っている");
        check(h, Vault.list(p, 2).get(0).getCount() == 5, "同じ素材がまとまっていない: %s", Vault.list(p, 2).get(0));
        check(h, !Vault.list(p, 0).get(0).isEmpty(), "武器が武器の区分に入っていない");
        h.succeed();
    }

    /** ガチャ: 10 連で欠片 270 を払い 10 個受け取る。レア以上が 1 つ以上。天井 (60 回) で神話が出る */
    private static void gachaPullAndPity(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var inv = p.getInventory();
        inv.clearContent();
        inv.setItem(0, new ItemStack(ModItems.SOUL_SHARD.get(), 64));
        inv.setItem(1, new ItemStack(ModItems.SOUL_SHARD.get(), 64));
        inv.setItem(2, new ItemStack(ModItems.SOUL_SHARD.get(), 64));
        inv.setItem(3, new ItemStack(ModItems.SOUL_SHARD.get(), 64));
        inv.setItem(4, new ItemStack(ModItems.SOUL_SHARD.get(), 64));
        Gacha.pull(p, 10, false);
        check(h, Gacha.countShards(p) == 320 - 270, "欠片の残りが違う: %s", Gacha.countShards(p));
        int gear = 0, best = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (ModItems.styleOf(st.getItem()) != null || ModItems.slotOf(st.getItem()) != null) {
                gear++;
                best = Math.max(best, GameData.rarityRank(GearData.rarityOf(st)));
            }
        }
        check(h, gear == 10, "10 連で受け取った装備が %s 個", gear);
        check(h, best >= 2, "10 連でレア以上が出ていない");
        Gacha.pull(p, 10, false);  // 欠片不足 (残り 50) なので何も起きない
        check(h, Gacha.countShards(p) == 50, "欠片不足なのに引けた: 残り %s", Gacha.countShards(p));
        p.setData(ModAttachments.GACHA_PITY.get(), Gacha.PITY - 1);
        inv.clearContent();
        Gacha.pull(p, 1, true);
        check(h, GameData.rarityRank(GearData.rarityOf(inv.getItem(0))) == 4, "天井で神話が出ない: %s", inv.getItem(0));
        check(h, p.getData(ModAttachments.GACHA_PITY.get()) == 0, "神話の後に天井の回数が戻っていない");
        h.succeed();
    }

    /** 装備工房: 指定した希少度・特性・鍛造・接辞の値がそのまま付く */
    private static void workshopExactStats(GameTestHelper h) {
        var affixes = new java.util.LinkedHashMap<String, Double>();
        affixes.put("critPct", 9.0);
        affixes.put("damagePct", 27.0);
        affixes.put("nonexistent", 5.0);
        var st = GearFactory.makeCustom(GameData.WeaponStyle.KATANA, null, null, "mythic", "infernus", null, 4, affixes, "試し斬り");
        check(h, "mythic".equals(GearData.rarityOf(st)), "希少度が違う: %s", GearData.rarityOf(st));
        check(h, "infernus".equals(GearData.traitOf(st)), "特性が違う: %s", GearData.traitOf(st));
        check(h, GearData.forgeLv(st) == 4, "鍛造が違う: %s", GearData.forgeLv(st));
        check(h, GearData.affixOf(st, "critPct") == 9.0 && GearData.affixOf(st, "damagePct") == 27.0, "接辞の値が違う");
        check(h, GearData.affixOf(st, "nonexistent") == 0, "存在しない接辞が付いた");
        var armor = GearFactory.makeCustom(null, ModItems.ABYSS_HELM.get(), "void", "legendary", null, null, 0,
                java.util.Map.of("maxHpPct", 12.0), "");
        check(h, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(armor.getItem()).getPath().equals("abyss_void_helm"),
                "セット防具の部位になっていない: %s", armor.getItem());
        h.succeed();
    }

    /** 掘削槌は 3×3 を掘り、伐採斧はつながった原木をまとめて伐る */
    private static void excavatorAndLumberAxe(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var level = h.getLevel();
        var base = p.blockPosition().offset(0, 0, 3);
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlock(base.offset(x, y, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            }
        }
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.ABYSS_EXCAVATOR.get()));
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(base.above()));
        p.gameMode.destroyBlock(base.above());
        int left = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                if (!level.getBlockState(base.offset(x, y, 0)).isAir()) left++;
            }
        }
        check(h, left == 0, "掘削槌で 3×3 が掘れていない (残り %s)", left);
        var tree = p.blockPosition().offset(4, 0, 0);
        for (int y = 0; y < 6; y++) {
            level.setBlock(tree.above(y), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 3);
        }
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.ABYSS_LUMBER_AXE.get()));
        p.gameMode.destroyBlock(tree);
        int logs = 0;
        for (int y = 0; y < 6; y++) if (level.getBlockState(tree.above(y)).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) logs++;
        check(h, logs == 0, "伐採斧で原木が %s 本残った", logs);
        // 伐った原木・掘った石のドロップを片付ける (近くの別のテストの数え間違いを防ぐ)
        for (var it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                p.getBoundingBox().inflate(12))) it.discard();
        h.succeed();
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
        check(h, total == 146, "追加アイテムが %s 件 (期待: 146)", total);
        h.succeed();
    }

    /** 武器の見た目切替: 鍛造レベルと特性が CUSTOM_MODEL_DATA (floats[0] / strings[0]) に写る */
    private static void weaponLookSync(GameTestHelper h) {
        var stack = GearFactory.makeWeapon(GameData.WeaponStyle.DROPPABLE[0], h.getLevel().getRandom(), 0, 1, 0);
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString("abyss_trait", "infernus");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        GearData.setForgeLv(stack, 3);
        var cmd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        check(h, cmd != null, "CUSTOM_MODEL_DATA が付いていない");
        check(h, cmd.getFloat(0) != null && cmd.getFloat(0) == 3f, "floats[0] が鍛造レベルでない: %s", cmd);
        check(h, "infernus".equals(cmd.getString(0)), "strings[0] が特性でない: %s", cmd);
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

    /** エンドコンテンツ武器: 最上位クラフトのレシピがあり、ドロップ・断片の抽選には出ず、完成品は神話級 */
    private static void endgameCraftOnly(GameTestHelper h) {
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (String name : List.of("endgame_nocturne", "endgame_aibou", "endgame_aibou_limit_stone")) {
            check(h, recipes.byKey(ResourceKey.create(Registries.RECIPE, id(name))).isPresent(),
                    "最上位クラフトのレシピが無い: %s", name);
        }
        for (var w : GameData.WeaponStyle.DROPPABLE) {
            check(h, !w.isEndgame(), "エンドコンテンツ武器がドロップ対象に入っている: %s", w.id);
        }
        check(h, !ModItems.ART_SHARDS.containsKey(GameData.WeaponStyle.NOCTURNE), "ノクターンに奥義の断片がある");
        check(h, ModItems.styleOf(ModItems.ABYSS_NOCTURNE.get()) == GameData.WeaponStyle.NOCTURNE, "ノクターンの武器種が逆引きできない");
        ServerPlayer p = player(h);
        var noct = new ItemStack(ModItems.ABYSS_NOCTURNE.get());
        Endgame.stampMythic(noct, GameData.WeaponStyle.NOCTURNE, p);
        check(h, GearData.rarityOf(noct).equals("mythic"), "クラフト品が神話級でない: %s", GearData.rarityOf(noct));
        check(h, noct.getHoverName().getString().contains("ノクターン"), "ノクターンの銘が無い: %s", noct.getHoverName().getString());
        check(h, GearData.curseOf(noct) == null, "エンドコンテンツ武器に呪いが付いた");
        h.succeed();
    }

    /** ノクターン: 空振りでカウント・メーターが進み、メーター100の11+1桁詠唱を正しく入力すると全スキルが発動する */
    private static void nocturneSequence(GameTestHelper h) {
        ServerPlayer p = player(h);
        var noct = new ItemStack(ModItems.ABYSS_NOCTURNE.get());
        Endgame.stampMythic(noct, GameData.WeaponStyle.NOCTURNE, p);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, noct);
        var zombie = EntityType.ZOMBIE.create(h.getLevel(), EntitySpawnReason.EVENT);
        check(h, zombie != null, "ゾンビを生成できない");
        Vec3 at = h.absoluteVec(new Vec3(6.5, 1, 4.5));
        zombie.setPos(at.x, at.y, at.z);
        zombie.setPersistenceRequired();
        h.getLevel().addFreshEntity(zombie);
        float hp0 = zombie.getHealth();
        Nocturne.onAirSwing(p);
        ItemStack held = p.getMainHandItem();
        check(h, Nocturne.countOf(held) == 1 && Nocturne.meterOf(held) == 1,
                "空振りでカウント/メーターが進まない: %s/%s", Nocturne.countOf(held), Nocturne.meterOf(held));
        check(h, zombie.getHealth() < hp0, "空振りの律動が最寄りの敵に当たっていない");
        // カウント4・メーター100 から詠唱 → 12 桁
        Endgame.putInts(held, java.util.Map.of(Nocturne.COUNT, 4, Nocturne.METER, 100), 4);
        p.setData(ModAttachments.LIGHT_CD.get(), 0f);
        Nocturne.onRightClick(p);
        int[] seq = Nocturne.debugSequence(p);
        check(h, seq != null && seq.length == 12, "メーター100・カウント4 の数列が 12 桁でない: %s",
                seq == null ? "null" : seq.length);
        for (int d : seq) Nocturne.onDigit(p, d);
        check(h, !Nocturne.inInput(p), "全桁入力後も入力モードのまま");
        check(h, Nocturne.countOf(held) == 0 && Nocturne.meterOf(held) == 0, "詠唱完了でカウント/メーターが戻らない");
        check(h, p.getData(ModAttachments.LIGHT_CD.get()) >= 30f, "メーター解放詠唱のCTが2倍になっていない: %s",
                p.getData(ModAttachments.LIGHT_CD.get()));
        check(h, Endgame.shieldOf(p) > 0, "⑥/⑫ のHPシールドが付いていない");
        check(h, Endgame.hasUndying(p), "⑫ の不死が付いていない");
        // 誤入力で即終了する
        p.setData(ModAttachments.LIGHT_CD.get(), 0f);
        Nocturne.onRightClick(p);
        int[] seq2 = Nocturne.debugSequence(p);
        check(h, seq2 != null && seq2.length == 2, "カウント0 の数列が 2 桁でない");
        Nocturne.onDigit(p, (seq2[0] + 1) % 10);
        check(h, !Nocturne.inInput(p), "誤入力で詠唱が終わらない");
        // スロット (⑦) の遅延処理が走り切ってから片付ける
        h.runAfterDelay(40, () -> {
            zombie.discard();
            p.getInventory().clearContent();
            h.succeed();
        });
    }

    /** 相棒の剣: 器XPで器Lvが上限まで育ち、限界突破の楔で段階と威力が上がる */
    private static void aibouGrowth(GameTestHelper h) {
        ServerPlayer p = player(h);
        var sword = new ItemStack(ModItems.ABYSS_AIBOU.get());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        ItemStack held = p.getMainHandItem();
        double m0 = Aibou.damageMult(held);
        Aibou.addXp(p, held, 100000);
        check(h, Aibou.lvOf(held) == Aibou.capOf(0), "器Lvが上限で止まらない: %s", Aibou.lvOf(held));
        check(h, Aibou.damageMult(held) > m0, "器Lvで威力が伸びていない");
        var stones = new ItemStack(ModItems.AIBOU_LIMIT_STONE.get(), 1);
        check(h, Aibou.limitBreak(p, stones), "上限到達で限界突破できない");
        check(h, Aibou.stageOf(held) == 1 && stones.isEmpty(), "段階が上がらない/楔が消費されない");
        check(h, !Aibou.limitBreak(p, new ItemStack(ModItems.AIBOU_LIMIT_STONE.get(), 5)),
                "器Lvが上限未満なのに限界突破できた");
        p.getInventory().clearContent();
        h.succeed();
    }
}
