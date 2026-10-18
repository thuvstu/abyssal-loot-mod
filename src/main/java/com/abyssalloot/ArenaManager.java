package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;



import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;



import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * アリーナ構築・結界・湧き位置・罠 (RunManager から分離)。
 */
public final class ArenaManager {
    private ArenaManager() {}

    /** アリーナ半径 (MOB収容の境界も兼ねる) */
    public static final int ARENA_R = 20;
    /** 塀の高さ */
    public static final int WALL_H = 5;

    /**
     * 戦闘威力のアリーナ補正。アリーナ内なら特攻1.5倍、範囲外・非出撃時は0.5倍に抑制。
     * バニラ基準に寄せつつ、アリーナでは強く遊べるよう調整する。
     */
    public static double arenaPowerMult(ServerPlayer player) {
        if (!RunManager.isRunLocked(player)) return 0.5;
        BlockPos center = arenaCenterOf(player);
        if (center == null) return 0.5;
        double dx = player.getX() - (center.getX() + 0.5);
        double dz = player.getZ() - (center.getZ() + 0.5);
        double r = ARENA_R + 8;
        return (dx * dx + dz * dz <= r * r) ? 1.5 : 0.5;
    }

    /** 区域ごとの床ブロック (見た目で深層帯が分かる) */
    private static net.minecraft.world.level.block.Block floorBlockFor(String zoneId) {
        return switch (zoneId) {
            case "ossuary" -> Blocks.BONE_BLOCK;
            case "forge" -> Blocks.BLACKSTONE;
            case "glacier" -> Blocks.PACKED_ICE;
            case "voidheart" -> Blocks.OBSIDIAN;
            case "nihil" -> Blocks.BEDROCK;
            default -> Blocks.STONE_BRICKS;
        };
    }

    /** 区域ごとの障害物ブロック */
    private static net.minecraft.world.level.block.Block trimBlockFor(String zoneId) {
        return switch (zoneId) {
            case "ossuary" -> Blocks.QUARTZ_BLOCK;
            case "forge" -> Blocks.MAGMA_BLOCK;
            case "glacier" -> Blocks.BLUE_ICE;
            case "voidheart" -> Blocks.CRYING_OBSIDIAN;
            case "nihil" -> Blocks.OBSIDIAN;
            default -> Blocks.COBBLESTONE;
        };
    }

    /**
     * アリーナ構築: 整地 (埋没防止)・区域床・ランダム障害物・5段塀・バリア天井。
     * 前回のアリーナのバリアは除去する。
     */
    public static void buildArena(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos old = arenaCenterOf(player);
        BlockPos center = player.blockPosition();
        int r = ARENA_R;
        if (old != null && !old.equals(center)) clearBarriers(level, old);
        player.setData(ModAttachments.ARENA.get(),
                center.getX() + "," + center.getY() + "," + center.getZ());
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        String zone = GameData.zoneForFloor(floor).id();
        var floorBlock = floorBlockFor(zone);
        var trim = trimBlockFor(zone);
        var rand = player.getRandom();
        // 床
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                level.setBlock(center.offset(x, -1, z), floorBlock.defaultBlockState(), 3);
            }
        }
        // 整地: 場内の地形・樹木を除去 (埋没防止。基盤は残す)
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                for (int y = 0; y <= 9; y++) {
                    BlockPos p = center.offset(x, y, z);
                    var st = level.getBlockState(p);
                    if (!st.isAir() && !st.is(Blocks.BEDROCK)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // ランダム障害物: 柱・岩塊・棘を 5〜7基 (中央4・湧き輪13〜15を避ける)
        int nFeat = 5 + rand.nextInt(3);
        int placed = 0;
        for (int tries = 0; tries < 40 && placed < nFeat; tries++) {
            double ang = rand.nextDouble() * Math.PI * 2;
            double dist = 5 + rand.nextDouble() * 7;
            if (dist > 12.5 && dist < 15.5) continue;
            int fx = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int fz = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            int kind = rand.nextInt(3);
            if (kind == 0) {
                int h = 2 + rand.nextInt(3);
                for (int y = 0; y < h; y++) {
                    level.setBlock(new BlockPos(fx, center.getY() + y, fz),
                            (y == h - 1 ? trim : Blocks.COBBLESTONE).defaultBlockState(), 3);
                }
            } else if (kind == 1) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        level.setBlock(new BlockPos(fx + dx, center.getY(), fz + dz),
                                Blocks.COBBLESTONE.defaultBlockState(), 3);
                        if (rand.nextBoolean()) {
                            level.setBlock(new BlockPos(fx + dx, center.getY() + 1, fz + dz),
                                    trim.defaultBlockState(), 3);
                        }
                    }
                }
            } else {
                level.setBlock(new BlockPos(fx, center.getY(), fz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                level.setBlock(new BlockPos(fx, center.getY() + 1, fz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            }
            placed++;
        }
        // 塀は5段 (ジャンプ・ノックバック越えを防ぐ。飛行等の抜けは収容処理で戻す)
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= (r - 2) * (r - 2)) continue;
                if (x * x + z * z > r * r) continue;
                for (int y = 0; y < WALL_H; y++) {
                    level.setBlock(center.offset(x, y, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                }
            }
        }
        // バリア: 塀上〜天井を不可視ブロックで封鎖 (リタイア・終了まで維持)
        placeBarriers(level, center);
        player.displayClientMessage(Component.literal("§7試練のアリーナを展開した (周囲を整地)"), false);
    }

    /** 塀上〜天井のバリア配置 */
    public static void placeBarriers(ServerLevel level, BlockPos center) {
        int r = ARENA_R;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d2 = x * x + z * z;
                if (d2 > r * r) continue;
                if (d2 > (r - 2) * (r - 2)) {
                    for (int y = WALL_H; y <= WALL_H + 5; y++) {
                        level.setBlock(center.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), 3);
                    }
                } else if (d2 <= (r - 2) * (r - 2) && Math.abs(x) + Math.abs(z) > 0) {
                    // 天井 (中央1マスは開けて光を入れる)
                    if ((x * x + z * z) % 7 == 0) continue;
                    level.setBlock(center.offset(x, WALL_H + 5, z), Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * 層ごとのアリーナ再構成 (床の張替え・バリア更新・障害物追加)。
     * 難易度と層が上がるほど障害物が増える。既設物は壊さない (空きマスのみ)。
     */
    public static void refreshArena(ServerPlayer player, int floor) {
        BlockPos center = arenaCenterOf(player);
        if (!(player.level() instanceof ServerLevel level) || center == null) return;
        int r = ARENA_R;
        String zone = GameData.zoneForFloor(Math.max(1, floor)).id();
        var floorBlock = floorBlockFor(zone);
        var trim = trimBlockFor(zone);
        var rand = player.getRandom();
        // 床の張替え
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                level.setBlock(center.offset(x, -1, z), floorBlock.defaultBlockState(), 3);
            }
        }
        // バリア更新
        clearBarriers(level, center);
        placeBarriers(level, center);
        // 障害物を追加 (空きマスのみ。層・難易度で増加)
        String diff = player.getData(ModAttachments.DIFF.get());
        int diffBonus = switch (diff) {
            case "hard" -> 1;
            case "hell" -> 2;
            case "inferno" -> 3;
            case "omega" -> 4;
            default -> 0;
        };
        int nFeat = Math.min(6, 2 + floor / 6 + diffBonus);
        int placed = 0;
        for (int tries = 0; tries < 30 && placed < nFeat; tries++) {
            double ang = rand.nextDouble() * Math.PI * 2;
            double dist = 5 + rand.nextDouble() * 7;
            if (dist > 12.5 && dist < 15.5) continue;
            int fx = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int fz = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            if (!level.getBlockState(new BlockPos(fx, center.getY(), fz)).isAir()) continue;
            if (!level.getBlockState(new BlockPos(fx, center.getY() + 1, fz)).isAir()) continue;
            int kind = rand.nextInt(3);
            if (kind == 0) {
                int h = 2 + rand.nextInt(2);
                for (int y = 0; y < h; y++) {
                    level.setBlock(new BlockPos(fx, center.getY() + y, fz),
                            (y == h - 1 ? trim : Blocks.COBBLESTONE).defaultBlockState(), 3);
                }
            } else if (kind == 1) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        BlockPos q = new BlockPos(fx + dx, center.getY(), fz + dz);
                        if (!level.getBlockState(q).isAir()) continue;
                        level.setBlock(q, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                }
            } else {
                level.setBlock(new BlockPos(fx, center.getY(), fz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                level.setBlock(new BlockPos(fx, center.getY() + 1, fz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            }
            placed++;
        }
    }

    /** 前回アリーナのバリアを除去 (再出撃・終了時) */
    public static void clearBarriers(ServerLevel level, BlockPos center) {
        int r = ARENA_R;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d2 = x * x + z * z;
                if (d2 > r * r) continue;
                if (d2 > (r - 2) * (r - 2)) {
                    for (int y = WALL_H; y <= WALL_H + 5; y++) {
                        BlockPos p = center.offset(x, y, z);
                        if (level.getBlockState(p).is(Blocks.BARRIER)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                } else {
                    BlockPos p = center.offset(x, WALL_H + 5, z);
                    if (level.getBlockState(p).is(Blocks.BARRIER)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** 現在のアリーナのバリアを除去 (リタイア・勝利時) */
    public static void clearArenaBarriers(ServerPlayer player) {
        BlockPos center = arenaCenterOf(player);
        if (center != null && player.level() instanceof ServerLevel level) {
            clearBarriers(level, center);
        }
    }

    /** 安全な湧きY: 上から走査し、固体の上の3マス空気を探す (埋没防止)。
     * バリア・壁の上は除外 (バリア天井や塀の上に湧かないように) */
    public static int safeSpawnY(ServerLevel level, int x, int z, int baseY) {
        for (int y = baseY + 12; y >= baseY - 10; y--) {
            BlockPos p = new BlockPos(x, y, z);
            var below = level.getBlockState(p.below());
            if (level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.above().above()).isAir()
                    && !below.isAir() && below.getFluidState().isEmpty()
                    && !below.is(Blocks.BARRIER) && !below.is(Blocks.STONE_BRICK_WALL)) {
                return y;
            }
        }
        return baseY;
    }

    /** 保存されたアリーナ中心 (未出撃なら null) */
    public static BlockPos arenaCenterOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.ARENA.get());
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.split(",");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /* ---------------- 罠・破壊物 ---------------- */

    /** 5層〜罠 (針・岩) / 3層〜破壊物を配置 */
    public static void placeHazards(ServerPlayer player, ServerLevel level, BlockPos center, int floor) {
        var rand = player.getRandom();
        if (floor >= 5) {
            int n = Math.min(6, 2 + floor / 8 + GameData.zoneForFloor(floor).hazards());
            var sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double dist = 6 + rand.nextDouble() * 10;
                int x = center.getX() + (int) (Math.cos(ang) * dist);
                int z = center.getZ() + (int) (Math.sin(ang) * dist);
                int y = center.getY();
                // 原作 traps.ts: spike (針・小範囲) / rock (岩・大範囲)
                String kind = rand.nextFloat() < 0.5f ? "spike" : "rock";
                if (!sb.isEmpty()) sb.append(";");
                sb.append(kind).append(",").append(x).append(",").append(y).append(",").append(z);
                var puff = "spike".equals(kind) ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
                level.sendParticles(puff, x + 0.5, y + 0.2, z + 0.5, 4, 0.3, 0.2, 0.3, 0.01);
            }
            player.setData(ModAttachments.TRAPS.get(), sb.toString());
        }
        if (floor >= 3) {
            String[] kinds = {"red", "blue", "shrine"};
            var sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double dist = 8 + rand.nextDouble() * 6;
                BlockPos p = center.offset((int) (Math.cos(ang) * dist), 0, (int) (Math.sin(ang) * dist));
                String kind = kinds[rand.nextInt(kinds.length)];
                var state = switch (kind) {
                    case "red" -> Blocks.RED_STAINED_GLASS.defaultBlockState();
                    case "blue" -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                    default -> Blocks.ENCHANTING_TABLE.defaultBlockState();
                };
                level.setBlock(p, state, 3);
                if (!sb.isEmpty()) sb.append(";");
                sb.append(kind).append(",").append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ());
            }
            player.setData(ModAttachments.DESTRUCT.get(), sb.toString());
        }
    }

    /** 罠チェック (10tickごと)。原作 traps.ts: spike=針・小範囲速発 / rock=岩・大範囲1秒予兆 */
    public static void tickTraps(ServerPlayer player) {
        String raw = player.getData(ModAttachments.TRAPS.get());
        if (raw == null || raw.isBlank()) return;
        var level = (ServerLevel) player.level();
        var keep = new java.util.ArrayList<String>();
        boolean changed = false;
        for (String t : raw.split(";")) {
            String[] p = t.split(",");
            // 新形式 kind,x,y,z / 旧形式 x,y,z (岩として扱う)
            String kind = "rock";
            int off = 0;
            if (p.length == 4) {
                kind = p[0];
                off = 1;
            } else if (p.length != 3) {
                continue;
            }
            final String fkind = kind;
            try {
                double x = Double.parseDouble(p[off]) + 0.5;
                double y = Double.parseDouble(p[off + 1]);
                double z = Double.parseDouble(p[off + 2]) + 0.5;
                double d = Math.hypot(player.getX() - x, player.getZ() - z);
                if (d < 2.5 && Math.abs(player.getY() - y) < 3) {
                    var at = new Vec3(x, y + 1, z);
                    if ("spike".equals(fkind)) {
                        // 針罠: 短い予兆 (0.5秒) の後に小範囲の直撃 + 鈍足
                        for (int k = 0; k < 2; k++) {
                            DelayRunner.after(5 * k, () -> level.sendParticles(ParticleTypes.CRIT,
                                    x, y + 0.2, z, 8, 0.8, 0.1, 0.8, 0.02));
                        }
                        DelayRunner.after(10, () -> {
                            if (!player.isAlive()) return;
                            player.hurtServer(level, level.damageSources().cactus(), 6f);
                            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
                        });
                        player.sendSystemMessage(Component.literal("§e針罠！"));
                    } else {
                        // 岩罠: 予兆 (約1秒) を経て範囲爆発. 予兆の間は煙を出す
                        for (int k = 0; k < 4; k++) {
                            DelayRunner.after(5 * k, () -> level.sendParticles(ParticleTypes.SMOKE,
                                    x, y + 0.2, z, 10, 1.0, 0.1, 1.0, 0.02));
                        }
                        DelayRunner.after(20, () -> WeaponArts.trapBlast(level, player, at));
                        player.sendSystemMessage(Component.literal("§c岩罠が起動… 1秒後に爆発！"));
                    }
                    changed = true;
                } else {
                    keep.add(t);
                    if (d < 8) {
                        var puff = "spike".equals(fkind) ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
                        level.sendParticles(puff, x, y + 0.2, z, 1, 0.2, 0.1, 0.2, 0.01);
                    }
                }
            } catch (NumberFormatException ignored) {
                changed = true;
            }
        }
        if (changed) player.setData(ModAttachments.TRAPS.get(), String.join(";", keep));
    }
}
