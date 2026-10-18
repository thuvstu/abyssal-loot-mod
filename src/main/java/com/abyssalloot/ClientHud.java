package com.abyssalloot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * クライアント専用: 魂ゲージ・層・レベルのHUD.
 * 数値はサーバーからの HudSync で更新される (未受信時は表示しない).
 * 区域名は GameData (MC非依存) から層番号で引くため追加パケットは不要.
 * 小技/必殺のCTは残存時のみ表示 (CT中はサーバーが0.1秒ごとに再送する).
 *
 * レイアウト方針: 全情報を画面左上 (x=10, y=10 から縦に流す) に集約する。
 * バニラHUDの既存ゾーン — 上中央=ボスバー / 右上=効果アイコン / 下中央=ホットバー・体力・
 * 経験値 / 左下=チャット — との被りを避けるため、左上 (通常は空いている領域) のみを使用する.
 */
public final class ClientHud {
    private ClientHud() {}

    private static volatile float soul = -1;
    private static volatile int floor;
    private static volatile int level;
    private static volatile int kills;
    private static volatile int combo;
    private static volatile int attrPoints;
    private static volatile int portalOpen;
    private static volatile int pendingBoons;
    private static volatile float lightCd;
    private static volatile float skillCd;
    private static volatile boolean relicPending;
    private static volatile boolean passivePending;
    private static volatile String subSkill = "";
    private static volatile float subCd;

    /** CTバーの満タン基準 (受信した最大値。CTが尽きて0.3秒経ったらリセット) */
    private static float lightCdMax;
    private static float skillCdMax;
    private static float subCdMax;
    /** 前回受信したCT値 (新しいCTの開始=前回0→今回>0 の検出に使う) */
    private static float lastLightCd;
    private static float lastSkillCd;
    private static float lastSubCd;
    private static long lastCdSeen;

    public static void update(float s, int f, int lv, int k, int c, int ap, int po, int pb,
                              float lcd, float scd, int rp, int pp, String ss, float sc) {
        soul = s;
        floor = f;
        level = lv;
        kills = k;
        combo = c;
        attrPoints = ap;
        portalOpen = po;
        pendingBoons = pb;
        lightCd = lcd;
        skillCd = scd;
        relicPending = rp != 0;
        passivePending = pp != 0;
        subSkill = ss == null ? "" : ss;
        subCd = sc;
        if (lcd > 0) {
            // 前回受信0→今回>0 は新しいCTの開始。古い最大値が残らないよう今回値で上書きする
            lightCdMax = lastLightCd <= 0 ? lcd : Math.max(lightCdMax, lcd);
            lastLightCd = lcd;
            lastCdSeen = System.currentTimeMillis();
        } else {
            lastLightCd = 0;
        }
        if (scd > 0) {
            // 前回受信0→今回>0 は新しいCTの開始。古い最大値が残らないよう今回値で上書きする
            skillCdMax = lastSkillCd <= 0 ? scd : Math.max(skillCdMax, scd);
            lastSkillCd = scd;
            lastCdSeen = System.currentTimeMillis();
        } else {
            lastSkillCd = 0;
        }
        if (sc > 0) {
            subCdMax = lastSubCd <= 0 ? sc : Math.max(subCdMax, sc);
            lastSubCd = sc;
            lastCdSeen = System.currentTimeMillis();
        } else {
            lastSubCd = 0;
        }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "hud"),
                ClientHud::render));
    }

    /** 共通バー (背景・枠・明部ハイライト)。MOD内の全HUDバーで同じ見た目にする */
    private static void bar(GuiGraphics g, int x, int y, int w, int h,
                            float pct, int color, boolean segments) {
        pct = Math.max(0f, Math.min(1f, pct));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xB010141E);          // 背景
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF5A6478);                   // 枠 (上)
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF5A6478);           // 枠 (下)
        g.fill(x - 1, y, x, y + h, 0xFF5A6478);                           // 枠 (左)
        g.fill(x + w, y, x + w + 1, y + h, 0xFF5A6478);                   // 枠 (右)
        int fill = (int) ((w - 2) * pct);
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
            // 明部 (上端ハイライト)
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x40FFFFFF);
        }
        if (segments) {
            // 25/50/75 の刻線
            for (int i = 1; i <= 3; i++) {
                int sx = x + w * i / 4;
                g.fill(sx, y + 1, sx + 1, y + h - 1, 0x70000000);
            }
        }
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (soul < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var font = mc.font;
        int x0 = 10;
        int y0 = 10;
        int bw = 160;
        long t = System.currentTimeMillis();
        boolean blink = (t / 400) % 2 == 0;

        // ---- 魂ゲージ (枠・刻線つき。MAX時は脈動) ----
        int gaugeColor;
        if (soul >= 100f) {
            // 緑チャンネルだけで脈動させる (0xFF からの桁上がりで色が崩れるのを防ぐ)
            int gr = 200 + (int) (Math.sin(t / 160.0) * 45);
            gaugeColor = 0xFFC00000 | (Math.min(255, gr) << 8) | 0x50;
        } else {
            gaugeColor = 0xFFB14BFF;
        }
        bar(g, x0, y0, bw, 8, soul / 100f, gaugeColor, true);
        String gaugeText = String.format("魂 %.0f/100", soul);
        g.drawString(font, gaugeText, x0, y0 + 12, soul >= 100 ? 0xFFE14AFF : 0xFFC9CFDE);
        // MAX時の奥義促し (ゲージ右側に点滅)
        if (soul >= 100 && blink) {
            g.drawString(font, "R!", x0 + bw + 6, y0 + 12, 0xFFE14AFF);
        }

        // ---- 層・レベル (区域名つき) ----
        var zone = GameData.zoneForFloor(Math.max(1, floor));
        g.drawString(font, "第" + floor + "層 [" + zone.name() + "] Lv" + level,
                x0, y0 + 24, 0xFFFFFFFF);
        int y = y0 + 38;

        // ---- CT (バー+秒数) ----
        if (t - lastCdSeen > 300) {  // CTが尽きてしばらく経ったら基準を戻す
            if (lightCd <= 0) lightCdMax = 0;
            if (skillCd <= 0) skillCdMax = 0;
            if (subCd <= 0) subCdMax = 0;
        }
        if (lightCd > 0) {
            float max = Math.max(1f, lightCdMax);
            bar(g, x0, y, 90, 5, lightCd / max, 0xFF7CC8FF, false);
            g.drawString(font, String.format("小技 %.1fs", lightCd), x0 + 96, y - 1, 0xFF7CC8FF);
            y += 11;
        }
        // sub skill (C key), CD shown while active
        var sub = SubSkills.byId(subSkill);
        g.drawString(font, "Sub: " + sub.name() + " (C)", x0, y, 0xFF9FD8FF);
        if (subCd > 0) {
            float max = Math.max(1f, subCdMax);
            bar(g, x0, y, 90, 5, subCd / max, 0xFF9FD8FF, false);
            g.drawString(font, String.format("%.1fs", subCd), x0 + 96, y - 1, 0xFF9FD8FF);
        }
        y += 12;
        if (skillCd > 0) {
            float max = Math.max(1f, skillCdMax);
            bar(g, x0, y, 90, 5, skillCd / max, 0xFFFFD23C, false);
            g.drawString(font, String.format("必殺 %.1fs", skillCd), x0 + 96, y - 1, 0xFFFFD23C);
            y += 11;
        }

        // ---- 撃破・コンボ ----
        g.drawString(font, "撃破 " + kills, x0, y, 0xFF9FB0C9);
        y += 12;
        if (combo >= 10) {
            int comboColor = combo >= 50 ? 0xFFFF8A3C : combo >= 30 ? 0xFFFFD23C : 0xFFFFFFFF;
            g.drawString(font, combo + " 連撃！", x0, y, comboColor);
            y += 12;
        }

        // ---- 未選択の提示 (点滅) ----
        if (pendingBoons > 0 && blink) {
            g.drawString(font, "◆祝福未選択 ×" + pendingBoons + " (G→祝福)", x0, y, 0xFFE14AFF);
            y += 12;
        }
        if (relicPending && blink) {
            g.drawString(font, "◆遺物未選択 (G→遺物)", x0, y, 0xFFB44BFF);
            y += 12;
        }
        if (passivePending && blink) {
            g.drawString(font, "◆技能未選択 (G→技能)", x0, y, 0xFF4BA8FF);
            y += 12;
        }
        if (attrPoints > 0 && blink) {
            g.drawString(font, "属性+" + attrPoints + " (G→ステータス)", x0, y, 0xFF7CFFB2);
            y += 12;
        }

        // ---- 転移門OPEN (点滅) ----
        // 画面上中央はバニラのボスバー (層主戦)、右上は効果アイコンと被るため
        // 左上のこのゾーンに集約する。バニラHUDと被らない位置は左上 (通常空き) のみ。
        if (portalOpen != 0 && blink) {
            g.drawString(font, "◆転移門OPEN! (G→次の層へ)", x0, y, 0xFF7CFFB2);
            y += 12;
        }
    }
}
