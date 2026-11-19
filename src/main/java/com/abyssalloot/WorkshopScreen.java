package com.abyssalloot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * 装備工房 (クリエイティブ専用・クライアント画面)。武器/防具の性能をすべて選んで作る。
 * 選択肢は共通コード (GameData・GearData) の表から作るので、武器種・特性・接辞が増えても自動で並ぶ。
 * 作成は {@link ModPackets.WorkshopPacket} でサーバーへ送り、サーバー側でクリエイティブかを再確認する。
 */
public class WorkshopScreen extends Screen {
    private static final String[] RARITIES = {"normal", "magic", "rare", "legendary", "mythic"};
    private static final String[] RARITY_NAMES = {"通常", "魔法", "レア", "レジェンド", "神話"};
    private static final String[] PIECES = {"helm", "chest", "legs", "boots"};
    private static final String[] PIECE_NAMES = {"兜", "鎧", "脚甲", "靴"};
    private static final int SLOTS = 6;
    private static final int PW = 360, PH = 250;

    private boolean weapon = true;
    private int style, piece = 1, set, rarity = 4, trait, skill, forge;
    private final int[] affixKey = new int[SLOTS];  // 0 = なし、それ以外は pool の添字 + 1
    private final double[] affixVal = new double[SLOTS];
    private EditBox nameBox;
    private String name = "";

    public WorkshopScreen() {
        super(Component.literal("装備工房 (クリエイティブ)"));
    }

    private static List<GameData.WeaponStyle> styles() {
        var out = new ArrayList<GameData.WeaponStyle>();
        for (var w : GameData.WeaponStyle.VALUES) if (!w.isEndgame()) out.add(w);
        return out;
    }

    private List<GameData.AffixKey> pool() {
        return weapon ? GameData.WEAPON_AFFIXES : GameData.ARMOR_AFFIXES;
    }

    private List<GameData.GearSkill> skills() {
        return weapon ? GameData.WEAPON_SKILLS : GameData.ARMOR_SKILLS;
    }

    private int x0() {
        return width / 2 - PW / 2;
    }

    private int y0() {
        return height / 2 - PH / 2;
    }

    private void cycle(Runnable r) {
        if (nameBox != null) name = nameBox.getValue();
        r.run();
        rebuildWidgets();
    }

    private void row(int y, String label, String value, int accent, Runnable prev, Runnable next) {
        int x = x0() + 14;
        addRenderableWidget(UiButton.of("◀", x + 70, y, 16, 14, accent, () -> cycle(prev)));
        addRenderableWidget(UiButton.of(value, x + 88, y, 140, 14, accent, () -> cycle(next)));
        addRenderableWidget(UiButton.of("▶", x + 230, y, 16, 14, accent, () -> cycle(next)));
    }

    @Override
    protected void init() {
        int y = y0() + 22;
        int x = x0() + 14;
        addRenderableWidget(UiButton.of(weapon ? "▶ 武器" : "武器", x, y, 60, 14, weapon ? UiKit.GOLD : UiKit.SUB,
                () -> cycle(() -> weapon = true)));
        addRenderableWidget(UiButton.of(!weapon ? "▶ 防具" : "防具", x + 64, y, 60, 14, !weapon ? UiKit.CYAN : UiKit.SUB,
                () -> cycle(() -> weapon = false)));
        y += 18;
        var st = styles();
        if (weapon) {
            row(y, "武器種", st.get(Math.floorMod(style, st.size())).weaponName, UiKit.GOLD,
                    () -> style--, () -> style++);
        } else {
            row(y, "部位", PIECE_NAMES[Math.floorMod(piece, 4)], UiKit.CYAN, () -> piece--, () -> piece++);
            y += 16;
            var sets = GameData.ARMOR_SETS;
            String sname = Math.floorMod(set, sets.size() + 1) == 0 ? "なし (基本防具)"
                    : sets.get(Math.floorMod(set, sets.size() + 1) - 1).name();
            row(y, "セット", sname, UiKit.CYAN, () -> set--, () -> set++);
        }
        y += 16;
        row(y, "希少度", RARITY_NAMES[Math.floorMod(rarity, 5)], UiKit.VIOLET, () -> rarity--, () -> rarity++);
        if (weapon) {
            y += 16;
            int n = GearData.TRAITS.length + 1;
            String tname = Math.floorMod(trait, n) == 0 ? "なし" : GearData.TRAITS[Math.floorMod(trait, n) - 1].name();
            row(y, "特性", tname, UiKit.RED, () -> trait--, () -> trait++);
        }
        y += 16;
        var sk = skills();
        String skname = Math.floorMod(skill, sk.size() + 1) == 0 ? "なし" : sk.get(Math.floorMod(skill, sk.size() + 1) - 1).name();
        row(y, "スキル", skname, UiKit.GREEN, () -> skill--, () -> skill++);
        y += 16;
        row(y, "鍛造", "+" + forge, UiKit.GOLD, () -> forge = Math.max(0, forge - 1), () -> forge = Math.min(5, forge + 1));
        // 接辞
        y += 20;
        var pool = pool();
        for (int i = 0; i < SLOTS; i++) {
            final int k = i;
            int ky = y + i * 16;
            int idx = Math.floorMod(affixKey[i], pool.size() + 1);
            String kname = idx == 0 ? "— なし —" : GearData.affixLabel(pool.get(idx - 1).key());
            addRenderableWidget(UiButton.of(kname, x, ky, 150, 14, UiKit.BLUE, () -> cycle(() -> {
                affixKey[k] = Math.floorMod(affixKey[k] + 1, pool.size() + 1);
                affixVal[k] = affixKey[k] == 0 ? 0 : pool.get(affixKey[k] - 1).max();
            })));
            if (idx != 0) {
                var def = pool.get(idx - 1);
                double step = Math.max(0.1, Math.round(def.max() / 5 * 10) / 10.0);
                addRenderableWidget(UiButton.of("−", x + 154, ky, 16, 14, UiKit.SUB,
                        () -> cycle(() -> affixVal[k] = Math.max(-def.max() * 3, affixVal[k] - step))));
                addRenderableWidget(UiButton.of(String.format("%.1f", affixVal[k]), x + 172, ky, 44, 14, UiKit.SUB,
                        () -> cycle(() -> affixVal[k] = def.max())));
                addRenderableWidget(UiButton.of("+", x + 218, ky, 16, 14, UiKit.SUB,
                        () -> cycle(() -> affixVal[k] = Math.min(def.max() * 3, affixVal[k] + step))));
                addRenderableWidget(UiButton.of("最大×3", x + 236, ky, 40, 14, UiKit.GOLD,
                        () -> cycle(() -> affixVal[k] = def.max() * 3)));
            }
        }
        int by = y0() + PH - 26;
        nameBox = new EditBox(font, x, by, 150, 16, Component.literal("名前"));
        nameBox.setMaxLength(40);
        nameBox.setValue(name);
        nameBox.setHint(Component.literal("名前 (空欄で自動)"));
        addRenderableWidget(nameBox);
        addRenderableWidget(UiButton.of("作成して受け取る", x + 160, by, 110, 16, UiKit.GREEN, this::create));
        addRenderableWidget(UiButton.of("閉じる", x0() + PW - 64, by, 50, 16, UiKit.SUB, this::onClose));
    }

    private void create() {
        var keys = new ArrayList<String>();
        var vals = new ArrayList<Double>();
        var pool = pool();
        for (int i = 0; i < SLOTS; i++) {
            int idx = Math.floorMod(affixKey[i], pool.size() + 1);
            if (idx == 0) continue;
            String key = pool.get(idx - 1).key();
            if (keys.contains(key)) continue;
            keys.add(key);
            vals.add(affixVal[i]);
        }
        var st = styles();
        String styleId = weapon ? st.get(Math.floorMod(style, st.size())).id : "";
        var sets = GameData.ARMOR_SETS;
        int si = Math.floorMod(set, sets.size() + 1);
        String setId = weapon || si == 0 ? "" : sets.get(si - 1).id();
        int tn = GearData.TRAITS.length + 1;
        String traitId = !weapon || Math.floorMod(trait, tn) == 0 ? "" : GearData.TRAITS[Math.floorMod(trait, tn) - 1].id();
        var sk = skills();
        int ki = Math.floorMod(skill, sk.size() + 1);
        String skillId = ki == 0 ? "" : sk.get(ki - 1).id();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new ModPackets.WorkshopPacket(weapon, styleId,
                    PIECES[Math.floorMod(piece, 4)], setId, RARITIES[Math.floorMod(rarity, 5)], traitId, skillId, forge,
                    keys, vals, nameBox.getValue())));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = x0(), y0 = y0();
        UiKit.panel(g, x0, y0, x0 + PW, y0 + PH, UiKit.GREEN);
        UiKit.titleBar(g, font, x0, x0 + PW, y0, title.getString(), UiKit.GREEN);
        int y = y0 + 40, x = x0 + 14;
        String[] labels = weapon ? new String[]{"武器種", "希少度", "特性", "スキル", "鍛造"}
                : new String[]{"部位", "セット", "希少度", "スキル", "鍛造"};
        for (String l : labels) {
            g.drawString(font, l, x, y + 3, UiKit.SUB, false);
            y += 16;
        }
        UiKit.section(g, font, "接辞 (クリックで種類を切替・値は ± / 中央で上限値)", x, y + 6, UiKit.BLUE);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
