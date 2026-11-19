package com.abyssalloot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * アリーナの安全策。
 * <ul>
 *   <li>設置前の地形 (ブロック + 中身) を保存し、リタイア・敗北・勝利で元に戻す。サーバーを落としても
 *       world/data/abyssalloot/arena_&lt;uuid&gt;.dat から次のログイン時に戻す</li>
 *   <li>整地・設置・復元は {@link #FLAGS} (隣接更新・ドロップ・ブロックエンティティの副作用なし) で書くので、
 *       松明・花・チェストの中身などがアイテムとして散らばらない</li>
 *   <li>出撃中のアリーナはプレイヤーが壊せない (罠など破壊対象は除く)。爆発・MOB の地形破壊も無効</li>
 *   <li>敗北したらアリーナを畳み、出撃前の位置へ戻して数秒の保護を付ける (追い出し)</li>
 * </ul>
 */
public final class ArenaGuard {
    private ArenaGuard() {}

    /** クライアントへの反映のみ。隣接更新・ドロップ・ブロックエンティティの副作用・onPlace を省く */
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    /** 保存する高さ (アリーナ中心からの相対: 床の下 1 段〜天井の上 1 段) */
    static final int Y_MIN = -1, Y_MAX = ArenaManager.WALL_H + 6;

    record Snapshot(ResourceKey<Level> dim, BlockPos center, Map<BlockPos, BlockState> states,
                    Map<BlockPos, CompoundTag> blockEntities) {}

    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    static boolean inCylinder(BlockPos center, BlockPos p, int pad) {
        int dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ(), dy = p.getY() - center.getY();
        int r = ArenaManager.ARENA_R + pad;
        return dx * dx + dz * dz <= r * r && dy >= Y_MIN - pad && dy <= Y_MAX + pad;
    }

    /** アリーナを建てる直前に呼ぶ: 範囲の地形を保存する (前の保存が残っていれば先にそこを戻す) */
    public static void capture(ServerPlayer player, ServerLevel level, BlockPos center) {
        restore(player, false);
        var states = new HashMap<BlockPos, BlockState>();
        var bes = new HashMap<BlockPos, CompoundTag>();
        int r = ArenaManager.ARENA_R;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                for (int y = Y_MIN; y <= Y_MAX; y++) {
                    BlockPos p = center.offset(x, y, z);
                    states.put(p, level.getBlockState(p));
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) bes.put(p, be.saveWithFullMetadata(level.registryAccess()));
                }
            }
        }
        var snap = new Snapshot(level.dimension(), center, states, bes);
        SNAPSHOTS.put(player.getUUID(), snap);
        save(player, snap);
    }

    /** 保存した地形を戻す。teleport=true なら、中にいるプレイヤーを出撃前の位置へ戻して保護する */
    public static void restore(ServerPlayer player, boolean teleport) {
        Snapshot snap = SNAPSHOTS.remove(player.getUUID());
        if (snap == null) snap = load(player);
        if (snap == null) return;
        var server = player.level().getServer();
        ServerLevel level = server == null ? null : server.getLevel(snap.dim());
        if (level == null) return;
        // 先にブロックエンティティを外す (中身を落とさない)
        for (BlockPos p : snap.states().keySet()) {
            if (level.getBlockEntity(p) != null) level.removeBlockEntity(p);
        }
        for (var e : snap.states().entrySet()) {
            if (level.getBlockState(e.getKey()) != e.getValue()) level.setBlock(e.getKey(), e.getValue(), FLAGS);
        }
        for (var e : snap.blockEntities().entrySet()) {
            BlockEntity be = level.getBlockEntity(e.getKey());
            if (be != null) {
                be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), e.getValue()));
                be.setChanged();
            }
        }
        deleteFile(player);
        if (teleport && player.level() == level && inCylinder(snap.center(), player.blockPosition(), 2)) {
            BlockPos c = snap.center();
            player.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            player.fallDistance = 0;
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false));
        }
    }

    /** pos が誰かの出撃中のアリーナの中か */
    public static boolean inActiveArena(Level level, BlockPos pos) {
        for (Snapshot s : SNAPSHOTS.values()) {
            if (s.dim() == level.dimension() && inCylinder(s.center(), pos, 0)) return true;
        }
        return false;
    }

    /* ---------------- 永続化 (サーバー停止をまたいで戻せるように) ---------------- */

    private static Path file(ServerPlayer player) {
        var server = player.level().getServer();
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("abyssalloot")
                .resolve("arena_" + player.getUUID() + ".dat");
    }

    private static void save(ServerPlayer player, Snapshot s) {
        try {
            var root = new CompoundTag();
            root.putString("dim", s.dim().identifier().toString());
            root.putIntArray("center", new int[]{s.center().getX(), s.center().getY(), s.center().getZ()});
            var palette = new HashMap<BlockState, Integer>();
            var pal = new ListTag();
            var cells = new int[s.states().size() * 4];
            int i = 0;
            for (var e : s.states().entrySet()) {
                Integer idx = palette.get(e.getValue());
                if (idx == null) {
                    idx = palette.size();
                    palette.put(e.getValue(), idx);
                    pal.add(NbtUtils.writeBlockState(e.getValue()));
                }
                BlockPos p = e.getKey().subtract(s.center());
                cells[i++] = p.getX();
                cells[i++] = p.getY();
                cells[i++] = p.getZ();
                cells[i++] = idx;
            }
            root.put("palette", pal);
            root.putIntArray("cells", cells);
            var bes = new ListTag();
            for (var e : s.blockEntities().entrySet()) {
                var t = e.getValue().copy();
                BlockPos p = e.getKey().subtract(s.center());
                t.putIntArray("abyss_rel", new int[]{p.getX(), p.getY(), p.getZ()});
                bes.add(t);
            }
            root.put("block_entities", bes);
            Path f = file(player);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(root, f);
        } catch (IOException | RuntimeException ex) {
            AbyssalLootMod.LOGGER.warn("アリーナの地形を保存できなかった: {}", ex.toString());
        }
    }

    private static Snapshot load(ServerPlayer player) {
        Path f = file(player);
        if (!Files.exists(f)) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            var server = player.level().getServer();
            var dimId = Identifier.parse(root.getStringOr("dim", "minecraft:overworld"));
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
            ServerLevel level = server.getLevel(dim);
            if (level == null) return null;
            int[] c = root.getIntArray("center").orElse(new int[3]);
            BlockPos center = new BlockPos(c[0], c[1], c[2]);
            var getter = level.holderLookup(Registries.BLOCK);
            var palList = root.getListOrEmpty("palette");
            var palette = new BlockState[palList.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = NbtUtils.readBlockState(getter, palList.getCompoundOrEmpty(i));
            }
            int[] cells = root.getIntArray("cells").orElse(new int[0]);
            var states = new HashMap<BlockPos, BlockState>();
            for (int i = 0; i + 3 < cells.length; i += 4) {
                states.put(center.offset(cells[i], cells[i + 1], cells[i + 2]), palette[cells[i + 3]]);
            }
            var bes = new HashMap<BlockPos, CompoundTag>();
            var beList = root.getListOrEmpty("block_entities");
            for (int i = 0; i < beList.size(); i++) {
                CompoundTag t = beList.getCompoundOrEmpty(i);
                int[] rel = t.getIntArray("abyss_rel").orElse(new int[3]);
                t.remove("abyss_rel");
                bes.put(center.offset(rel[0], rel[1], rel[2]), t);
            }
            return new Snapshot(dim, center, states, bes);
        } catch (IOException | RuntimeException ex) {
            AbyssalLootMod.LOGGER.warn("アリーナの地形を読めなかった: {}", ex.toString());
            return null;
        }
    }

    private static void deleteFile(ServerPlayer player) {
        try {
            Files.deleteIfExists(file(player));
        } catch (IOException ignored) {}
    }

    /* ---------------- イベント ---------------- */

    /** 出撃していないのに地形の保存が残っていれば (前回の異常終了)、ログイン時に戻す */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getData(ModAttachments.RUN_LOCK.get()) != 0) {
            if (!SNAPSHOTS.containsKey(player.getUUID())) {
                Snapshot s = load(player);
                if (s != null) SNAPSHOTS.put(player.getUUID(), s);  // 出撃の続き: 保護を戻す
            }
            return;
        }
        if (Files.exists(file(player))) {
            restore(player, true);
            player.sendSystemMessage(Component.literal("§7前回のアリーナを元の地形に戻した"));
        }
    }

    /** 出撃中のアリーナは壊せない (罠などの破壊対象は CombatHandler が処理するので通す) */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || !inActiveArena(level, event.getPos())) return;
        if (event.getPlayer() instanceof ServerPlayer sp && CombatHandler.isDestructTarget(sp, event.getPos())) return;
        if (event.getPlayer() != null && event.getPlayer().isCreative() && event.getPlayer().isShiftKeyDown()) return;
        event.setCanceled(true);
    }

    /** 爆発はダメージだけ与え、アリーナの地形は壊さない */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(p -> inActiveArena(level, p));
    }

    /** MOB の地形破壊 (エンダーマン・ラヴェジャーなど) もアリーナ内では無効 */
    @SubscribeEvent
    public static void onGriefing(EntityMobGriefingEvent event) {
        var e = event.getEntity();
        if (e != null && inActiveArena(e.level(), e.blockPosition())) event.setCanGrief(false);
    }
}
