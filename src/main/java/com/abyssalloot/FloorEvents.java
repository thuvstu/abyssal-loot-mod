package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/** 層間イベントまわり: 休息/誓約/商店/鍛造/宝箱・購入・ランダムイベント・遺物/技能の提示.
 * RunManager から分離。
 */
public final class FloorEvents {
    private FloorEvents() {}

    /* ---------------- 層間イベント ---------------- */

    /** 魂の欠片の所持数 (汎用素材数え替えの別名) */
    public static int countShards(ServerPlayer player) {
        return RunManager.countMats(player, ModItems.SOUL_SHARD.get());
    }

    /** 魂の欠片をn個消費 (不足ならfalse) */
    public static boolean takeShards(ServerPlayer player, int n) {
        return RunManager.takeMats(player, ModItems.SOUL_SHARD.get(), n);
    }

    /** 層クリア時に選択肢を提示 (原作の rest/forge/oath/merchant) */
    public static void offerEvent(ServerPlayer player) {
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) return;
        // 商店の品揃えを生成。価格は原作 merchant (power比例) の欠片版: 見本の威力から算出
        var styles = GameData.WeaponStyle.values();
        var rand = player.getRandom();
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        var shop = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var st = styles[rand.nextInt(styles.length)];
            int price = Math.max(2, (GearData.powerOf(GearFactory.makeWeapon(st, rand, StatCalc.luckOf(player), floor, 0)) + 7) / 15);
            shop.add(st.id + ":" + price);
        }
        player.setData(ModAttachments.SHOP.get(), String.join(",", shop));
        var msg = Component.literal("§e◆ 層間イベント (1つ選択): ");
        msg.append(btn("§a[休息 HP38%+魂25]", "/abyss event rest"));
        msg.append(btn("§5[血の誓約 祝福]", "/abyss event oath"));
        msg.append(btn("§6[商店 欠片で購入]", "/abyss event shop"));
        msg.append(btn("§b[遺された宝箱]", "/abyss event forge"));
        player.sendSystemMessage(msg);
        player.sendSystemMessage(Component.literal(
                "§7所持欠片: " + countShards(player) + "個 §8| §7武器合成は /abyss merge (両手にAbyss武器)"));
        // 選択画面 (GUI) を自動で開く。チャットボタンは後から選び直す代替手段として残す
        openEventScreen(player, "event", "◆ 層間イベント — 1つ選択 ◆",
                List.of("rest", "oath", "shop", "forge"),
                List.of("休息", "血の誓約", "商店", "遺された宝箱"),
                List.of("HP38%回復・魂+25", "HP18%を捧げて祝福を1つ選ぶ",
                        "欠片で武器を購入 (3点)・§7/abyss buy 1-3§r",
                        "層+1相当の装備を1点入手 (層主層はボーナス)"),
                "所持欠片 " + countShards(player) + "個 §8| ESCで後で選ぶ (選ぶまで消えない)",
                true);
    }

    /** 汎用選択画面 (FloorEventScreen) をクライアントへ開かせる */
    private static void openEventScreen(ServerPlayer player, String mode, String title,
                                        List<String> ids, List<String> names, List<String> descs,
                                        String footer, boolean portalOpen) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ModPackets.FloorEventSync(mode, title, ids, names, descs, footer, portalOpen));
    }

    private static Component btn(String label, String cmd) {
        return Component.literal(label + " ").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withUnderlined(true));
    }

    public static void doEvent(ServerPlayer player, String kind) {
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) {
            player.sendSystemMessage(Component.literal("§cこの層のイベントは使用済み"));
            return;
        }
        switch (kind) {
            case "rest" -> {
                // 原作: 最大HPの38%回復 + 魂ゲージ25
                completeEvent(player);
                RunManager.heal(player, player.getMaxHealth() * 0.38);
                RunManager.addGauge(player, 25);
                player.sendSystemMessage(Component.literal("§a休息した。HP38%回復・魂+25。"));
            }
            case "oath" -> {
                // 原作: 最大HPの18%を捧げ、祝福を1つ選ぶ (盟約モードでは宝箱に差し替わる)
                if (RunManager.isCovenant(player)) {
                    doForgeCrate(player);
                    break;
                }
                completeEvent(player);
                float cost = player.getMaxHealth() * 0.18f;
                if (player.getHealth() > cost + 1) {
                    player.hurtServer((ServerLevel) player.level(),
                            player.level().damageSources().magic(), cost);
                }
                player.sendSystemMessage(Component.literal("§5血の誓約… 最大HP18%を捧げた。祝福を選べ"));
                // 既に祝福の選択中 (提示済み or 未選択残数が残る) なら上書きせず後回しにする
                if (!player.getData(ModAttachments.OFFER.get()).isBlank()
                        || RunManager.pendingBoonsOf(player) > 0) {
                    RunManager.addPendingBoons(player, 1);
                    player.sendSystemMessage(Component.literal(
                            "§7現在の選択のあとに (残り" + RunManager.pendingBoonsOf(player) + "件)"));
                } else {
                    BoonHandler.offerBoons(player);
                }
            }
            case "shop" -> {
                String raw = player.getData(ModAttachments.SHOP.get());
                if (raw == null || raw.isBlank()) {
                    player.sendSystemMessage(Component.literal("§c商品なし"));
                    return;
                }
                // 商店GUIを直接開く (購入はGUIのボタン→doBuy。チャット購入も可)
                player.openMenu(new SimpleMenuProvider(
                        (id, inv, pl) -> new ShopMenu(id, inv),
                        Component.literal("商店")));
                player.sendSystemMessage(Component.literal(
                        "§6商店を開いた (所持欠片" + countShards(player) + "／チャット購入は /abyss buy 1〜3)"));
            }
            case "forge" -> {
                // 原作「遺された宝箱」: 第(層+1)層相当の装備 (層主の層はボーナス130)
                doForgeCrate(player);
            }
            default -> player.sendSystemMessage(Component.literal("§crest/oath/shop/forge で指定"));
        }
    }

    /** 原作の遺された宝箱/禁呪の宝箱: 層相当の装備を1点得る (鍛冶場ボーナス付き) */
    public static void doForgeCrate(ServerPlayer player) {
        completeEvent(player);
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        boolean bossFloor = floor % 5 == 0;
        var styles = GameData.WeaponStyle.values();
        var st = styles[player.getRandom().nextInt(styles.length)];
        ItemStack drop;
        if (player.getRandom().nextDouble() < 0.4) {
            drop = GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), floor + 1,
                    bossFloor ? 130 : 65);
        } else {
            drop = GearFactory.makeWeapon(st, player.getRandom(), StatCalc.luckOf(player), floor + 1,
                    bossFloor ? 130 : 65);
        }
        player.getInventory().add(drop);
        player.sendSystemMessage(Component.literal(
                "§b宝箱を開けた: " + drop.getHoverName().getString()));
        if ("mythic".equals(GearData.rarityOf(drop)) || "legendary".equals(GearData.rarityOf(drop))) {
            player.sendSystemMessage(Component.literal("§d★ 高希少度の装備だ！ (/abyss keep で継承可能)"));
        }
    }

    /**
     * 武器合成マージ (原作 forgeItems の MC 対応版)。
     * 2点の同武器種を合成し、鍛造+1 (最大5)。費用は欠片 3+鍛造Lv×2+威力/25
     * (+原作の素材種別: レア以上=古代石×1 / レジェンド以上=混沌×1 / 神話絡み=虚空×2)。
     * 接辞は両者の最善値を ×1.05 で統合 (原作のモジュラー合成)、特性は本体優先・無ければ継承。
     * Lv3以上は「神錬・」、Lv5は「覚醒・」の銘が付く (原作の forgedCount 命名に対応)。
     * 鍛造画面のスロットからも呼べるよう、任意の2スタックで動作する。
     */
    public static void doMerge(ServerPlayer player) {
        doMergeStacks(player, player.getMainHandItem(), player.getOffhandItem());
    }

    public static void doMergeStacks(ServerPlayer player,
                                     ItemStack main,
                                     ItemStack sub) {
        boolean bothWeapons = ModItems.styleOf(main.getItem()) != null
                && ModItems.styleOf(sub.getItem()) != null;
        boolean bothArmor = ModItems.slotOf(main.getItem()) != null
                && main.getItem() == sub.getItem();
        if (!bothWeapons && !bothArmor) {
            player.sendSystemMessage(Component.literal("§c合成にはAbyss武器2点か同種の深淵防具2点が必要 (両手持ち or 鍛造画面)"));
            return;
        }
        int lv = GearData.forgeLv(main);
        if (lv >= 5) {
            player.sendSystemMessage(Component.literal("§cこの武器は鍛造MAX (覚醒)"));
            return;
        }
        int cost = 3 + lv * 2 + (GearData.powerOf(main) + GearData.powerOf(sub) + 12) / 25;
        if (countShards(player) < cost) {
            player.sendSystemMessage(Component.literal("§c欠片" + cost + "個が足りない (所持" + countShards(player) + ")"));
            return;
        }
        // 原作の素材種別コスト (希少度の高い方で判定)。不足時は欠片を消費しない
        String mainRarity = GearData.rarityOf(main);
        String subRarity = GearData.rarityOf(sub);
        String top = GameData.rarityRank(subRarity) > GameData.rarityRank(mainRarity)
                ? subRarity : mainRarity;
        Item matItem = null;
        int matCount = 0;
        String matName = "";
        var mat = GameData.mergeMaterial(top);
        if (mat != null) {
            matItem = switch (mat.id()) {
                case "void" -> ModItems.VOID_ESSENCE.get();
                case "chaos" -> ModItems.CHAOS_GEM.get();
                default -> ModItems.ANCIENT_STONE.get();
            };
            matCount = mat.count();
            matName = mat.name();
        }
        if (matItem != null && RunManager.countMats(player, matItem) < matCount) {
            player.sendSystemMessage(Component.literal("§c" + matName + "が足りない (精鋭・番人・層主が落とす)"));
            return;
        }
        takeShards(player, cost);
        if (matItem != null) RunManager.takeMats(player, matItem, matCount);
        sub.shrink(1);
        // 原作のモジュラー合成: 両方の接辞から最善値を採用し ×1.05 (上位8件まで)
        var merged = new LinkedHashMap<String, Double>();
        var ma = GearData.affixMap(main);
        var sa = GearData.affixMap(sub);
        var keys = new LinkedHashSet<String>();
        keys.addAll(ma.keySet());
        keys.addAll(sa.keySet());
        for (String k : keys) {
            double best = Math.max(ma.getOrDefault(k, 0.0), sa.getOrDefault(k, 0.0));
            if (best > 0) merged.put(k, Math.round(best * 1.05 * 10) / 10.0);
        }
        var ordered = new ArrayList<>(merged.entrySet());
        ordered.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        var tag = main.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        if (!ordered.isEmpty()) {
            var sb = new StringBuilder();
            int n = 0;
            for (var e : ordered) {
                if (n >= 8) break;
                if (!sb.isEmpty()) sb.append(";");
                sb.append(e.getKey()).append(":").append(e.getValue());
                n++;
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        // 原作の高ランク採用: 素材側の希少度が高ければ引き継ぐ。特性も本体優先・無ければ継承
        if (GameData.rarityRank(GearData.rarityOf(sub)) > GameData.rarityRank(GearData.rarityOf(main))) {
            tag.putString("abyss_rarity", GearData.rarityOf(sub));
        }
        String subTrait = GearData.traitOf(sub);
        if (GearData.traitOf(main) == null && subTrait != null) tag.putString("abyss_trait", subTrait);
        main.set(DataComponents.CUSTOM_DATA,
                CustomData.of(tag));
        GearData.setForgeLv(main, lv + 1);
        if (lv + 1 >= 5) {
            var a = GameData.achievementById("forge-max");
            Achievements.grantAch(player, "forge-max", a.title(), GameData.shardsForReward(a.reward()));
        }
        String nowTrait = GearData.traitOf(main);
        String hover = main.getHoverName().getString().replaceAll("^[§0-9a-f]+(覚醒|神錬)・", "");
        String lead = "";
        var leadM = Pattern.compile("^(§[0-9a-f])+").matcher(hover);
        if (leadM.find()) lead = leadM.group();
        String body = hover.substring(lead.length());
        if (nowTrait != null && !body.contains("〔")) body += "〔" + GearData.traitName(nowTrait) + "〕";
        String prefix = lv + 1 >= 5 ? "§d覚醒・" : lv + 1 >= 3 ? "§b神錬・" : "";
        String newName = (prefix.isEmpty() ? lead : prefix) + body;
        if (!newName.equals(main.getHoverName().getString())) {
            main.set(DataComponents.CUSTOM_NAME, Component.literal(newName));
        }
        GearData.refreshLore(main);
        player.sendSystemMessage(Component.literal(
                "§b合成！ " + main.getHoverName().getString() + " 鍛造+" + (lv + 1)
                        + " 威力" + GearData.powerOf(main) + " (欠片-" + cost + ")"));
    }

    /** 層間イベントの完了記録 (実績「祭壇の巡礼者」/5回、「祭壇の常連」/10回) */
    public static void completeEvent(ServerPlayer player) {
        player.setData(ModAttachments.EVENT_DONE.get(), 1);
        int total = player.getData(ModAttachments.EVENT_TOTAL.get()) + 1;
        player.setData(ModAttachments.EVENT_TOTAL.get(), total);
        if (total >= 5) {
            var a = GameData.achievementById("event-five");
            Achievements.grantAch(player, "event-five", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (total >= 10) {
            var a = GameData.achievementById("event-ten");
            Achievements.grantAch(player, "event-ten", a.title(), GameData.shardsForReward(a.reward()));
        }
    }

    public static void doBuy(ServerPlayer player, int index) {
        // 連打による二重購入を防ぐ (この層のイベントは1回のみ)
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) {
            player.sendSystemMessage(Component.literal("§cこの層のイベントは使用済み"));
            return;
        }
        String raw = player.getData(ModAttachments.SHOP.get());
        if (raw == null || raw.isBlank()) {
            player.sendSystemMessage(Component.literal("§c商店は開いていない (/abyss event shop)"));
            return;
        }
        String[] items = raw.split(",");
        if (index < 1 || index > items.length) {
            player.sendSystemMessage(Component.literal("§c1〜" + items.length + "で指定"));
            return;
        }
        String[] kv = items[index - 1].split(":");
        int price;
        try {
            price = Integer.parseInt(kv[1]);
        } catch (Exception ex) {
            return;
        }
        var st = GameData.WeaponStyle.byId(kv[0]);
        if (st == null) {
            player.sendSystemMessage(Component.literal("§c不明な商品: " + kv[0]));
            return;
        }
        if (!takeShards(player, price)) {
            player.sendSystemMessage(Component.literal("§c欠片" + price + "個が足りない (所持" + countShards(player) + ")"));
            return;
        }
        player.getInventory().add(GearFactory.makePrizeWeapon(st, player.getRandom(), StatCalc.luckOf(player),
                Math.max(1, player.getData(ModAttachments.FLOOR.get()))));
        completeEvent(player);
        player.sendSystemMessage(Component.literal("§6購入: " + st.weaponName + " (奥義「" + st.artName + "」)"));
    }

    /* ---------------- ランダムイベント (原作 RANDOM_EVENTS 7種) ---------------- */

    /** 層の中盤イベントを1件引いて発動する */
    public static void triggerRandomEvent(ServerPlayer player, ServerLevel level, BlockPos center, int floor) {
        var ev = GameData.RANDOM_EVENTS.get(player.getRandom().nextInt(GameData.RANDOM_EVENTS.size()));
        player.sendSystemMessage(Component.literal("§e◆ " + ev.name() + " — " + ev.desc()));
        var scale = Enemies.scaleFor(player, floor);
        switch (ev.kind()) {
            case "loot" -> {
                // 宝の間: 装備3点 + 遺物の3択 (最大2つまで)
                var styles = GameData.WeaponStyle.values();
                for (int i = 0; i < 3; i++) {
                    var drop = GearFactory.makeWeapon(styles[player.getRandom().nextInt(styles.length)],
                            player.getRandom(), StatCalc.luckOf(player), floor, 0);
                    var e = new ItemEntity(
                            level, center.getX() + player.getRandom().nextInt(7) - 3,
                            center.getY() + 1, center.getZ() + player.getRandom().nextInt(7) - 3, drop);
                    level.addFreshEntity(e);
                }
                offerRelics(player);
            }
            case "gamble" -> {
                // 賭博の祭壇: 欠片の半分を賭ける
                int have = countShards(player);
                if (have < 2) {
                    player.sendSystemMessage(Component.literal("§7欠片が足りず祭壇は沈黙した (2個〜で賭けられる)"));
                    break;
                }
                int bet = have / 2;
                if (player.getRandom().nextBoolean()) {
                    player.getInventory().add(new ItemStack(
                            ModItems.SOUL_SHARD.get(), bet));
                    player.sendSystemMessage(Component.literal("§6当たり！ 欠片+" + bet));
                } else {
                    takeShards(player, bet);
                    player.sendSystemMessage(Component.literal("§c外れ… 欠片-" + bet));
                }
            }
            case "elite" -> {
                // 精鋭の襲撃: 精鋭3体 (通常敵のみ)
                for (int i = 0; i < 3; i++) {
                    var spec = Enemies.pickSpec(floor, -1, player.getRandom());
                    if (spec.role() != Enemies.Role.NORMAL) spec = Enemies.byId("golem");
                    Mob mob = Enemies.create(level, spec);
                    BlockPos pos = center.offset(player.getRandom().nextInt(17) - 8, 1,
                            player.getRandom().nextInt(17) - 8);
                    mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    Enemies.apply(mob, spec, scale, 1.0);
                    Enemies.makeElite(mob);
                    Enemies.tagByRole(mob, spec);
                    mob.setTarget(player);
                    level.addFreshEntity(mob);
                }
                player.setData(ModAttachments.WAVE_TOTAL.get(),
                        player.getData(ModAttachments.WAVE_TOTAL.get()) + 3);
            }
            case "heal" -> {
                // 加護の泉: 全回復 + 素材
                RunManager.heal(player, player.getMaxHealth());
                int shards = RunManager.materialCount(player, 3.0);
                if (shards > 0) {
                    player.getInventory().add(new ItemStack(
                            ModItems.SOUL_SHARD.get(), shards));
                }
                player.sendSystemMessage(Component.literal("§a全回復！ 欠片+" + shards));
            }
            case "fate" -> {
                if ("mist".equals(ev.fate())) {
                    RunManager.setFate(player, "mist", 400);
                    player.sendSystemMessage(Component.literal("§5呪いの霧 (20秒): 被ダメ+30%、戦利品2倍"));
                } else {
                    RunManager.setFate(player, "timewarp", 300);
                    var mobs = level.getEntitiesOfClass(Mob.class,
                            player.getBoundingBox().inflate(48),
                            e -> e.isAlive());
                    for (var m : mobs) {
                        m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 2, false, false));
                    }
                    player.sendSystemMessage(Component.literal("§b時の歪み (15秒): 敵半速、魂ゲージ2倍"));
                }
            }
            default -> {
                // 群れの呼び声: 追加ウェーブ
                RunManager.spawnWave(player, floor, false);
                player.sendSystemMessage(Component.literal("§e群れが押し寄せる！ 追加ウェーブ"));
            }
        }
    }

    /* ---------------- 遺物・技能の提示 ---------------- */

    /** 選択肢ラベルの内側 (「名前 説明」)。遺物・技能をどちらにも対応 */
    private static String selectionLabel(String id) {
        var r = GameData.relicById(id);
        if (r != null) return r.name() + " " + r.desc();
        var p = GameData.passiveById(id);
        return p == null ? id : p.name() + " " + p.desc();
    }

    /** 遺物・技能共通の3択提示 (ボタンを並べ、候補IDを添付に保存。選択画面GUIも開く) */
    private static void sendOffer(ServerPlayer player, String header,
                                  java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<String>> offerKey,
                                  List<String> ids, String cmdPrefix) {
        var msg = Component.literal(header);
        for (String id : ids) {
            msg.append(btn("§6[" + selectionLabel(id) + "] ", cmdPrefix + id));
        }
        player.setData(offerKey.get(), String.join(",", ids));
        player.sendSystemMessage(msg);
        // 選択画面 (GUI) を自動で開く (祝福と同じパターン)
        boolean relic = offerKey.get() == ModAttachments.RELIC_OFFER.get();
        String mode = relic ? "relic" : "passive";
        var names = new ArrayList<String>();
        var descs = new ArrayList<String>();
        for (String id : ids) {
            var r = GameData.relicById(id);
            if (r != null) {
                names.add(r.name());
                descs.add(r.desc());
                continue;
            }
            var p = GameData.passiveById(id);
            names.add(p == null ? id : p.name());
            descs.add(p == null ? "" : p.desc());
        }
        openEventScreen(player, mode,
                relic ? "◆ 遺物を選べ (最大2つまで) ◆" : "◆ 技能を選べ (代償付き) ◆",
                ids, names, descs,
                "ESCで後で選ぶ (選ばないと消えない)", false);
    }

    /** 遺物の3択を提示 (宝の間・層主撃破時) */
    public static void offerRelics(ServerPlayer player) {
        var owned = RunManager.relicsOf(player);
        if (owned.size() >= 2) return;
        var roll = GameData.rollRelics(new Random(player.getRandom().nextLong()), owned, 3);
        if (roll.isEmpty()) return;
        sendOffer(player, "§d遺物を選べ (最大2つまで): ", ModAttachments.RELIC_OFFER,
                roll.stream().map(GameData.Relic::id).toList(), "/abyss relic ");
    }

    public static void doRelic(ServerPlayer player, String id) {
        String offer = player.getData(ModAttachments.RELIC_OFFER.get());
        if (offer == null || !List.of(offer.split(",")).contains(id)) {
            player.sendSystemMessage(Component.literal("§c今はその遺物を選べない"));
            return;
        }
        if (RunManager.addRelic(player, id)) {
            player.setData(ModAttachments.RELIC_OFFER.get(), "");
            var r = GameData.relicById(id);
            player.sendSystemMessage(Component.literal("§d遺物を得た: " + r.name() + " (" + r.desc() + ")"));
        } else {
            player.sendSystemMessage(Component.literal("§c遺物は2つまで (取得済み・上限)"));
        }
    }

    /** 技能の3択を提示 (層主撃破時) */
    public static void offerPassives(ServerPlayer player) {
        var roll = GameData.rollPassives(new Random(player.getRandom().nextLong()), RunManager.passivesOf(player), 3);
        if (roll.isEmpty()) return;
        sendOffer(player, "§b技能を選べ (代償付き): ", ModAttachments.PASSIVE_OFFER,
                roll.stream().map(GameData.Passive::id).toList(), "/abyss passive ");
    }

    public static void doPassive(ServerPlayer player, String id) {
        String offer = player.getData(ModAttachments.PASSIVE_OFFER.get());
        if (offer == null || !List.of(offer.split(",")).contains(id)) {
            player.sendSystemMessage(Component.literal("§c今はその技能を選べない"));
            return;
        }
        if (RunManager.addPassive(player, id)) {
            player.setData(ModAttachments.PASSIVE_OFFER.get(), "");
            var p = GameData.passiveById(id);
            player.sendSystemMessage(Component.literal("§b技能を得た: " + p.name() + " (" + p.desc() + ")"));
        }
    }

    /** 未選択の遺物を再提示 (選択画面GUI + チャットボタン)。ESCで後回しにした分を開ける導線 */
    public static void resendRelicOffer(ServerPlayer player) {
        String raw = player.getData(ModAttachments.RELIC_OFFER.get());
        if (raw == null || raw.isBlank()) {
            player.sendSystemMessage(Component.literal("§7選択待ちの遺物は無い"));
            return;
        }
        var ids = new ArrayList<String>();
        var names = new ArrayList<String>();
        var descs = new ArrayList<String>();
        for (String id : raw.split(",")) {
            var r = GameData.relicById(id);
            if (r == null) continue;
            ids.add(id);
            names.add(r.name());
            descs.add(r.desc());
        }
        if (ids.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7選択待ちの遺物は無い"));
            return;
        }
        player.sendSystemMessage(Component.literal("§d遺物を選べ (再提示): ")
                .append(btn("§6[選択画面を開く]", "/abyss relic")));
        openEventScreen(player, "relic", "◆ 遺物を選べ (最大2つまで) ◆",
                ids, names, descs, "ESCで後で選ぶ (選ばないと消えない)", false);
    }

    /** 未選択の技能を再提示 (選択画面GUI + チャットボタン)。上書きしない。選ばず進んでも失われない */
    public static void resendPassiveOffer(ServerPlayer player) {
        String raw = player.getData(ModAttachments.PASSIVE_OFFER.get());
        if (raw == null || raw.isBlank()) {
            player.sendSystemMessage(Component.literal("§7選択待ちの技能は無い"));
            return;
        }
        var ids = new ArrayList<String>();
        var names = new ArrayList<String>();
        var descs = new ArrayList<String>();
        for (String id : raw.split(",")) {
            var p = GameData.passiveById(id);
            if (p == null) continue;
            ids.add(id);
            names.add(p.name());
            descs.add(p.desc());
        }
        if (ids.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7選択待ちの技能は無い"));
            return;
        }
        player.sendSystemMessage(Component.literal("§b技能を選べ (再提示): ")
                .append(btn("§6[選択画面を開く]", "/abyss passive")));
        openEventScreen(player, "passive", "◆ 技能を選べ (代償付き) ◆",
                ids, names, descs, "ESCで後で選ぶ (選ばないと消えない)", false);
    }
}
