package com.abyssalloot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * クライアント専用: 魂ゲージ・層・レベルのHUD.
 * 数値はサーバーからの HudSync で更新される (未受信時は表示しない).
 * サーバー値は HudSync record を丸ごと保持する (個別volatileフィールドの分散を避ける).
 * 区域名は GameData (MC非依存) から層番号で引くため追加パケットは不要.
 * 小技/必殺のCTは残存時のみ表示 (CT中はサーバーが0.2秒ごとに再送する).
 *
 * レイアウト方針: 全情報を画面左上 (x=10, y=10 から縦に流す) に集約する。
 * バニラHUDの既存ゾーン — 上中央=ボスバー / 右上=効果アイコン / 下中央=ホットバー・体力・
 * 経験値 / 左下=チャット — との被りを避けるため、左上 (通常は空いている領域) のみを使用する.
 */
public final class ClientHud {
    private ClientHud() {}

    /** サーバーからの最新HUD状態 (未受信は null)。受信のたびに record を差し替える */
    private static volatile ModPackets.HudSync last;

    /** CTバーの満タン基準 (受信した最大値。CTが尽きて0.3秒経ったらリセット) */
    private static float lightCdMax;
    private static float skillCdMax;
    private static float subCdMax;
    /** 前回受信したCT値 (新しいCTの開始=前回0→今回>0 の検出に使う) */
    private static float lastLightCd;
    private static float lastSkillCd;
    private static float lastSubCd;
    private static long lastCdSeen;

    public static void update(ModPackets.HudSync p) {
        last = p;
        float lcd = p.lightCd();
        float scd = p.skillCd();
        float sc = p.subCd();
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
        var hud = last;
        if (hud == null || hud.soul() < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var font = mc.font;
        int x0 = 10;
        int y0 = 10;
        int bw = 160;
        long t = System.currentTimeMillis();
        boolean blink = (t / 400) % 2 == 0;
        float soul = hud.soul();
        float lightCd = hud.lightCd();
        float skillCd = hud.skillCd();
        float subCd = hud.subCd();

        renderSoulGauge(g, font, hud, x0, y0, bw, soul, t, blink);
        renderFloorLine(g, font, hud, x0, y0);
        int y = renderCooldowns(g, font, hud, x0, y0 + 38, t, lightCd, skillCd, subCd);
        y = renderKillsAndCombo(g, font, hud, x0, y);
        renderPendingPrompts(g, font, hud, x0, y, blink);
        // 低HPの警告 (画面周縁が赤く染まる)
        renderLowHealthWarning(g, hud, g.guiWidth(), g.guiHeight());
    }

    /** 低HPの警告表現 (HP30%未満で周縁が赤く、HP-critical ほど強い) */
    private static void renderLowHealthWarning(GuiGraphics g, ModPackets.HudSync hud, int w, int h) {
        float max = hud.maxHp();
        if (max <= 0f) return;
        float ratio = hud.hp() / max;
        if (ratio >= 0.3f) return;
        // 30%未満で分级 → 0%付近で最大
        float danger = Math.min(1f, (0.3f - ratio) / 0.3f);
        // 鼓動に合わせて明滅させる
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 140.0);
        int alpha = (int) (danger * 90 * pulse);
        if (alpha <= 2) return;
        int band = 16;
        for (int i = 0; i < 5; i++) {
            int a = (int) (alpha * (1.0 - i / 5.0));
            if (a <= 1) continue;
            int inset = i * band;
            int color = (a << 24) | 0x00C02020;
            g.fill(0, inset, w, inset + band, color);
            g.fill(0, h - inset - band, w, h - inset, color);
            g.fill(inset, 0, inset + band, h, color);
            g.fill(w - inset - band, 0, w - inset, h, color);
        }
    }

    /** 魂ゲージ (枠・刻線つき。MAX時は脈動) */
    private static void renderSoulGauge(GuiGraphics g, Font font, ModPackets.HudSync hud,
                                        int x0, int y0, int bw, float soul, long t, boolean blink) {
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
    }

    /** 層・レベル (区域名つき) */
    private static void renderFloorLine(GuiGraphics g, Font font, ModPackets.HudSync hud, int x0, int y0) {
        var zone = GameData.zoneForFloor(Math.max(1, hud.floor()));
        g.drawString(font, "第" + hud.floor() + "層 [" + zone.name() + "] Lv" + hud.level(),
                x0, y0 + 24, 0xFFFFFFFF);
    }

    /** CT (バー+秒数)。描画後の次行Yを返す */
    private static int renderCooldowns(GuiGraphics g, Font font, ModPackets.HudSync hud, int x0, int y,
                                       long t, float lightCd, float skillCd, float subCd) {
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
        var sub = SubSkills.byId(hud.subSkill());
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
        return y;
    }

    /** 撃破数・コンボ・残存敵数。描画後の次行Yを返す */
    private static int renderKillsAndCombo(GuiGraphics g, Font font, ModPackets.HudSync hud, int x0, int y) {
        g.drawString(font, "撃破 " + hud.kills(), x0, y, 0xFF9FB0C9);
        y += 12;
        int combo = hud.combo();
        if (combo >= 10) {
            int comboColor = combo >= 50 ? 0xFFFF8A3C : combo >= 30 ? 0xFFFFD23C : 0xFFFFFFFF;
            g.drawString(font, combo + " 連撃！", x0, y, comboColor);
            y += 12;
        }
        // 残存敵数 (層クリアまでの目標が分かるように)
        if (hud.waveTotal() > 0 && hud.portalOpen() == 0) {
            int left = Math.max(0, hud.waveTotal() - hud.floorKills());
            g.drawString(font, "残存 " + left + " 体", x0, y, 0xFFFFB86B);
            y += 12;
        }
        return y;
    }

    /** 未選択の提示・転移門 (点滅) */
    private static void renderPendingPrompts(GuiGraphics g, Font font, ModPackets.HudSync hud,
                                             int x0, int y, boolean blink) {
        if (hud.pendingBoons() > 0 && blink) {
            g.drawString(font, "◆祝福未選択 ×" + hud.pendingBoons() + " (G→祝福)", x0, y, 0xFFE14AFF);
            y += 12;
        }
        if (hud.relicPending() != 0 && blink) {
            g.drawString(font, "◆遺物未選択 (G→遺物)", x0, y, 0xFFB44BFF);
            y += 12;
        }
        if (hud.passivePending() != 0 && blink) {
            g.drawString(font, "◆技能未選択 (G→技能)", x0, y, 0xFF4BA8FF);
            y += 12;
        }
        if (hud.attrPoints() > 0 && blink) {
            g.drawString(font, "属性+" + hud.attrPoints() + " (G→ステータス)", x0, y, 0xFF7CFFB2);
            y += 12;
        }
        // 画面上中央はバニラのボスバー (層主戦)、右上は効果アイコンと被るため
        // 左上のこのゾーンに集約する。バニラHUDと被らない位置は左上 (通常空き) のみ。
        if (hud.portalOpen() != 0 && blink) {
            g.drawString(font, "◆転移門OPEN! (門をくぐって次層へ)", x0, y, 0xFF7CFFB2);
        }
    }
}
