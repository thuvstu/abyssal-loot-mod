package com.abyssalloot;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 追加の生活道具の能力。
 * <ul>
 *   <li>深淵の掘削槌: つるはしで掘れるブロックを、掘った面に垂直な 3×3 でまとめて掘る (しゃがむと 1 マス)</li>
 *   <li>深淵の伐採斧: 原木を切ると、つながった原木 (最大 96) をまとめて伐る (しゃがむと 1 本)</li>
 *   <li>深淵の収穫鎌: 熟した作物を右クリックで、周囲 5×5 の熟した作物を収穫して植え直す</li>
 * </ul>
 * いずれも出撃中のアリーナの中では範囲の効果を出さない (ArenaGuard の保護と食い違わないように)。
 */
public final class ToolAbilities {
    private ToolAbilities() {}

    /** 範囲で壊している最中のプレイヤー (自分の破壊イベントで再帰しないように) */
    private static final Set<UUID> BUSY = new HashSet<>();
    private static final int MAX_LOGS = 96;

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (BUSY.contains(player.getUUID()) || player.isShiftKeyDown() || event.isCanceled()) return;
        if (ArenaGuard.inActiveArena(level, event.getPos())) return;
        var held = player.getMainHandItem();
        BlockState state = event.getState();
        if (held.is(ModItems.ABYSS_EXCAVATOR.get()) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            Direction face = facing(player);
            BUSY.add(player.getUUID());
            try {
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        if (a == 0 && b == 0) continue;
                        BlockPos p = switch (face.getAxis()) {
                            case X -> event.getPos().offset(0, a, b);
                            case Y -> event.getPos().offset(a, 0, b);
                            case Z -> event.getPos().offset(a, b, 0);
                        };
                        BlockState s = level.getBlockState(p);
                        if (s.is(BlockTags.MINEABLE_WITH_PICKAXE) && s.getDestroySpeed(level, p) >= 0
                                && s.getDestroySpeed(level, p) <= state.getDestroySpeed(level, event.getPos()) + 3) {
                            if (!player.gameMode.destroyBlock(p)) break;
                            if (held.isEmpty()) break;
                        }
                    }
                }
            } finally {
                BUSY.remove(player.getUUID());
            }
        } else if (held.is(ModItems.ABYSS_LUMBER_AXE.get()) && state.is(BlockTags.LOGS)) {
            Block log = state.getBlock();
            BUSY.add(player.getUUID());
            try {
                var queue = new ArrayDeque<BlockPos>();
                var seen = new HashSet<BlockPos>();
                queue.add(event.getPos());
                seen.add(event.getPos());
                int cut = 0;
                while (!queue.isEmpty() && cut < MAX_LOGS) {
                    BlockPos cur = queue.poll();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = 0; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos n = cur.offset(dx, dy, dz);
                                if (!seen.add(n) || !level.getBlockState(n).is(log)) continue;
                                queue.add(n);
                                if (!player.gameMode.destroyBlock(n) || held.isEmpty()) return;
                                cut++;
                            }
                        }
                    }
                }
            } finally {
                BUSY.remove(player.getUUID());
            }
        }
    }

    /** 掘った面の向き (視線の当たり判定から) */
    private static Direction facing(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 0, false);
        if (hit instanceof BlockHitResult bh) return bh.getDirection();
        return Direction.orderedByNearest(player)[0].getOpposite();
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        ItemStack held = event.getItemStack();
        if (!held.is(ModItems.ABYSS_SICKLE.get())) return;
        BlockState clicked = level.getBlockState(event.getPos());
        if (!(clicked.getBlock() instanceof CropBlock crop0) || !crop0.isMaxAge(clicked)) return;
        int harvested = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = event.getPos().offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) continue;
                    if (ArenaGuard.inActiveArena(level, p)) continue;
                    for (ItemStack drop : Block.getDrops(s, level, p, null, player, held)) {
                        LootHandler.giveOrDrop(player, drop);
                    }
                    level.setBlock(p, crop.getStateForAge(0), Block.UPDATE_ALL);  // 植え直し
                    harvested++;
                }
            }
        }
        if (harvested > 0) {
            held.hurtAndBreak(Math.max(1, harvested / 4), player, EquipmentSlot.MAINHAND);
            player.swing(event.getHand(), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
