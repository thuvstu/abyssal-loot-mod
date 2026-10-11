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
 *  class / weapon / sigil / level / mode / diff / start / next / art / skill / dash / status / boon / attr / keep
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
                                    RunManager.applyClass(p, cls, level);
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
                                    var stack = RunManager.makeWeapon(style, p.getRandom());
                                    p.getInventory().add(stack);
                                    p.sendSystemMessage(Component.literal(
                                            "§b武器: " + style.weaponName + " / 奥義: " + style.artName + " — " + style.artDesc));
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
                                    RunManager.applyClass(p, cls, n);
                                    return 1;
                                })))
                .then(Commands.literal("start").executes(ctx -> {
                    RunManager.startRun(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("next").executes(ctx -> {
                    RunManager.nextFloor(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("daily").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.setData(ModAttachments.MODE.get(), "daily");
                    p.sendSystemMessage(Component.literal(
                            "§6日替わり深淵【" + RunManager.dailyPreset() + "】 (敵HP×"
                                    + RunManager.dailyHpMult() + " 攻撃×" + RunManager.dailyDmgMult()
                                    + " 魂×1.5) /abyss start で出撃"));
                    return 1;
                }))
                .then(Commands.literal("true").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    RunManager.startTrue(p);
                    return 1;
                }))
                .then(Commands.literal("ach").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String raw = p.getData(ModAttachments.ACH.get());
                    p.sendSystemMessage(Component.literal(
                            "§e実績: " + ((raw == null || raw.isBlank()) ? "(なし)" : raw)));
                    return 1;
                }))
                .then(Commands.literal("art").executes(ctx -> {
                    CombatHandler.tryArt(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("skill").executes(ctx -> {
                    CombatHandler.trySkill(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("boon")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (var boon : CombatHandler.BOON_POOL) b.suggest(boon.id());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    var def = CombatHandler.boonById(id);
                                    if (def == null) {
                                        p.sendSystemMessage(Component.literal("§c不明な祝福: " + id));
                                        return 0;
                                    }
                                    String offer = p.getData(ModAttachments.OFFER.get());
                                    if (offer == null || !java.util.List.of(offer.split(",")).contains(id)) {
                                        p.sendSystemMessage(Component.literal("§c今はその祝福を選べない (レベルアップ時の3択から選んでね)"));
                                        return 0;
                                    }
                                    RunManager.addBoon(p, id);
                                    p.setData(ModAttachments.OFFER.get(), "");
                                    GameData.HeroClass cls = GameData.HeroClass.byId(
                                            p.getData(ModAttachments.HERO_CLASS.get()));
                                    RunManager.applyClass(p, cls, p.getData(ModAttachments.LEVEL.get()));
                                    p.sendSystemMessage(Component.literal(
                                            "§e祝福を得た: " + def.name() + " (" + def.desc() + ")"));
                                    return 1;
                                })))
                .then(Commands.literal("attr")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    b.suggest("got"); b.suggest("rin"); b.suggest("bin");
                                    b.suggest("nin"); b.suggest("un");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    int idx = switch (id) {
                                        case "got" -> 0; case "rin" -> 1; case "bin" -> 2;
                                        case "nin" -> 3; case "un" -> 4;
                                        default -> -1;
                                    };
                                    if (idx < 0) {
                                        p.sendSystemMessage(Component.literal("§cgot/rin/bin/nin/un で指定 (剛力/韻律/敏捷/忍耐/幸運)"));
                                        return 0;
                                    }
                                    int level = p.getData(ModAttachments.LEVEL.get());
                                    int spent = RunManager.attrSpent(p);
                                    if (spent >= level - 1) {
                                        p.sendSystemMessage(Component.literal(
                                                "§c属性ポイント不足 (Lv" + level + "で" + (level - 1) + "点、配分済" + spent + "点)"));
                                        return 0;
                                    }
                                    int[] a = RunManager.attrsOf(p);
                                    a[idx]++;
                                    RunManager.setAttrs(p, a);
                                    GameData.HeroClass cls = GameData.HeroClass.byId(
                                            p.getData(ModAttachments.HERO_CLASS.get()));
                                    RunManager.applyClass(p, cls, level);
                                    p.sendSystemMessage(Component.literal(
                                            "§a属性配分: 剛" + a[0] + " 韻" + a[1] + " 敏" + a[2] + " 忍" + a[3] + " 幸" + a[4]
                                                    + " (残" + (level - 1 - spent - 1) + "点)"));
                                    return 1;
                                })))
                .then(Commands.literal("mode")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    b.suggest("standard"); b.suggest("bossrush");
                                    b.suggest("endless"); b.suggest("covenant");
                                    b.suggest("daily");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (!id.equals("standard") && !id.equals("bossrush")
                                            && !id.equals("endless") && !id.equals("covenant")
                                            && !id.equals("daily")) {
                                        p.sendSystemMessage(Component.literal("§cstandard/bossrush/endless/covenant/daily で指定"));
                                        return 0;
                                    }
                                    p.setData(ModAttachments.MODE.get(), id);
                                    p.sendSystemMessage(Component.literal("§5進行モード: " + id
                                            + (id.equals("bossrush") ? " (Lv10開始・毎層ボス・8層主で踏破)"
                                            : id.equals("covenant") ? " (祝福なし・魂×3.0)"
                                            : id.equals("endless") ? " (25層以降も無限)"
                                            : " (全25層・虚無神を討て)")));
                                    return 1;
                                })))
                .then(Commands.literal("diff")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    b.suggest("normal"); b.suggest("hard"); b.suggest("inferno");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (!id.equals("normal") && !id.equals("hard") && !id.equals("inferno")) {
                                        p.sendSystemMessage(Component.literal("§cnormal/hard/inferno で指定"));
                                        return 0;
                                    }
                                    p.setData(ModAttachments.DIFF.get(), id);
                                    p.sendSystemMessage(Component.literal("§c難易度: " + id
                                            + (id.equals("hard") ? " (敵HP×1.4 攻撃×1.25 魂×1.5)"
                                            : id.equals("inferno") ? " (敵HP×2.0 攻撃×1.6 魂×2.2)"
                                            : " (等倍)")));
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
                                    RunManager.doEvent(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("buy")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> {
                                    RunManager.doBuy(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "n"));
                                    return 1;
                                })))
                .then(Commands.literal("dash").executes(ctx -> {
                    CombatHandler.dash(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    CombatHandler.showStatus(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    /** 参加時の案内 (はじめる / 操作説明 ボタン) */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        p.sendSystemMessage(Component.literal("§6◆ Abyssal Loot へようこそ ◆ ")
                .append(button("§a[はじめる]", "/abyss quick"))
                .append(button("§b[操作説明]", "/abyss help")));
    }

    /** 職業と武器を配備して即出撃 (進行中の冒険は破棄される) */
    static void quickStart(ServerPlayer p, String classId, String weaponId) {
        GameData.HeroClass cls = GameData.HeroClass.byId(classId);
        GameData.WeaponStyle style = GameData.WeaponStyle.byId(weaponId);
        p.setData(ModAttachments.HERO_CLASS.get(), cls.id);
        if (p.getData(ModAttachments.LEVEL.get()) < 1) p.setData(ModAttachments.LEVEL.get(), 1);
        p.getInventory().add(RunManager.makeWeapon(style, p.getRandom()));
        p.sendSystemMessage(Component.literal("§6配備完了: " + cls.name + " / " + style.weaponName
                + "（奥義「" + style.artName + "」）→ 出撃します"));
        RunManager.startRun(p);
    }

    static void showHelp(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("§6━━━ Abyssal Loot 操作説明 ━━━"));
        p.sendSystemMessage(Component.literal("§f移動 WASD / 攻撃 左クリック / 照準 マウス"));
        p.sendSystemMessage(Component.literal("§bE 武器奥義（右クリックでも可） / §dQ 職業の必殺技 / §aC 回避ダッシュ"));
        p.sendSystemMessage(Component.literal("§7魂ゲージは与ダメ・被ダメで溜まる。満タンでEの奥義。"));
        p.sendSystemMessage(Component.literal("§7倒すと武器や魂の欠片が出る。全滅で転移門が開く → /abyss next"));
        p.sendSystemMessage(Component.literal("§7属性配分 /abyss attr got|rin|bin|nin|un（レベルアップで1点）"));
        p.sendSystemMessage(Component.literal("§7呪印（難易度）/abyss sigil <id>　モード /abyss mode <id>"));
        p.sendSystemMessage(Component.literal("§7")
                .append(button("§a[一括開始]", "/abyss quick"))
                .append(button("§e[状態]", "/abyss status"))
                .append(button("§b[次の層へ]", "/abyss next")));
    }

    static net.minecraft.network.chat.MutableComponent button(String label, String cmd) {
        return Component.literal(label + " ").withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
                .withUnderlined(true));
    }
}
