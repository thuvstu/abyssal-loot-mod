package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 転移門: 層をクリアすると「次の層への入り口」が目の前に実体として開く.
 * <ul>
 *   <li>門は黒曜石の枠 (4×5) と中の裂け目 (粒子) で表現する</li>
 *   <li>門の中に立ち止まると 0.5 秒で次層へ移行する (右クリックでも即進行)</li>
 *   <li>層移動・リタイアで自動的に撤去される</li>
 * </ul>
 */
public final class TransferGate {
    private TransferGate() {}

    /** 門の幅 (ブロック) */
    private static final int WIDTH = 4;
    /** 門の高さ (ブロック) */
    private static final int HEIGHT = 5;
    /** 中に停留してから次層へ移るまでの時間 (tick) */
    private static final int CHARGE_TICKS = 10;
    /** 候補位置 (プレイヤー surroundings・8方向) */
    private static final int[][] OFFSETS = {
            {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {2, 2}, {-2, 2}, {2, -2}, {-2, -2}
    };

    /** 門の中にいた時間 (tick) */
    private static final Map<UUID, Integer> CHARGE = new HashMap<>();

    /** 門を開く (層クリア時・プレイヤーの近くに設置) */
    public static void open(ServerPlayer player, ServerLevel level) {
        close(player);
        BlockPos base = findSpot(player);
        if (base == null) return;
        build(level, base);
        // ディメンションも保存する (別ディメンションの座標と誤認しないため)
        player.setData(ModAttachments.GATE.get(), level.dimension().identifier().toString()
                + "," + base.getX() + "," + base.getY() + "," + base.getZ());
        level.playSound(null, base, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    /** 門を撤去する (層移動・リタイア時) */
    public static void close(ServerPlayer player) {
        String raw = player.getData(ModAttachments.GATE.get());
        if (raw == null || raw.isBlank()) return;
        BlockPos base = parse(raw, player);
        if (base != null && player.level() instanceof ServerLevel level) {
            clear(level, base);
        }
        player.setData(ModAttachments.GATE.get(), "");
        CHARGE.remove(player.getUUID());
    }

    /** 毎tick: 門の演出と、中に居たら次層への移行 */
    public static void tick(ServerPlayer player) {
        if (player.getData(ModAttachments.PORTAL_OPEN.get()) == 0) {
            CHARGE.remove(player.getUUID());
            return;
        }
        BlockPos base = gateBase(player);
        if (base == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // 門の粒子演出 (4tickに1度)
        if (level.getGameTime() % 4 == 0) {
            double cx = base.getX() + 2.0;
            double cy = base.getY() + 2.0;
            double cz = base.getZ() + 0.5;
            level.sendParticles(ParticleTypes.PORTAL, cx, cy, cz, 10, 0.6, 1.0, 0.15, 0.04);
            level.sendParticles(ParticleTypes.SOUL, cx, cy, cz, 4, 0.5, 1.0, 0.15, 0.02);
        }

        if (!inside(player, base)) {
            CHARGE.remove(player.getUUID());
            return;
        }
        int charged = CHARGE.merge(player.getUUID(), 1, Integer::sum);
        // 溜まるほど門が明るく光る
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                base.getX() + 2.0, base.getY() + 2.0, base.getZ() + 0.5, 2, 0.5, 1.0, 0.1, 0.02);
        if (charged >= CHARGE_TICKS) {
            CHARGE.remove(player.getUUID());
            RunManager.tryNextFloor(player);
        }
    }

    /** 門を右クリックしたときの即時進行 */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ServerPlayer player = event.getEntity() instanceof ServerPlayer p ? p : null;
        if (player == null) return;
        if (player.getData(ModAttachments.PORTAL_OPEN.get()) == 0) return;
        BlockPos base = gateBase(player);
        if (base == null) return;
        BlockPos clicked = event.getPos();
        if (!isGateBlock(base, clicked)) return;
        event.setCanceled(true);
        RunManager.tryNextFloor(player);
    }

    /* ---------------- 内部処理 ---------------- */

    /** 門の基準座標 (未設置・別ディメンションなら null) */
    public static BlockPos gateBase(ServerPlayer player) {
        return parse(player.getData(ModAttachments.GATE.get()), player);
    }

    /** 設置位置の決定: プレイヤーの近くで、プレイヤーが埋まらない場所を探す */
    private static BlockPos findSpot(ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        BlockPos center = ArenaManager.arenaCenterOf(player);
        for (int[] off : OFFSETS) {
            BlockPos base = origin.offset(off[0], 0, off[1]);
            // プレイヤーと門の内部が重ならないこと (即時移行の誤発動防止)
            double dx = player.getX() - (base.getX() + 2.0);
            double dz = player.getZ() - (base.getZ() + 0.5);
            if (dx * dx + dz * dz < 4.0) continue;
            // アリーナの塀に埋めないこと
            if (center != null) {
                double cx = base.getX() + 2.0 - center.getX();
                double cz = base.getZ() + 0.5 - center.getZ();
                if (cx * cx + cz * cz > 14 * 14) continue;
            }
            return base;
        }
        return null;
    }

    /** 門の構造を組む (左右と上が黒曜石の枠・床はそのまま使う) */
    private static void build(ServerLevel level, BlockPos base) {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                boolean frame = x == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
                BlockPos p = base.offset(x, y, 0);
                if (frame) {
                    level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 3);
                } else {
                    // 開口部は必ず空ける (障害物に塞がれないように)
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 上の灯り (遠くからでも門が分かるように)
        level.setBlock(base.offset(1, HEIGHT, 0), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        level.setBlock(base.offset(2, HEIGHT, 0), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
    }

    /** 門の構造を撤去する (置いたブロックだけ消す) */
    private static void clear(ServerLevel level, BlockPos base) {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos p = base.offset(x, y, 0);
                var state = level.getBlockState(p);
                if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.SOUL_LANTERN)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /** クリック位置が門の一部か */
    private static boolean isGateBlock(BlockPos base, BlockPos clicked) {
        int dx = clicked.getX() - base.getX();
        int dy = clicked.getY() - base.getY();
        int dz = clicked.getZ() - base.getZ();
        if (dz != 0 && dz != 1) return false;
        if (dx < 0 || dx >= WIDTH) return false;
        return dy >= 0 && dy <= HEIGHT;
    }

    /** プレイヤーが門の開口部の中にいるか (床の上に立って開口部の内側) */
    private static boolean inside(ServerPlayer player, BlockPos base) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean inX = x >= base.getX() + 0.9 && x <= base.getX() + 2.1;
        boolean inY = y >= base.getY() - 0.6 && y <= base.getY() + 3.6;
        boolean inZ = z >= base.getZ() - 0.6 && z <= base.getZ() + 1.6;
        return inX && inY && inZ;
    }

    /** "dim,x,y,z" の解析 (現在いるディメンションと一致しなければ null) */
    private static BlockPos parse(String raw, ServerPlayer player) {
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.split(",");
        if (p.length != 4) return null;
        if (!p[0].trim().equals(player.level().dimension().identifier().toString())) return null;
        try {
            return new BlockPos(Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                    Integer.parseInt(p[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
