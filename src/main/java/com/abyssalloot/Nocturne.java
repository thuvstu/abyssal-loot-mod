package com.abyssalloot;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 夜想曲ノクターン (律動旋り宙に楽でるノクターン) — エンドコンテンツの杖.
 * 原作: mm/origin/MythicCruicible/nocturne仕様.txt を Abyssal Loot の戦闘式に合わせて移植.
 *
 * <h3>左クリック</h3>
 * 左クリックカウント c (0→4 で一巡) が 1 打ごとに進む。通常攻撃に加え、
 * 防具無視の追撃 (攻撃力 × 0.15 × (c+1)²: 1→4→9→16→25 の比) を与える (CT 1.0秒)。
 * 空振りでも 5m 以内の最寄りの敵を狙う。1 打ごとにメーター+1 (最大100)。
 * メーター100未満で一巡すると最大HPの2%回復。
 *
 * <h3>右クリック (数列詠唱)</h3>
 * カウントに応じた桁数 (2/3/5/7/11、メーター100なら+1) の数列が表示され、数字キーで入力する。
 * 次の桁はスニークで開示される。1 桁正解するたびに対応するスキル1〜12が即発動し、
 * 誤入力・完了・再右クリックで終了してカウントは0に戻る。入力中は鈍足・持ち替え不可・左クリック無効。
 * メーター100で始めた詠唱は全効果2倍で、完了時に最大HPの50%回復しメーターは0になる。
 */
public final class Nocturne {
    private Nocturne() {}

    static final String COUNT = "noct_count";
    static final String METER = "noct_meter";
    /** カウントごとの数列の桁数 */
    private static final int[] LENGTHS = {2, 3, 5, 7, 11};
    /** スキル8 (隕石) のダメージ表 (原作値)。威力は平方根で圧縮して攻撃力に掛ける */
    private static final int[] METEOR = {1, 9, 45, 55, 99, 297, 703, 999};
    private static final int FOLLOW_CD_TICKS = 20;
    private static final int INPUT_TIMEOUT_TICKS = 20 * 40;

    /** 数列入力の状態 (揮発) */
    private static final class Input {
        int[] seq;
        int pos;
        int revealed;
        int count;
        boolean boosted;
        double atk;
        double taken;
        int slot;
        long start;
        boolean wasSneaking;
        /** スロット 777 の倍率 (スキル8〜11へ) */
        int slotMult = 1;
        boolean rolling;
        final List<String> log = new ArrayList<>();
    }

    private static final Map<UUID, Input> INPUT = new HashMap<>();
    private static final Map<UUID, Long> LAST_FOLLOW = new HashMap<>();

    /* ================================================================ 小道具 */

    public static boolean isNocturne(ItemStack stack) {
        return stack.is(ModItems.ABYSS_NOCTURNE.get());
    }

    public static boolean inInput(ServerPlayer player) {
        return INPUT.containsKey(player.getUUID());
    }

    static int countOf(ItemStack stack) {
        return Math.floorMod(Endgame.intOf(stack, COUNT, 0), 5);
    }

    static int meterOf(ItemStack stack) {
        return Math.max(0, Math.min(100, Endgame.intOf(stack, METER, 0)));
    }

    private static void store(ItemStack stack, int count, int meter) {
        Endgame.putInts(stack, Map.of(COUNT, count, METER, meter), count);
        GearData.refreshLore(stack);
    }

    /** 固有の銘 (原作の色分けそのまま) */
    static void applyName(ItemStack stack) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§b律動旋り§d宙に§3楽でる§cノクターン"));
    }

    /** 原作の説明文 + 操作の要点 (GearData.loreLines から呼ぶ) */
    static List<Component> lore(ItemStack stack) {
        int c = countOf(stack);
        int m = meterOf(stack);
        return List.of(
                Component.literal("§7数の律動が織り成す§f秩序§7とともに、"),
                Component.literal("§eエントロピー§7は静かに§d発散§7し、"),
                Component.literal("§7万象は§6予測不可能§7な波形を描きながら、§9其の刻§7を迎える。"),
                Component.literal("§7時はやがて§b収束§7し、§a螺旋§7を描く光が§3音§7となって世界を満たす。"),
                Component.literal("§7そしてすべては、§e§l天命§7に導かれ、§5終曲§7へと§c復§7る。"),
                Component.literal(String.format("§bカウント %d/4 §7| §dメーター %s", c,
                        m >= 100 ? "§6MAX!" : m + "%")),
                Component.literal("§e左: 律動の追撃 (防具無視) §7/ §e右: 数列詠唱 (数字キー・スニークで開示)"),
                Component.literal("§8詳細は /nocturnehelp"));
    }

    private static ServerLevel slevel(ServerPlayer p) {
        return (ServerLevel) p.level();
    }

    private static void later(ServerPlayer player, int ticks, Runnable r) {
        DelayRunner.after(ticks, () -> {
            if (player.isAlive() && !player.hasDisconnected()) r.run();
        });
    }

    private static List<LivingEntity> enemies(ServerPlayer player, Vec3 at, double rx, double ry, double rz) {
        return slevel(player).getEntitiesOfClass(LivingEntity.class,
                new AABB(at.x - rx, at.y - ry, at.z - rz, at.x + rx, at.y + ry, at.z + rz),
                CombatUtil::isValidTarget);
    }

    private static LivingEntity nearest(ServerPlayer player, double r) {
        return enemies(player, player.position(), r, r, r).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    private static void spark(ServerPlayer player, LivingEntity e, ParticleOptions p, int n) {
        slevel(player).sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                n, 0.3, 0.4, 0.3, 0.05);
    }

    private static void ring(ServerPlayer player, Vec3 at, double r, ParticleOptions p) {
        ServerLevel level = slevel(player);
        int n = (int) Math.max(16, r * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(p, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
        }
    }

    private static void note(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(),
                SoundSource.PLAYERS, 0.9f, pitch);
    }

    /* ================================================================ 左クリック */

    /** 近接命中 (WeaponHitHandler の通常命中から) */
    public static void onMeleeHit(ServerPlayer player, LivingEntity target, double atk) {
        swing(player, target, atk, false);
    }

    /** 空振り (クライアントの LeftClickEmpty → ActPacket "noct_swing") */
    public static void onAirSwing(ServerPlayer player) {
        if (!isNocturne(player.getMainHandItem())) return;
        LivingEntity t = nearest(player, 5);
        swing(player, t, StatCalc.attackDamageOf(player), true);
    }

    private static void swing(ServerPlayer player, LivingEntity target, double atk, boolean air) {
        if (inInput(player)) return;
        long t = player.level().getGameTime();
        Long last = LAST_FOLLOW.get(player.getUUID());
        if (last != null && t - last < FOLLOW_CD_TICKS) return;
        LAST_FOLLOW.put(player.getUUID(), t);
        ItemStack stack = player.getMainHandItem();
        int c = countOf(stack);
        if (target != null) {
            // 空振り時は通常攻撃 (防具・耐性あり) も杖から放つ: 基礎 1-2-3-4-5 の比
            if (air) {
                target.invulnerableTime = 0;
                WeaponArts.artHurt(slevel(player), target, slevel(player).damageSources().playerAttack(player),
                        (float) (atk * (0.6 + 0.2 * c)));
            }
            Endgame.pierceHit(player, target, atk * 0.15 * (c + 1) * (c + 1));
            spark(player, target, new DustParticleOptions(colorOf(c), 1.1f), 6 + c * 3);
        }
        int next = (c + 1) % 5;
        int meter = Math.min(100, meterOf(stack) + 1);
        note(player, 0.7f + 0.15f * next);
        if (next == 0 && meter < 100) {
            RunManager.heal(player, player.getMaxHealth() * 0.02);
            player.sendSystemMessage(Component.literal("§b[ノクターン] §7律動が一巡し、§a旋律が傷を癒す §7(+2%)"));
        }
        if (meter == 100 && meterOf(stack) < 100) {
            player.sendSystemMessage(Component.literal("§b[ノクターン] §6メーターが満ちた。§7次の詠唱は桁が増え、全効果が2倍になる。"));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1f, 1.4f);
        }
        store(stack, next, meter);
        showStatus(player);
    }

    /** カウントごとの主色 (tools/art/endgame.py の NOCTURNE_THEMES と同じ: 紫→蒼→水→桃→白金) */
    static int colorOf(int c) {
        return switch (c) {
            case 1 -> 0x6f8cff;
            case 2 -> 0x5ce8ff;
            case 3 -> 0xff6ad5;
            case 4 -> 0xffe7a0;
            default -> 0xb48cff;
        };
    }

    /** 攻撃のキャンセル: 数列入力中は左クリックを受け付けない */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inInput(p)) event.setCanceled(true);
    }

    /* ================================================================ 右クリック */

    /** 右クリック: 入力中なら取消、そうでなければ数列詠唱を開始 */
    public static void onRightClick(ServerPlayer player) {
        if (!isNocturne(player.getMainHandItem())) return;
        Input in = INPUT.get(player.getUUID());
        if (in != null) {
            // 右クリック長押しの連続発火で開始直後に取り消さないよう 0.5 秒は無視する
            if (player.level().getGameTime() - in.start < 10) return;
            finish(player, in, false, "§7詠唱を取り消した");
            return;
        }
        float cd = player.getData(ModAttachments.LIGHT_CD.get());
        if (cd > 0) {
            player.displayClientMessage(Component.literal(String.format("§7数列詠唱 CT %.1fs", cd)), true);
            return;
        }
        ItemStack stack = player.getMainHandItem();
        in = new Input();
        in.count = countOf(stack);
        in.boosted = meterOf(stack) >= 100;
        int len = LENGTHS[in.count] + (in.boosted ? 1 : 0);
        in.seq = new int[len];
        for (int i = 0; i < len; i++) in.seq[i] = player.getRandom().nextInt(10);
        in.revealed = 1;
        in.atk = StatCalc.attackDamageOf(player);
        in.slot = player.getInventory().getSelectedSlot();
        in.start = player.level().getGameTime();
        in.wasSneaking = player.isShiftKeyDown();
        INPUT.put(player.getUUID(), in);
        ModPackets.send(player, new ModPackets.NocturneSync(true));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1f, 0.8f);
        in.log.add("§d旋律の譜面が開かれた §7(カウント" + in.count + "・" + len + "桁"
                + (in.boosted ? "・§6メーター解放 ×2§7" : "") + ")");
        showInput(player, in);
    }

    /** 数字キー (ActPacket "noct_d0".."noct_d9") */
    public static void onDigit(ServerPlayer player, int digit) {
        Input in = INPUT.get(player.getUUID());
        if (in == null || in.pos >= in.seq.length) return;
        if (digit != in.seq[in.pos]) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 1f, 0.5f);
            finish(player, in, false, "§c旋律が乱れた §7(" + (in.pos + 1) + "桁目: §c" + digit
                    + "§7 ≠ §e" + in.seq[in.pos] + "§7)");
            return;
        }
        in.pos++;
        note(player, 0.6f + 0.12f * Math.min(12, in.pos));
        castSkill(player, in, in.pos);
        if (in.pos >= in.seq.length) {
            finish(player, in, true, "§a旋律は完成した");
        } else {
            showInput(player, in);
        }
    }

    /** 被弾の記録 (スキル4用。Endgame の被弾処理から) */
    static void recordTaken(ServerPlayer player, float dmg) {
        Input in = INPUT.get(player.getUUID());
        if (in != null) in.taken += dmg;
    }

    /** 入力の毎tick処理: 鈍足・スニーク開示・持ち替え/時間切れの取消・表示更新 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Input in = INPUT.get(player.getUUID());
        long t = player.level().getGameTime();
        if (in == null) {
            if (t % 20 == 0 && isNocturne(player.getMainHandItem())) showStatus(player);
            return;
        }
        if (!player.isAlive() || !isNocturne(player.getMainHandItem())
                || player.getInventory().getSelectedSlot() != in.slot) {
            finish(player, in, false, "§7ノクターンを手放したため詠唱が途切れた");
            return;
        }
        if (t - in.start > INPUT_TIMEOUT_TICKS) {
            finish(player, in, false, "§7時間切れ — 旋律は夜に溶けた");
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 2, false, false, true));
        boolean sneak = player.isShiftKeyDown();
        if (sneak && !in.wasSneaking && in.revealed <= in.pos && in.revealed < in.seq.length) {
            in.revealed = in.pos + 1;
            note(player, 1.6f);
            showInput(player, in);
        }
        in.wasSneaking = sneak;
        if (t % 10 == 0) showInput(player, in);
    }

    private static void finish(ServerPlayer player, Input in, boolean success, String why) {
        INPUT.remove(player.getUUID());
        ModPackets.send(player, new ModPackets.NocturneSync(false));
        player.removeEffect(MobEffects.SLOWNESS);
        ItemStack stack = player.getMainHandItem();
        if (!isNocturne(stack)) stack = findNocturne(player);
        int meter = stack.isEmpty() ? 0 : meterOf(stack);
        if (in.boosted) {
            meter = 0;
            if (success) {
                RunManager.heal(player, player.getMaxHealth() * 0.5);
                in.log.add("§a満ちた律動が還り、HPが大きく回復した §7(最大HPの50%)");
            }
        }
        if (!stack.isEmpty()) store(stack, 0, meter);
        int cdSec = (15 + 3 * in.count) * (in.boosted ? 2 : 1);
        if (!success && in.pos == 0) cdSec = 3; // 1 桁も奏でていなければ軽いCTだけ
        player.setData(ModAttachments.LIGHT_CD.get(), (float) cdSec);
        in.log.add(why + " §8(" + in.pos + "/" + in.seq.length + "桁・CT " + cdSec + "s)");
        player.sendSystemMessage(Component.literal("§b[ノクターン] §e==== 奏でられし旋律の記録 ===="));
        for (String line : in.log) player.sendSystemMessage(Component.literal(" " + line));
        player.displayClientMessage(Component.literal(why), true);
    }

    private static ItemStack findNocturne(ServerPlayer player) {
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (isNocturne(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** テスト用: 入力中の数列 (入力していなければ null) */
    static int[] debugSequence(ServerPlayer player) {
        Input in = INPUT.get(player.getUUID());
        return in == null ? null : in.seq.clone();
    }

    static void onLogout(UUID id) {
        INPUT.remove(id);
        LAST_FOLLOW.remove(id);
    }

    /* ================================================================ 表示 */

    private static void showInput(ServerPlayer player, Input in) {
        var sb = new StringBuilder("§d[入力] ");
        for (int i = 0; i < in.seq.length; i++) {
            if (i < in.pos) sb.append("§7").append(in.seq[i]);
            else if (i < in.revealed) sb.append("§c§l").append(in.seq[i]).append("§r");
            else sb.append("§8_");
            sb.append(' ');
        }
        if (in.revealed <= in.pos && in.pos < in.seq.length) sb.append("§8(スニークで次を開示)");
        if (in.rolling && in.pos >= in.seq.length) sb = new StringBuilder("§d[スロット] 運命の回転中...");
        player.displayClientMessage(Component.literal(sb.toString()), true);
    }

    /** 通常時のステータス: カウント・基礎・追撃・メーター (+シールド) */
    static void showStatus(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!isNocturne(stack)) return;
        int c = countOf(stack);
        int m = meterOf(stack);
        double atk = StatCalc.attackDamageOf(player);
        var sb = new StringBuilder(String.format("§bカウント: %d §7| §f基礎: %.0f §7| §d追撃: %.0f §7| §eメーター: %s",
                c, atk * (0.6 + 0.2 * c), atk * 0.15 * (c + 1) * (c + 1), m >= 100 ? "§6MAX!" : m + "%"));
        double shield = Endgame.shieldOf(player);
        if (shield > 0) sb.append(String.format(" §7| §3HPシールド: %.0f", shield));
        player.displayClientMessage(Component.literal(sb.toString()), true);
    }

    /* ================================================================ スキル1〜12 */

    private static void castSkill(ServerPlayer player, Input in, int no) {
        int c = in.count;
        int m = in.boosted ? 2 : 1;
        double atk = in.atk;
        switch (no) {
            case 1 -> skill1(player, in, c, m, atk);
            case 2 -> skill2(player, in, c, m, atk);
            case 3 -> skill3(player, in, c, m);
            case 4 -> skill4(player, in, c, m);
            case 5 -> skill5(player, in, c, m, atk);
            case 6 -> skill6(player, in, c, m);
            case 7 -> skill7(player, in, m, atk);
            case 8 -> skill8(player, in, c, m, atk);
            case 9 -> skill9(player, in, c, m, atk);
            case 10 -> skill10(player, in, c, m, atk);
            case 11 -> skill11(player, in, m, atk);
            case 12 -> skill12(player, in, m);
            default -> { }
        }
    }

    /** スキル1 律動の針: 周囲で最もHPの低い敵のHPを直接削る */
    private static void skill1(ServerPlayer player, Input in, int c, int m, double atk) {
        LivingEntity t = enemies(player, player.position(), 32, 16, 32).stream()
                .min(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
        if (t == null) {
            in.log.add("§7① 律動の針 — 標的なし");
            return;
        }
        double v = atk * 0.5 * (1 + c) * m;
        Endgame.drainHealth(player, t, v);
        in.log.add(String.format("§f① 律動の針 §7— 最も弱き者のHPを §c%.0f §7削った", v));
    }

    /** スキル2 和音の波紋: 周囲の敵へ範囲攻撃 */
    private static void skill2(ServerPlayer player, Input in, int c, int m, double atk) {
        double r = Math.min(16, 4 + 3 * c);
        double v = atk * 0.6 * (1 + c) * m;
        var list = enemies(player, player.position(), r, 6, r);
        for (LivingEntity e : list) {
            if (e.distanceTo(player) > r + 1) continue;
            Endgame.splitHit(player, e, v);
            spark(player, e, ParticleTypes.NOTE, 2);
        }
        ring(player, player.position(), r, ParticleTypes.NOTE);
        in.log.add(String.format("§f② 和音の波紋 §7— 半径%.0fの %d体へ §c%.0f", r, list.size(), v));
    }

    /** スキル3 静寂の休符: 28m 内の敵を鈍足に */
    private static void skill3(ServerPlayer player, Input in, int c, int m) {
        int amp = Math.min(9, (1 + 2 * c) * m - 1);
        int sec = (int) Math.min(120, (1 + Math.pow(3, c)) * m);
        var list = enemies(player, player.position(), 28, 12, 28);
        for (LivingEntity e : list) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, sec * 20, amp));
            spark(player, e, ParticleTypes.SNOWFLAKE, 3);
        }
        in.log.add("§f③ 静寂の休符 §7— " + list.size() + "体に鈍足 Lv" + (amp + 1) + " (" + sec + "秒)");
    }

    /** スキル4 反響の盾: 詠唱中の被ダメを回復し、オート反撃を纏う */
    private static void skill4(ServerPlayer player, Input in, int c, int m) {
        double heal = in.taken * m;
        RunManager.heal(player, heal);
        Endgame.grantCounter(player, c * m, 15 * 20 * Math.max(1, c));
        slevel(player).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(),
                4, 0.4, 0.2, 0.4, 0);
        in.log.add(String.format("§f④ 反響の盾 §7— 被ダメ §a%.0f §7を回復%s", heal,
                c > 0 ? "・オート反撃 Lv" + c * m + " (" + 15 * c + "秒)" : ""));
    }

    /** スキル5 魔法攻撃 (星屑の連撃 / 虚無の波動 / 天雷の調和 から1つ) */
    private static void skill5(ServerPlayer player, Input in, int c, int m, double atk) {
        int hits = 1 + c;
        double per = atk * (0.4 + 0.4 * c) * m;
        int pattern = player.getRandom().nextInt(3);
        LivingEntity t = nearest(player, 16);
        switch (pattern) {
            case 0 -> { // 星屑の連撃
                if (t != null) {
                    for (int i = 0; i < hits; i++) {
                        later(player, i * 3, () -> {
                            if (!t.isAlive()) return;
                            Endgame.splitHit(player, t, per);
                            spark(player, t, ParticleTypes.CRIT, 12);
                        });
                    }
                }
                in.log.add(String.format("§f⑤ 星屑の連撃 §7— %d連 × §c%.0f", hits, per));
            }
            case 1 -> { // 虚無の波動: 総量を均等分配
                var list = enemies(player, player.position(), 8, 5, 8);
                double total = per * hits;
                for (LivingEntity e : list) Endgame.splitHit(player, e, total / Math.max(1, list.size()));
                ring(player, player.position(), 3, ParticleTypes.PORTAL);
                ring(player, player.position(), 6, ParticleTypes.PORTAL);
                in.log.add(String.format("§f⑤ 虚無の波動 §7— 総量 §c%.0f §7を%d体に分配", total, list.size()));
            }
            default -> { // 天雷の調和: 単発の極大
                if (t != null) {
                    Endgame.splitHit(player, t, per * hits);
                    var bolt = EntityType.LIGHTNING_BOLT.create(slevel(player), EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.setVisualOnly(true);
                        bolt.snapTo(t.getX(), t.getY(), t.getZ());
                        slevel(player).addFreshEntity(bolt);
                    }
                    spark(player, t, ParticleTypes.FIREWORK, 30);
                }
                in.log.add(String.format("§f⑤ 天雷の調和 §7— 単発 §c%.0f", per * hits));
            }
        }
    }

    /** スキル6 夜の帳: 周囲を弱体し、自分に耐性とHPシールド */
    private static void skill6(ServerPlayer player, Input in, int c, int m) {
        int lv = Math.max(1, c) * m;
        var list = enemies(player, player.position(), 27, 27, 27);
        for (LivingEntity e : list) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200 * m, lv - 1));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 200 * m, lv - 1));
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 200 * m, lv - 1));
            spark(player, e, ParticleTypes.SQUID_INK, 4);
        }
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400 * m, 3));
        double shield = player.getMaxHealth() * m;
        Endgame.addShield(player, shield, 20 * 60 * 10);
        in.log.add(String.format("§f⑥ 夜の帳 §7— %d体を蝕み、§3HPシールド+%.0f §7(10分)・耐性IV", list.size(), shield));
    }

    /** スキル7 運命のスロット: 3つの数字が左から確定し、出目で効果が変わる */
    private static void skill7(ServerPlayer player, Input in, int m, double atk) {
        int[] d = {player.getRandom().nextInt(10), player.getRandom().nextInt(10), player.getRandom().nextInt(10)};
        // 777 は少しだけ出やすく (ジャックポット演出)
        if (player.getRandom().nextFloat() < 0.04f) d = new int[]{7, 7, 7};
        final int[] dice = d;
        in.rolling = true;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 10));
        for (int lane = 0; lane <= 3; lane++) {
            final int open = lane;
            later(player, lane * 6, () -> {
                var sb = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                    sb.append(i < open ? "§e§l" + dice[i] : "§d§k" + player.getRandom().nextInt(10)).append("§r ");
                }
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(sb.toString().trim())));
                if (open > 0) note(player, 0.8f + open * 0.3f);
                if (open == 3) {
                    in.rolling = false;
                    slotResult(player, in, dice, m, atk);
                }
            });
        }
        in.log.add("§f⑦ 運命のスロット §7— 回転開始");
    }

    private static void slotResult(ServerPlayer player, Input in, int[] d, int m, double atk) {
        String code = "" + d[0] + d[1] + d[2];
        String msg;
        switch (code) {
            case "777" -> {
                in.slotMult = 7;
                player.setHealth(player.getMaxHealth());
                Endgame.addShield(player, player.getMaxHealth() * 7 * m, 20 * 60 * 10);
                player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.PLAYERS, 1f, 1.2f);
                msg = "§6§l777 JACKPOT! §7— 全回復・HPシールド ×7、スキル8〜11が7倍";
            }
            case "111", "999" -> {
                LivingEntity t = nearest(player, 50);
                double keep = code.equals("111") ? 0.711 : 0.799;
                if (t != null) {
                    Endgame.drainHealth(player, t, t.getHealth() * (1 - keep));
                    spark(player, t, ParticleTypes.ENCHANT, 30);
                }
                msg = "§e" + code + " §7— 最寄りの敵のHPを" + (keep * 100) + "%へ";
            }
            case "222", "333", "555", "666", "888" -> {
                int n = d[0];
                var boosted = new ArrayList<MobEffectInstance>();
                for (MobEffectInstance e : player.getActiveEffects()) {
                    if (!e.getEffect().value().isBeneficial()) continue;
                    boosted.add(new MobEffectInstance(e.getEffect(), Math.min(20 * 600, e.getDuration() * n),
                            Math.min(9, (e.getAmplifier() + 1) * n - 1)));
                }
                for (MobEffectInstance e : boosted) player.addEffect(e);
                msg = "§e" + code + " §7— 有益な効果 " + boosted.size() + "種を ×" + n + " 強化";
            }
            case "000", "444" -> {
                later(player, 60, () -> {
                    var res = player.getEffect(MobEffects.RESISTANCE);
                    if (res != null && res.getAmplifier() >= 4) {
                        player.displayClientMessage(Component.literal("§a耐性が不運を退けた"), true);
                        return;
                    }
                    player.hurtServer(slevel(player), Endgame.selfHarm(player), player.getMaxHealth() * 0.9f);
                });
                msg = "§4" + code + " §7— 3秒後、不吉な音色があなたを打つ (耐性V以上で無効)";
            }
            case "123", "456", "789" -> {
                LivingEntity t = nearest(player, 50);
                double v = atk * 3.69 * m;
                if (t != null) {
                    Endgame.splitHit(player, t, v);
                    spark(player, t, ParticleTypes.TOTEM_OF_UNDYING, 20);
                }
                msg = String.format("§e%s §7— 最寄りの敵へ §c%.0f", code, v);
            }
            default -> {
                int targets = Math.max(1, d[2]);
                double v = (d[0] * 10 + d[1]) * atk / 10.0 * m;
                var list = enemies(player, player.position(), 30, 30, 30);
                list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
                int hit = 0;
                for (LivingEntity e : list) {
                    if (hit++ >= targets) break;
                    Endgame.splitHit(player, e, v);
                    spark(player, e, ParticleTypes.NOTE, 4);
                }
                msg = String.format("§e%s §7— 近くの%d体へ §c%.0f", code, Math.min(targets, list.size()), v);
            }
        }
        in.log.add("§f⑦ スロット: " + msg);
        player.displayClientMessage(Component.literal(msg), true);
        if (!INPUT.containsKey(player.getUUID())) {
            // 詠唱が先に終わっていた場合は結果だけを即座に知らせる
            player.sendSystemMessage(Component.literal("§b[ノクターン] §f⑦ スロット: " + msg));
        }
    }

    /** スキル8 星墜: 2個ずつ隕石を落とす */
    private static void skill8(ServerPlayer player, Input in, int c, int m, double atk) {
        int idx = Math.min(METEOR.length - 1, c + (in.boosted ? 3 : 0));
        double each = atk * Math.sqrt(METEOR[idx]) / 3.0 * m * in.slotMult;
        var list = enemies(player, player.position(), 20, 10, 20);
        ServerLevel level = slevel(player);
        for (int volley = 0; volley < 3; volley++) {
            for (int k = 0; k < 2; k++) {
                Vec3 at = !list.isEmpty()
                        ? list.get(player.getRandom().nextInt(list.size())).position()
                        : player.position().add((player.getRandom().nextDouble() - 0.5) * 12, 0,
                        (player.getRandom().nextDouble() - 0.5) * 12);
                int delay = volley * 10;
                // 落下の軌跡 (高度8から)
                for (int f = 0; f < 8; f++) {
                    final int step = f;
                    later(player, delay + f, () -> level.sendParticles(
                            new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GLOWSTONE.defaultBlockState()),
                            at.x, at.y + 8 - step, at.z, 12, 1.0, 0.5, 1.0, 0));
                }
                later(player, delay + 8, () -> {
                    for (LivingEntity e : enemies(player, at, 2.5, 3, 2.5)) Endgame.splitHit(player, e, each);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLOWSTONE.defaultBlockState()),
                            at.x, at.y + 0.5, at.z, 40, 1, 0.5, 1, 0.2);
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                    level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(),
                            SoundSource.PLAYERS, 0.7f, 1.3f);
                });
            }
        }
        in.log.add(String.format("§f⑧ 星墜 §7— 隕石6個 (1個 §c%.0f§7)%s", each, in.slotMult > 1 ? " §6×7!" : ""));
    }

    private static int pick(ServerPlayer player, java.util.function.IntUnaryOperator seqFn, int max) {
        var vals = new ArrayList<Integer>();
        for (int i = 1; ; i++) {
            int v = seqFn.applyAsInt(i);
            if (v > max) break;
            vals.add(v);
        }
        return vals.get(player.getRandom().nextInt(vals.size()));
    }

    /** スキル9 数霊: (三角数+立方数+フィボナッチ数) × カウント を周囲の敵へ分配 */
    private static void skill9(ServerPlayer player, Input in, int c, int m, double atk) {
        int tri = pick(player, i -> i * (i + 1) / 2, 250);
        int cube = pick(player, i -> i * i * i, 250);
        int fib = pick(player, i -> {
            int a = 1, b = 1;
            for (int k = 1; k < i; k++) {
                int t = a + b;
                a = b;
                b = t;
            }
            return a;
        }, 250);
        double total = Math.max(1, c) * (tri + cube + fib) * atk / 40.0 * m * in.slotMult;
        var list = enemies(player, player.position(), 16, 16, 16);
        for (LivingEntity e : list) {
            Endgame.splitHit(player, e, total / list.size());
            spark(player, e, ParticleTypes.ENCHANT, 10);
        }
        in.log.add(String.format("§f⑨ 数霊 §7— (%d+%d+%d)×%d → 総量 §c%.0f §7を%d体に分配",
                tri, cube, fib, Math.max(1, c), total, list.size()));
    }

    /** スキル10 経験の残響: XPレベルに応じた範囲攻撃と、1体分の与ダメをシールド化 */
    private static void skill10(ServerPlayer player, Input in, int c, int m, double atk) {
        int xpl = player.experienceLevel;
        double lvTerm = Math.min(xpl, 500) * 0.1 + Math.max(0, Math.min(xpl, 1000) - 500) * 0.05
                + Math.max(0, Math.min(xpl, 1500) - 1000) * 0.02 + Math.max(0, xpl - 1500) * 0.008;
        double v = (atk * (0.5 + 0.25 * c) + atk * lvTerm * Math.max(1, c) / 20.0) * m * in.slotMult;
        var list = enemies(player, player.position(), 10, 6, 10);
        for (LivingEntity e : list) Endgame.splitHit(player, e, v);
        ring(player, player.position(), 10, ParticleTypes.GLOW);
        if (!list.isEmpty()) Endgame.addShield(player, v, 20 * 60 * 5);
        in.log.add(String.format("§f⑩ 経験の残響 §7— XPLv%d・%d体へ §c%.0f%s", xpl, list.size(), v,
                list.isEmpty() ? "" : String.format(" §3(シールド+%.0f)", v)));
    }

    /** スキル11 凍れる小節: 最寄りの敵を11秒止め、11m遠ざけて打つ */
    private static void skill11(ServerPlayer player, Input in, int m, double atk) {
        LivingEntity t = nearest(player, 24);
        if (t == null) {
            in.log.add("§7⑪ 凍れる小節 — 標的なし");
            return;
        }
        Vec3 away = t.position().subtract(player.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = player.getLookAngle().multiply(1, 0, 1);
        Vec3 dest = player.position().add(away.normalize().scale(11));
        t.teleportTo(dest.x, t.getY(), dest.z);
        if (t instanceof Mob mob && !mob.isNoAi()) {
            mob.setNoAi(true);
            DelayRunner.after(220, () -> {
                if (mob.isAlive()) mob.setNoAi(false);
            });
        }
        double v = atk * 6 * m * in.slotMult;
        Endgame.splitHit(player, t, v);
        spark(player, t, ParticleTypes.SNOWFLAKE, 30);
        in.log.add(String.format("§f⑪ 凍れる小節 §7— 11秒停止・11m転送・§c%.0f", v));
    }

    /** スキル12 終曲: 全回復・3倍シールド・3分間の不死 */
    private static void skill12(ServerPlayer player, Input in, int m) {
        player.setHealth(player.getMaxHealth());
        Endgame.addShield(player, player.getMaxHealth() * 3 * m, 20 * 60 * 10);
        Endgame.grantUndying(player, 20 * 180);
        ServerLevel level = slevel(player);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                120, 1.2, 1.4, 1.2, 0.5);
        level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 2, player.getZ(),
                80, 2, 2, 2, 0.2);
        ring(player, player.position(), 5, ParticleTypes.END_ROD);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
                SoundSource.PLAYERS, 1f, 0.6f);
        in.log.add("§6§l⑫ 終曲 §7— 全回復・§3HPシールド ×" + 3 * m + "§7・§63分間の不死");
    }

    /* ================================================================ 奥義 (R) */

    /** 終曲・天命: 今のカウントで和音・魔法・星墜を一斉に奏で、最大HP分のシールドを纏う */
    public static void art(ServerPlayer player, double atk) {
        ItemStack stack = player.getMainHandItem();
        Input in = new Input();
        in.count = Math.max(2, countOf(stack));
        in.boosted = meterOf(stack) >= 100;
        in.atk = atk;
        int m = in.boosted ? 2 : 1;
        skill2(player, in, in.count, m, atk);
        later(player, 6, () -> skill5(player, in, in.count, m, atk));
        later(player, 12, () -> skill8(player, in, in.count, m, atk));
        Endgame.addShield(player, player.getMaxHealth(), 20 * 60 * 5);
        in.log.add("§3HPシールド+" + Math.round(player.getMaxHealth()));
        later(player, 14, () -> {
            player.sendSystemMessage(Component.literal("§b[ノクターン] §d==== 終曲・天命 ===="));
            for (String line : in.log) player.sendSystemMessage(Component.literal(" " + line));
        });
    }

    /* ================================================================ ヘルプ */

    public static final List<String> HELP = List.of(
            "§b§l==== 律動旋り宙に楽でるノクターン 取扱説明書 ====",
            "§e■ 左クリック (律動)",
            " §7打つたびにカウントが 0→1→2→3→4→0 と進む (CT 1.0秒)。",
            " §7通常攻撃に加え §d防具無視の追撃 §7(攻撃力×0.15×(カウント+1)²) を与える。",
            " §7空振りでも5m以内の最寄りの敵を狙う。1打ごとにメーター+1 (最大100)。",
            " §7メーター100未満で一巡すると §a最大HPの2%回復§7。",
            "§e■ 右クリック (数列詠唱)",
            " §7カウントに応じて 2/3/5/7/11 桁の数列が現れる (メーター100なら+1桁)。",
            " §7数字キー (0〜9) で入力。§c次の桁はスニークで開示§7される。",
            " §71桁正解するたびにスキルが即発動。誤入力・完了・再右クリックで終了し、カウントは0に戻る。",
            " §7入力中は鈍足・持ち替え/ドロップ/インベントリ不可・左クリック無効。",
            " §7CT: 15秒 + カウント×3秒 (メーター解放時は2倍)。",
            " §6メーター100で詠唱すると全効果2倍、完了時に最大HPの50%回復しメーターは0になる。",
            "§e■ スキル (n桁目で発動)",
            " §f①§7 最弱の敵のHPを直接削る  §f②§7 周囲へ範囲攻撃  §f③§7 28m内を鈍足",
            " §f④§7 詠唱中の被ダメを回復＋オート反撃  §f⑤§7 魔法攻撃 (3種からランダム)",
            " §f⑥§7 周囲を弱体・自分に耐性IVとHPシールド  §f⑦§7 運命のスロット (777で大当たり)",
            " §f⑧§7 隕石を6個落とす  §f⑨§7 数霊 (三角数+立方数+フィボナッチ) を分配",
            " §f⑩§7 XPレベル比例の範囲攻撃＋シールド  §f⑪§7 最寄りの敵を11秒停止して打つ",
            " §f⑫§7 終曲: 全回復・3倍シールド・3分間の不死 (メーター解放時のみ到達)",
            "§e■ 奥義 (Rキー・魂ゲージ100)",
            " §7終曲・天命: ②⑤⑧を一斉に奏で、最大HP分のシールドを纏う。",
            "§e■ HPシールド",
            " §7HPの上に積まれ、被ダメを先に吸収する。付与は加算。時間切れ・耐久0・ログアウトで消える。",
            "§8入手: 最上位クラフト (終焉の核・零の結晶・超越の羽・魂の欠片の核・混沌の宝石×2・深底の真珠×2・帰らずの灯)");
}
