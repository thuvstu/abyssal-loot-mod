package com.abyssalloot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * /abyss 系コマンド.
 *  quick / menu / help / class / weapon / sigil / level / start / next / daily / true / pinnacle /
 *  hub / retire / event_screen / respec /
 *  zone / relic / passive / compass / ach / art / skill / boon / attr / mode / diff / keep /
 *  event / buy / merge / forge / shop / compare / statusgui / dash / status
 */
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        d.register(Commands.literal("abyss")
                // 一括開始: 職業と武器を配備して即出撃 (/abyss quick [職業] [武器])
                .then(Commands.literal("quick")
                        .executes(ctx -> {
                            quickStart(ctx.getSource().getPlayerOrException(), "samurai", "sword");
                            return 1;
                        })
                        .then(Commands.argument("class", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (GameData.HeroClass c : GameData.HeroClass.values()) b.suggest(c.id);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    quickStart(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "class"), "sword");
                                    return 1;
                                })
                                .then(Commands.argument("weapon", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (GameData.WeaponStyle w : GameData.WeaponStyle.values()) b.suggest(w.id);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            quickStart(ctx.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(ctx, "class"),
                                                    StringArgumentType.getString(ctx, "weapon"));
                                            return 1;
                                        }))))
                // 出撃前の設定メニュー (職業・呪印・モード・難易度をクリックで選ぶ)
                .then(Commands.literal("menu").executes(ctx -> {
                    showMenu(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                // 操作ハブ (GUIボタン集約。Gキーからも開ける)
                .then(Commands.literal("hub").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                            new ModPackets.HubSync());
                    return 1;
                }))
                // 操作説明 (クリックで実行できるボタン付き)
                .then(Commands.literal("help").executes(ctx -> {
                    showHelp(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("class")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (GameData.HeroClass c : GameData.HeroClass.values()) {
                                        b.suggest(c.id);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    GameData.HeroClass cls = GameData.HeroClass.byId(
                                            StringArgumentType.getString(ctx, "id"));
                                    int level = p.getData(ModAttachments.LEVEL.get());
                                    StatCalc.applyClass(p, cls, level);
                                    return 1;
                                })))
                .then(Commands.literal("weapon")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (GameData.WeaponStyle w : GameData.WeaponStyle.values()) {
                                        b.suggest(w.id);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    GameData.WeaponStyle style = GameData.WeaponStyle.byId(
                                            StringArgumentType.getString(ctx, "id"));
                                    var stack = GearFactory.makeWeapon(style, p.getRandom());
                                    p.getInventory().add(stack);
                                    p.sendSystemMessage(Component.literal(
                                            "§b武器: " + style.weaponName + " / 奥義: " + style.artName + " — " + style.artDesc
                                                    + " / 小技: " + GameData.WEAPON_LIGHTS.get(style.id).name()));
                                    return 1;
                                })))
                .then(Commands.literal("sigil")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (GameData.Sigil s : GameData.Sigil.values()) {
                                        b.suggest(s.id);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    if (RunManager.isRunLocked(p)) {
                                        p.sendSystemMessage(Component.literal("§c出撃中は条件を変更できません (原作 /api/expeditions の条件固定と同様)"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (GameData.Sigil.byId(id) == null) {
                                        p.sendSystemMessage(Component.literal("§c不明な呪印: " + id));
                                        return 0;
                                    }
                                    Set<String> sigils = new HashSet<>(RunManager.sigilsOf(p));
                                    if (sigils.contains(id)) sigils.remove(id);
                                    else sigils.add(id);
                                    RunManager.setSigils(p, sigils);
                                    p.sendSystemMessage(Component.literal(
                                            "§5呪印: " + (sigils.isEmpty() ? "(なし)" : String.join(",", sigils))
                                                    + " 魂倍率×" + String.format("%.2f", GameData.soulMult(sigils))));
                                    return 1;
                                })))
                .then(Commands.literal("level")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                    GameData.HeroClass cls = GameData.HeroClass.byId(
                                            p.getData(ModAttachments.HERO_CLASS.get()));
                                    StatCalc.applyClass(p, cls, n);
                                    return 1;
                                })))
                .then(Commands.literal("start").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (RunManager.isRunLocked(p)) {
                        p.sendSystemMessage(Component.literal(
                                "§c出撃中だ。先に §7/abyss retire §cで中断するか §7/abyss quick §cで再配備してね"));
                        return 0;
                    }
                    RunManager.startRun(p);
                    return 1;
                }))
                .then(Commands.literal("next").executes(ctx -> {
                    RunManager.tryNextFloor(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("retire").executes(ctx -> {
                    RunManager.retire(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("daily").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (RunManager.isRunLocked(p)) {
                        p.sendSystemMessage(Component.literal(
                                "§c出撃中はモードを変更できません (retire か quick で再出撃してね)"));
                        return 0;
                    }
                    p.setData(ModAttachments.MODE.get(), "daily");
                    var dm = RunManager.dailyMod();
                    p.sendSystemMessage(Component.literal(
                            "§6日替わり深淵【" + dm.title() + "】 (敵HP×"
                                    + dm.enemyHp() + " 攻撃×" + dm.enemyDmg()
                                    + " 魂×" + dm.soul() + ") /abyss start で出撃"));
                    return 1;
                }))
                .then(Commands.literal("true").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BossHandler.startTrue(p);
                    return 1;
                }))
                .then(Commands.literal("pinnacle").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BossHandler.startPinnacle(p);
                    return 1;
                }))
                .then(Commands.literal("zone").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    var z = GameData.zoneForFloor(p.getData(ModAttachments.FLOOR.get()));
                    p.sendSystemMessage(Component.literal(
                            "§7区域: " + z.name() + " — " + z.desc()));
                    return 1;
                }))
                .then(Commands.literal("relic")
                        .executes(ctx -> {
                            FloorEvents.resendRelicOffer(ctx.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    String offer = p != null ? p.getData(ModAttachments.RELIC_OFFER.get()) : "";
                                    if (offer != null && !offer.isBlank()) {
                                        for (var id : offer.split(",")) b.suggest(id);
                                    }
                                    for (var r : GameData.RELICS) b.suggest(r.id());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    FloorEvents.doRelic(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("passive")
                        .executes(ctx -> {
                            FloorEvents.resendPassiveOffer(ctx.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (var p : GameData.PASSIVES) b.suggest(p.id());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    FloorEvents.doPassive(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("compass").executes(ctx -> {
                    RunManager.compass(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("ach").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String raw = p.getData(ModAttachments.ACH.get());
                    int n = (raw == null || raw.isBlank()) ? 0 : raw.split(",").length;
                    p.sendSystemMessage(Component.literal(
                            "§e実績: " + ((raw == null || raw.isBlank()) ? "(なし)" : raw)
                                    + " (" + n + "/" + GameData.ACHIEVEMENTS.size() + ")"));
                    return 1;
                }))
                .then(Commands.literal("art").executes(ctx -> {
                    SkillHandler.tryArt(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("skill").executes(ctx -> {
                    SkillHandler.trySkill(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("boon")
                        .executes(ctx -> {
                            BoonHandler.resendBoonOffer(ctx.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    String offer = p != null ? p.getData(ModAttachments.OFFER.get()) : "";
                                    if (offer != null && !offer.isBlank()) {
                                        for (var id : offer.split(",")) b.suggest(id);
                                    }
                                    for (var boon : Boons.BOON_POOL) b.suggest(boon.id());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    BoonHandler.chooseBoon(p, StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("event_screen")
                        .executes(ctx -> {
                            // 層間イベント選択画面の再提示 (ESCで後回しにした分をハブから開ける)
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (p.getData(ModAttachments.EVENT_DONE.get()) != 0) {
                                p.sendSystemMessage(Component.literal("§cこの層のイベントは使用済み"));
                                return 0;
                            }
                            FloorEvents.offerEvent(p);
                            return 1;
                        }))
                .then(Commands.literal("attr")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (String name : GameData.ATTR_NAMES) b.suggest(name);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    return StatCalc.addAttrPoint(p,
                                            attrIndex(StringArgumentType.getString(ctx, "id"))) ? 1 : 0;
                                })))
                .then(Commands.literal("respec").executes(ctx -> {
                    StatCalc.respec(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("mode")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (var m : GameData.RUN_MODES) b.suggest(m.id());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    if (RunManager.isRunLocked(p)) {
                                        p.sendSystemMessage(Component.literal("§c出撃中はモードを変更できません"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (GameData.modeSoulMult(id) == 1.0
                                            && !id.equals("standard") && !id.equals("daily")) {
                                        p.sendSystemMessage(Component.literal("§cstandard/bossrush/endless/covenant/daily/pinnacle/danmaku/omega で指定"));
                                        return 0;
                                    }
                                    p.setData(ModAttachments.MODE.get(), id);
                                    String desc = switch (id) {
                                            case "bossrush" -> " Lv10開始・毎層ボス・8層主で踏破";
                                            case "covenant" -> " 祝福なし";
                                            case "endless" -> " 25層以降も無限";
                                            case "pinnacle" -> " /abyss pinnacle で終焉の座へ";
                                            case "danmaku" -> " 敵の群れが濃くなるかわりに被弾は軽い";
                                            case "omega" -> " 全8呪印を強制付与";
                                            case "daily" -> " 日替わり種別の層構成";
                                            default -> " 全25層・虚無神を討て"; // standard
                                    };
                                    p.sendSystemMessage(Component.literal("§5進行モード: " + id
                                            + " (魂×" + GameData.modeSoulMult(id) + ")" + desc));
                                    return 1;
                                })))
                .then(Commands.literal("diff")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (var dif : GameData.DIFFICULTIES) b.suggest(dif.id());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    if (RunManager.isRunLocked(p)) {
                                        p.sendSystemMessage(Component.literal("§c出撃中は難易度を変更できません"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    var diff = GameData.difficultyById(id);
                                    if (!diff.id().equals(id)) {
                                        p.sendSystemMessage(Component.literal("§cnormal/hard/hell/inferno/omega で指定"));
                                        return 0;
                                    }
                                    p.setData(ModAttachments.DIFF.get(), id);
                                    p.sendSystemMessage(Component.literal("§c難易度: " + diff.name()
                                            + " (敵HP×" + diff.hpMul() + " 攻撃×" + diff.dmgMul()
                                            + " 魂×" + diff.soulMul() + ")"));
                                    return 1;
                                })))
                .then(Commands.literal("keep").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack held = p.getMainHandItem();
                    if (ModItems.styleOf(held.getItem()) == null) {
                        p.sendSystemMessage(Component.literal("§cAbyss武器を手に持ってね (1点を次回出撃へ継承)"));
                        return 0;
                    }
                    p.setData(ModAttachments.HEIRLOOM.get(), held.copy());
                    p.sendSystemMessage(Component.literal(
                            "§6血脈に刻んだ: " + held.getHoverName().getString() + " (次回 /abyss start で受け取る)"));
                    return 1;
                }))
                .then(Commands.literal("event")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    b.suggest("rest"); b.suggest("oath");
                                    b.suggest("shop"); b.suggest("forge");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    FloorEvents.doEvent(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("buy")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> {
                                    FloorEvents.doBuy(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "n"));
                                    return 1;
                                })))
                .then(Commands.literal("merge").executes(ctx -> {
                    FloorEvents.doMerge(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("forge").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, pl) -> new ForgeMenu(id, inv),
                            Component.literal("鍛造")));
                    return 1;
                }))
                .then(Commands.literal("shop").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String raw = p.getData(ModAttachments.SHOP.get());
                    if (raw == null || raw.isBlank()) {
                        p.sendSystemMessage(Component.literal("§c商店は開いていない (/abyss event shop)"));
                        return 0;
                    }
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, pl) -> new ShopMenu(id, inv),
                            Component.literal("商店")));
                    return 1;
                }))
                .then(Commands.literal("compare").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, pl) -> new CompareMenu(id, inv),
                            Component.literal("装備比較")));
                    return 1;
                }))
                .then(Commands.literal("statusgui").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                            new ModPackets.StatusSync(RunManager.statusLines(p)));
                    return 1;
                }))
                .then(Commands.literal("dash").executes(ctx -> {
                    SkillHandler.dash(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    CombatHandler.showStatus(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    /** 属性名→index (日本語・英語・旧ローマ字を受け付ける) */
    static int attrIndex(String id) {
        if (id == null) return -1;
        String s = id.toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "剛力", "ごうりき", "ゴウリキ", "power", "str", "got" -> 0;
            case "韻律", "いんりつ", "インリツ", "rhythm", "rin" -> 1;
            case "敏捷", "びんしょう", "ビンショウ", "agility", "speed", "bin" -> 2;
            case "忍耐", "にんたい", "ニンタイ", "endurance", "vit", "nin" -> 3;
            case "幸運", "こううん", "コウウン", "luck", "un" -> 4;
            case "集中", "しゅうちゅう", "シュウチュウ", "focus", "con" -> 5;
            case "活力", "かつりょく", "カツリョク", "vital", "katsu" -> 6;
            case "技巧", "ぎこう", "ギコウ", "tech", "giko" -> 7;
            case "採掘", "さいくつ", "サイクツ", "mining", "mine", "saikutu" -> 8;
            case "跳躍", "ちょうやく", "チョウヤク", "jump", "leap", "tyouyaku" -> 9;
            default -> -1;
        };
    }

    /** 参加時の案内 (はじめる / 操作説明 ボタン) */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean first = p.getData(ModAttachments.TUTOR.get()) == 0;
        p.sendSystemMessage(Component.literal(first
                        ? "§6◆ Abyssal Loot へようこそ！まずはワンタップで潜ろう ◆ "
                        : "§6◆ Abyssal Loot ◆ "));
        p.sendSystemMessage(Component.literal("§7")
                .append(button("§a[おすすめ: すぐ潜る]", "/abyss quick"))
                .append(button("§e[職業・条件を選ぶ]", "/abyss menu"))
                .append(button("§d[操作ハブ]", "/abyss hub"))
                .append(button("§b[操作説明]", "/abyss help")));
        // HUDの初期同期 (参加直後の魂ゲージ・層を即時表示する)
        CombatHandler.sendHud(p);
    }

    /** 職業と武器を配備して即出撃 (進行中の冒険は破棄される) */
    static void quickStart(ServerPlayer p, String classId, String weaponId) {
        GameData.HeroClass cls = GameData.HeroClass.byId(classId);
        GameData.WeaponStyle style = GameData.WeaponStyle.byId(weaponId);
        p.setData(ModAttachments.HERO_CLASS.get(), cls.id);
        if (p.getData(ModAttachments.LEVEL.get()) < 1) p.setData(ModAttachments.LEVEL.get(), 1);
        p.getInventory().add(GearFactory.makeWeapon(style, p.getRandom()));
        p.sendSystemMessage(Component.literal("§6配備完了: " + cls.name + " / " + style.weaponName
                + "（奥義「" + style.artName + "」）→ 出撃します"));
        RunManager.startRun(p);
    }

    static void showHelp(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("§6━━━ Abyssal Loot 操作説明 ━━━"));
        p.sendSystemMessage(Component.literal("§f移動 WASD / 攻撃 左クリック / 照準 マウス"));
        p.sendSystemMessage(Component.literal("§b右クリック 武器小技 (CT1~3秒) / §dR 武器奥義 (魂100) / §dV 必殺技 (CT25秒) / §aX 回避ダッシュ / §fN 次の層へ"));
        p.sendSystemMessage(Component.literal("§eG 操作ハブ（出撃・次層・各GUIへのボタン集）"));
        p.sendSystemMessage(Component.literal("§7魂ゲージは与ダメ・被ダメで溜まる。満タンでRの奥義。"));
        p.sendSystemMessage(Component.literal("§7倒すと武器・防具や魂の欠片が出る。全滅で転移門が開く → /abyss next"));
        p.sendSystemMessage(Component.literal("§7防具(兜・鎧・脚・靴)は右クリックで装備。接辞は武器と合算される"));
        p.sendSystemMessage(Component.literal("§7武器は持ち替えで奥義が変わる。性能はカーソルを合わせると表示"));
        p.sendSystemMessage(Component.literal("§7ロッド・ワンドは遠隔魔弾が自動発射 (銃魔は多重化)。防具で着用モデル表示"));
        p.sendSystemMessage(Component.literal("§7全武器に固有能力、全防具に部位能力つき (lore参照)。ツールも採集能力あり"));
        p.sendSystemMessage(Component.literal("§7§b射程§7/§b横斬り§7/§b連撃§7は接辞・祝福・防具スキル・武器種の素値で伸びる (攻撃距離と薙ぎ幅)"));
        p.sendSystemMessage(Component.literal("§7満腹は §b軍糧丸§7 や撃破時のもぐに (12%) で回復。試練も腹が減るのが本番"));
        p.sendSystemMessage(Component.literal("§7モブにも固有能力 (反射・爆発・転移・分裂・吸収・鈍足)。層に地相 (65%で1つ) あり"));
        p.sendSystemMessage(Component.literal("§7属性配分 /abyss attr 剛力|韻律|敏捷|忍耐|幸運|集中|活力|技巧|採掘|跳躍（レベルアップで1点）"));
        p.sendSystemMessage(Component.literal("§7適性★の属性は効果1.5倍。各属性10・20・30点で節目ボーナス。振り直し /abyss respec (欠片8)"));
        p.sendSystemMessage(Component.literal("§7呪印（難易度）/abyss sigil <id>　モード /abyss mode <id>"));
        p.sendSystemMessage(Component.literal("§7武器合成は鍛造画面で（同武器2点 or 同防具2点）")
                .append(button("§b[鍛造画面を開く]", "/abyss forge")));
        p.sendSystemMessage(Component.literal("§7出撃の中断は ")
                .append(button("§c[リタイア]", "/abyss retire")));
        p.sendSystemMessage(Component.literal("§7")
                .append(button("§6[商店]", "/abyss shop"))
                .append(button("§b[装備比較]", "/abyss compare"))
                .append(button("§e[ステータス]", "/abyss statusgui")));
        p.sendSystemMessage(Component.literal("§7")
                .append(button("§a[一括開始]", "/abyss quick"))
                .append(button("§e[状態]", "/abyss status"))
                .append(button("§d[設定メニュー]", "/abyss menu"))
                .append(button("§b[次の層へ]", "/abyss next")));
        p.sendSystemMessage(Component.literal("§7")
                .append(button("§e[操作ハブを開く]", "/abyss hub")));
    }

    static net.minecraft.network.chat.MutableComponent button(String label, String cmd) {
        return Component.literal(label + " ").withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
                .withUnderlined(true));
    }

    /** 出撃前設定メニュー: 現在値を表示し、クリックで切り替え/出撃する */
    static void showMenu(ServerPlayer p) {
        String cls = p.getData(ModAttachments.HERO_CLASS.get());
        String mode = p.getData(ModAttachments.MODE.get());
        String diff = p.getData(ModAttachments.DIFF.get());
        Set<String> sigils = RunManager.sigilsOf(p);
        p.sendSystemMessage(Component.literal("§6━━━ 出撃前設定 ━━━"));
        var row = Component.literal("§b職業: ");
        for (GameData.HeroClass c : GameData.HeroClass.values()) {
            String mark = c.id.equals(cls) ? "§a" : "§7";
            row.append(button(mark + "[" + c.name + "]", "/abyss class " + c.id));
        }
        p.sendSystemMessage(row);
        var sig = Component.literal("§d呪印 (" + sigils.size() + "/8, 魂×"
                + String.format("%.2f", GameData.soulMult(sigils)) + "): ");
        for (GameData.Sigil g : GameData.Sigil.values()) {
            String mark = sigils.contains(g.id) ? "§a✔" : "§7";
            sig.append(button(mark + g.name, "/abyss sigil " + g.id));
        }
        p.sendSystemMessage(sig);
        var md = Component.literal("§5モード: ");
        for (var m : GameData.RUN_MODES) {
            String mark = m.id().equals(mode) ? "§a" : "§7";
            md.append(button(mark + "[" + m.id() + "]", "/abyss mode " + m.id()));
        }
        p.sendSystemMessage(md);
        var df = Component.literal("§c難易度: ");
        for (var d : GameData.DIFFICULTIES) {
            String mark = d.id().equals(diff) ? "§a" : "§7";
            df.append(button(mark + "[" + d.id() + "]", "/abyss diff " + d.id()));
        }
        p.sendSystemMessage(df);
        p.sendSystemMessage(Component.literal("§7GUI: ")
                .append(button("§b[鍛造]", "/abyss forge"))
                .append(button("§6[商店]", "/abyss shop"))
                .append(button("§b[装備比較]", "/abyss compare"))
                .append(button("§e[ステータス]", "/abyss statusgui")));
        p.sendSystemMessage(Component.literal("§7")
                .append(button("§a[出撃！]", "/abyss start"))
                .append(button("§e[メニュー更新]", "/abyss menu"))
                .append(button("§b[操作説明]", "/abyss help")));
    }
}
