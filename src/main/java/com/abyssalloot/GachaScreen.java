package com.abyssalloot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * 装備ガチャの画面 (クライアント専用)。
 * 引いた結果はカードの裏面から 1 枚ずつめくる。めくった瞬間に希少度の色で光り、レジェンド・神話は音と閃光で知らせる。
 * 支払い・抽選・受け渡しはすべてサーバー ({@link Gacha}) が行い、ここは表示だけ。
 */
public class GachaScreen extends Screen {
    private static final int[] RARITY_COLORS = {0xFFB8B8C8, 0xFF5CC8FF, 0xFFFFE14A, 0xFFFF9A2E, 0xFFFF5CFF};
    private static final String[] RARITY_NAMES = {"通常", "魔法", "レア", "レジェンド", "神話"};
    private static final int PW = 320, PH = 214;
    private static final int CARD_W = 52, CARD_H = 62;
    private static final int REVEAL_GAP = 5;

    private List<ItemStack> items = new ArrayList<>();
    private int pityLeft, shards;
    private boolean free;
    private int ticks;
    private int revealStart;
    private int lastRevealed = -1;
    private float flash;

    public GachaScreen(ModPackets.GachaResult first) {
        super(Component.literal("深淵の装備ガチャ"));
        accept(first);
    }

    /** サーバーからの結果を受け取る (空なら所持状況の更新だけ) */
    public void accept(ModPackets.GachaResult r) {
        pityLeft = r.pityLeft();
        shards = r.shards();
        free = r.free();
        if (!r.items().isEmpty()) {
            items = new ArrayList<>(r.items());
            revealStart = ticks + 6;
            lastRevealed = -1;
        }
    }

    private static int rankOf(ItemStack s) {
        return GameData.rarityRank(GearData.rarityOf(s));
    }

    private int x0() {
        return width / 2 - PW / 2;
    }

    private int y0() {
        return height / 2 - PH / 2;
    }

    private static void pull(int n) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ModPackets.GachaPacket(n)));
    }

    @Override
    protected void init() {
        int by = y0() + PH - 28;
        addRenderableWidget(UiButton.of("1 回 (" + (free ? "無料" : "欠片 " + Gacha.COST_ONE) + ")", x0() + 12, by, 96, 18,
                UiKit.CYAN, () -> pull(1)));
        addRenderableWidget(UiButton.of("10 連 (" + (free ? "無料" : "欠片 " + Gacha.COST_TEN) + ")", x0() + 112, by, 110, 18,
                UiKit.GOLD, () -> pull(10)));
        addRenderableWidget(UiButton.of("閉じる", x0() + PW - 72, by, 60, 18, UiKit.SUB, this::onClose));
    }

    @Override
    public void tick() {
        ticks++;
        flash = Math.max(0, flash - 0.08f);
        int shown = revealedCount();
        while (lastRevealed + 1 < shown) {  // 新しくめくれたカードの音と閃光
            lastRevealed++;
            int rank = rankOf(items.get(lastRevealed));
            var sm = Minecraft.getInstance().getSoundManager();
            if (rank >= 4) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
                flash = 1f;
            } else if (rank == 3) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.3f));
                flash = Math.max(flash, 0.6f);
            } else {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 0.8f + rank * 0.2f));
            }
        }
    }

    private int revealedCount() {
        if (items.isEmpty() || ticks < revealStart) return 0;
        return Math.min(items.size(), (ticks - revealStart) / REVEAL_GAP + 1);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = x0(), y0 = y0();
        UiKit.panel(g, x0, y0, x0 + PW, y0 + PH, UiKit.VIOLET);
        UiKit.titleBar(g, font, x0, x0 + PW, y0, title.getString(), UiKit.VIOLET);
        String own = free ? "クリエイティブ: 無料" : "所持欠片 " + shards;
        UiKit.chip(g, font, own, x0 + 10, y0 + 20, UiKit.GOLD);
        String pity = "神話確定まで あと " + pityLeft + " 回";
        UiKit.chip(g, font, pity, x0 + PW - font.width(pity) - 18, y0 + 20, 0xFFFF5CFF);
        // カード 5 x 2
        int gx = x0 + (PW - (5 * CARD_W + 4 * 6)) / 2, gy = y0 + 38;
        int shown = revealedCount();
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < 10; i++) {
            int cx = gx + (i % 5) * (CARD_W + 6), cy = gy + (i / 5) * (CARD_H + 6);
            if (i >= items.size()) {
                g.fill(cx, cy, cx + CARD_W, cy + CARD_H, 0x40101020);
                g.renderOutline(cx, cy, CARD_W, CARD_H, 0x40FFFFFF);
                continue;
            }
            if (i >= shown) {  // 裏面
                float wob = (float) Math.sin((ticks + partialTick) * 0.4 + i) * 0.5f + 0.5f;
                g.fillGradient(cx, cy, cx + CARD_W, cy + CARD_H, 0xFF2A2050, 0xFF120E26);
                g.renderOutline(cx, cy, CARD_W, CARD_H, UiKit.alpha(UiKit.VIOLET, (int) (120 + 100 * wob)));
                g.drawCenteredString(font, "?", cx + CARD_W / 2, cy + CARD_H / 2 - 4, UiKit.VIOLET);
                continue;
            }
            ItemStack s = items.get(i);
            int rank = rankOf(s);
            int col = RARITY_COLORS[rank];
            float age = Math.min(1f, (ticks - revealStart - i * REVEAL_GAP + partialTick) / 6f);
            g.fillGradient(cx, cy, cx + CARD_W, cy + CARD_H, UiKit.alpha(col, (int) (40 + 50 * (1 - age))), 0xE0100C1C);
            g.renderOutline(cx, cy, CARD_W, CARD_H, col);
            if (rank >= 3) g.renderOutline(cx - 1, cy - 1, CARD_W + 2, CARD_H + 2, UiKit.alpha(col, 0x90));
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(cx + CARD_W / 2f - 16, cy + 8);
            pose.scale(2f, 2f);
            g.renderItem(s, 0, 0);
            pose.popMatrix();
            g.drawCenteredString(font, RARITY_NAMES[rank], cx + CARD_W / 2, cy + CARD_H - 20, col);
            String name = s.getHoverName().getString().replaceAll("§.", "");
            if (font.width(name) > CARD_W - 4) name = font.plainSubstrByWidth(name, CARD_W - 10) + "…";
            g.drawCenteredString(font, name, cx + CARD_W / 2, cy + CARD_H - 10, UiKit.TEXT);
            if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) hovered = s;
        }
        // 排出率
        var sb = new StringBuilder("排出率 ");
        for (int i = 0; i < 5; i++) sb.append(RARITY_NAMES[i]).append(' ').append(Gacha.RATES[i]).append("% ");
        g.drawCenteredString(font, sb.toString().trim(), x0 + PW / 2, y0 + PH - 42, UiKit.MUTED);
        super.render(g, mouseX, mouseY, partialTick);
        if (flash > 0) g.fill(0, 0, width, height, UiKit.alpha(0xFFFFF0FF, (int) (flash * 110)));
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
