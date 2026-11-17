package com.abyssalloot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Map;

/**
 * 相棒の剣 — 一生使える相棒枠のエンドコンテンツ武器.
 * 原作: mm/life-pve-aibou (MythicCrucible 版 Aibou_0〜5) を Abyssal Loot 向けに再設計.
 * <ul>
 *   <li>器Lv / 器XP: 手に持って敵を倒すと器XPが溜まり、器Lvが上がるほど威力が伸びる</li>
 *   <li>段階 (0〜5): 器Lvが上限に達したら「限界突破の楔」で次の段階へ。上限と威力が上がり、見た目が変わる</li>
 *   <li>メーター: 命中で溜まり、右クリックの大技で消費して威力に変える</li>
 * </ul>
 */
public final class Aibou {
    private Aibou() {}

    static final String LV = "aibou_lv";
    static final String XP = "aibou_xp";
    static final String STAGE = "aibou_stage";
    static final String METER = "aibou_meter";
    public static final int MAX_STAGE = 5;

    public static boolean isAibou(ItemStack stack) {
        return stack.is(ModItems.ABYSS_AIBOU.get());
    }

    static int lvOf(ItemStack s) {
        return Math.max(1, Endgame.intOf(s, LV, 1));
    }

    static int xpOf(ItemStack s) {
        return Math.max(0, Endgame.intOf(s, XP, 0));
    }

    static int stageOf(ItemStack s) {
        return Math.max(0, Math.min(MAX_STAGE, Endgame.intOf(s, STAGE, 0)));
    }

    static int meterOf(ItemStack s) {
        return Math.max(0, Math.min(100, Endgame.intOf(s, METER, 0)));
    }

    /** 段階ごとの器Lv上限 (20/40/60/80/100/120) */
    public static int capOf(int stage) {
        return 20 * (stage + 1);
    }

    /** 次の器Lvまでの器XP */
    public static int xpToNext(int lv) {
        return 20 + lv * 8;
    }

    /** 限界突破に要る楔の数 (段階+1) */
    public static int stonesFor(int stage) {
        return stage + 1;
    }

    /** 見た目 = 段階そのもの (鋼 → 金の縁 → ルーン → 翼の鍔 → 蒼光の刃 → 光輝) */
    static int lookOf(int stage) {
        return stage;
    }

    /** 与ダメ倍率 (StatCalc の倍率に掛かる)。器Lv +1.5%/Lv、段階 +10%/段 */
    public static double damageMult(ItemStack held) {
        if (!isAibou(held)) return 1.0;
        return 1.0 + (lvOf(held) - 1) * 0.015 + stageOf(held) * 0.10;
    }

    private static void store(ItemStack s, int lv, int xp, int stage, int meter) {
        Endgame.putInts(s, Map.of(LV, lv, XP, xp, STAGE, stage, METER, meter), lookOf(stage));
        applyName(s);
        GearData.refreshLore(s);
    }

    static void applyName(ItemStack s) {
        s.set(DataComponents.CUSTOM_NAME, Component.literal("§b相棒の剣 §7— §e段階" + stageOf(s)));
    }

    static List<Component> lore(ItemStack s) {
        int lv = lvOf(s);
        int stage = stageOf(s);
        int cap = capOf(stage);
        String xpLine = lv >= cap
                ? (stage >= MAX_STAGE ? "§6器Lv最大 — 相棒は極みに至った" : "§c器Lv上限 — 限界突破の楔 ×" + stonesFor(stage) + " で突破")
                : String.format("§7器XP: §e%d§7/§e%d", xpOf(s), xpToNext(lv));
        return List.of(
                Component.literal("§7一生使える相棒枠。敵を倒すと器XPが溜まる。"),
                Component.literal(String.format("§7器Lv: §e%d§7/§e%d §7| 段階: §e%d§7/%d §7| 威力 §a×%.2f",
                        lv, cap, stage, MAX_STAGE, damageMult(s))),
                Component.literal(xpLine),
                Component.literal(String.format("§7メーター: §e%d%%", meterOf(s))),
                Component.literal("§e左: 命中でメーター蓄積 §7/ §e右: メーターを消費する大技"));
    }

    /* ---------------- 戦闘 ---------------- */

    /** 通常命中: メーター+4 */
    public static void onHit(ServerPlayer player) {
        ItemStack s = player.getMainHandItem();
        if (!isAibou(s)) return;
        int meter = meterOf(s);
        if (meter >= 100) return;
        store(s, lvOf(s), xpOf(s), stageOf(s), Math.min(100, meter + 4));
        if (meter + 4 >= 100) {
            player.displayClientMessage(Component.literal("§b相棒のメーターが満ちた §7— 右クリックで大技"), true);
        }
    }

    /** 右クリックの大技: メーターを消費して周囲を薙ぎ払う (メーターが多いほど強い) */
    public static void light(ServerPlayer player, double atk) {
        ItemStack s = player.getMainHandItem();
        if (!isAibou(s)) return;
        int meter = meterOf(s);
        int stage = stageOf(s);
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        if (meter < 20) {
            WeaponArts.slashCone(level, player, player.position(), look, 5, atk * 1.2,
                    new DustParticleOptions(GameData.WeaponStyle.AIBOU.color, 1.2f));
            return;
        }
        double dmg = atk * (1.5 + meter / 40.0) * (1 + stage * 0.1);
        WeaponArts.aoe(level, player, player.position(), 6 + stage * 0.5, dmg, true, ParticleTypes.SWEEP_ATTACK);
        level.sendParticles(new DustParticleOptions(0x55ffff, 1.6f), player.getX(), player.getY() + 1,
                player.getZ(), 40, 2.0, 0.8, 2.0, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        store(s, lvOf(s), xpOf(s), stage, 0);
        player.displayClientMessage(Component.literal(String.format("§e大技 §c%.0f §7(メーター%d%%消費)", dmg, meter)), true);
    }

    /** 奥義: 3連の大斬撃 → 器Lvと段階に比例した大爆発 */
    public static void art(ServerPlayer player, double atk) {
        ItemStack s = player.getMainHandItem();
        int lv = isAibou(s) ? lvOf(s) : 1;
        int stage = isAibou(s) ? stageOf(s) : 0;
        Vec3 origin = player.position();
        Vec3 look = player.getLookAngle();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            DelayRunner.after(k * 4, () -> {
                if (!player.isAlive()) return;
                double ang = Math.atan2(look.z, look.x) + (k - 1) * 0.6;
                Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                WeaponArts.slashCone((ServerLevel) player.level(), player, origin, dir, 8,
                        atk * (1.8 + stage * 0.2), ParticleTypes.SWEEP_ATTACK);
            });
        }
        DelayRunner.after(14, () -> {
            if (!player.isAlive()) return;
            WeaponArts.aoe((ServerLevel) player.level(), player, origin, 8,
                    atk * (3.5 + stage * 0.8 + lv * 0.02), true, ParticleTypes.EXPLOSION_EMITTER);
        });
    }

    /* ---------------- 器XP ---------------- */

    /** 撃破: 相棒を手に持っていれば器XPを得る (通常2 / 精鋭8 / 番人・層主40) */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() instanceof ServerPlayer) return;
        ItemStack s = player.getMainHandItem();
        if (!isAibou(s)) return;
        LivingEntity dead = event.getEntity();
        int gain = dead.getTags().contains(Enemies.WARDEN_TAG) || RunManager.BOSS_BARS.containsKey(dead) ? 40
                : Enemies.isElite(dead) ? 8 : 2;
        addXp(player, s, gain);
    }

    static void addXp(ServerPlayer player, ItemStack s, int gain) {
        int lv = lvOf(s);
        int xp = xpOf(s);
        int stage = stageOf(s);
        int cap = capOf(stage);
        if (lv >= cap) {
            store(s, lv, 0, stage, meterOf(s));
            return;
        }
        xp += gain;
        boolean up = false;
        while (lv < cap && xp >= xpToNext(lv)) {
            xp -= xpToNext(lv);
            lv++;
            up = true;
        }
        if (lv >= cap) xp = 0;
        store(s, lv, xp, stage, meterOf(s));
        if (up) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.7f, 1.6f);
            player.sendSystemMessage(Component.literal("§b[相棒] §e器Lv " + lv + " §7に成長した"
                    + (lv >= cap ? (stage >= MAX_STAGE ? " §6— 極み" : " §c— 上限。限界突破の楔で突破できる") : "")));
        }
    }

    /* ---------------- 限界突破 ---------------- */

    /** 限界突破の楔を右クリック: オフハンド→インベントリの順に相棒の剣を探して突破する */
    @SubscribeEvent
    public static void onUseStone(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stone = event.getItemStack();
        if (!stone.is(ModItems.AIBOU_LIMIT_STONE.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        limitBreak(player, stone);
    }

    /** 限界突破を試みる (楔を消費して段階+1)。成功したら true */
    public static boolean limitBreak(ServerPlayer player, ItemStack stone) {
        ItemStack sword = findAibou(player);
        if (sword.isEmpty()) {
            player.displayClientMessage(Component.literal("§c相棒の剣を持っていない"), true);
            return false;
        }
        int stage = stageOf(sword);
        int lv = lvOf(sword);
        if (stage >= MAX_STAGE) {
            player.displayClientMessage(Component.literal("§6相棒はすでに極みに至っている"), true);
            return false;
        }
        if (lv < capOf(stage)) {
            player.displayClientMessage(Component.literal("§7器Lvが上限 (" + capOf(stage) + ") に達していない §8(現在 "
                    + lv + ")"), true);
            return false;
        }
        int need = stonesFor(stage);
        if (stone.getCount() < need) {
            player.displayClientMessage(Component.literal("§c限界突破の楔が足りない §7(" + stone.getCount() + "/" + need + ")"), true);
            return false;
        }
        stone.shrink(need);
        store(sword, lv, 0, stage + 1, meterOf(sword));
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                60, 0.8, 1.0, 0.8, 0.4);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1f, 1.1f);
        player.sendSystemMessage(Component.literal("§b[相棒] §d§l限界突破！ §r§e段階" + (stage + 1)
                + " §7— 器Lv上限 " + capOf(stage + 1) + "・威力 ×" + String.format("%.2f", damageMult(sword))));
        return true;
    }

    private static ItemStack findAibou(ServerPlayer player) {
        if (isAibou(player.getOffhandItem())) return player.getOffhandItem();
        if (isAibou(player.getMainHandItem())) return player.getMainHandItem();
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (isAibou(s)) return s;
        }
        return ItemStack.EMPTY;
    }
}
