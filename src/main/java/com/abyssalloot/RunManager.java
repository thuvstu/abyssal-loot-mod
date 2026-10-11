package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 層進行・ウェーブ・ボス・職業適用.
 * 原作: FINAL_FLOOR=25, 5層ごとボス, TRUE_FLOOR=26.
 * v0.1は同ワールドにアリーナを展開する簡易版 (Dimensionは後続で追加).
 */
public final class RunManager {
    private RunManager() {}

    /** 層主ボス -> ボスバー */
    public static final java.util.Map<net.minecraft.world.entity.monster.Monster, ServerBossEvent> BOSS_BARS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    public static double diffHpMult(ServerPlayer player) {
        return switch (player.getData(ModAttachments.DIFF.get())) {
            case "hard" -> 1.4;
            case "inferno" -> 2.0;
            default -> 1.0;
        };
    }

    public static double diffDmgMult(ServerPlayer player) {
        return switch (player.getData(ModAttachments.DIFF.get())) {
            case "hard" -> 1.25;
            case "inferno" -> 1.6;
            default -> 1.0;
        };
    }

    public static double diffSoulMult(ServerPlayer player) {
        return switch (player.getData(ModAttachments.DIFF.get())) {
            case "hard" -> 1.5;
            case "inferno" -> 2.2;
            default -> 1.0;
        };
    }

    public static boolean isBossrush(ServerPlayer player) {
        return "bossrush".equals(player.getData(ModAttachments.MODE.get()));
    }

    public static boolean isCovenant(ServerPlayer player) {
        return "covenant".equals(player.getData(ModAttachments.MODE.get()));
    }

    public static boolean isDaily(ServerPlayer player) {
        return "daily".equals(player.getData(ModAttachments.MODE.get()));
    }

    /** 日替わりプリセット (epoch日で3種ローテ) */
    public static String dailyPreset() {
        long day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        return new String[]{"blood-moon", "starfall", "treasure-tide"}[(int) (Math.abs(day) % 3)];
    }

    public static double dailyHpMult() {
        return switch (dailyPreset()) {
            case "blood-moon" -> 1.42;
            case "starfall" -> 1.25;
            default -> 1.32;
        };
    }

    public static double dailyDmgMult() {
        return switch (dailyPreset()) {
            case "blood-moon" -> 1.18;
            case "starfall" -> 1.10;
            default -> 1.06;
        };
    }

    public static Set<String> sigilsOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.SIGILS.get());
        if (raw == null || raw.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    public static void setSigils(ServerPlayer player, Set<String> sigils) {
        player.setData(ModAttachments.SIGILS.get(), String.join(",", sigils));
    }

    /* ---------------- 回復・ゲージ・報酬 (呪印の効果を一箇所で適用) ---------------- */

    /**
     * 戦闘回復・回復アイテム・休息・層移動の回復. 飢餓の呪印で回復量×0.58 (原作 healPlayer).
     * 初期HP・蘇生HPは対象外なので、この関数を通さない.
     */
    public static void heal(ServerPlayer player, double amount) {
        if (amount <= 0 || !player.isAlive()) return;
        double rate = GameData.trialRules(sigilsOf(player)).healing();
        player.heal((float) (amount * rate));
    }

    /**
     * 奥義ゲージの獲得 (原作 gainEnergy / weaponArtGauge). 鈍痛の呪印で×0.70.
     * 魂の報酬倍率(呪印・難易度など)は関係しない.
     */
    public static void addGauge(ServerPlayer player, double raw) {
        if (raw <= 0 || !player.isAlive()) return;
        double rate = GameData.trialRules(sigilsOf(player)).energy();
        float cur = player.getData(ModAttachments.SOUL.get());
        float next = (float) Math.min(100.0, cur + raw * rate);
        player.setData(ModAttachments.SOUL.get(), next);
        if (cur < 100f && next >= 100f) {
            player.displayClientMessage(Component.literal("§d◆ 魂ゲージMAX！右クリックで武器奥義！"), true);
        }
    }

    /** 魂の報酬倍率 (呪印 146% 上限 × 難易度 × 契約×3 × 日替わり × 血月). 原作 calculateRunReward 相当 */
    public static double rewardMult(ServerPlayer player) {
        double m = GameData.soulMult(sigilsOf(player)) * diffSoulMult(player);
        if (isCovenant(player)) m *= 3.0;
        if (isDaily(player)) m *= 1.5;
        if (hasMutation(player, "blood")) m *= 1.25;
        return m;
    }

    /** 素材個数: 報酬倍率と幸運(+4%/点)を掛け、端数は確率丸め (1個でも無駄にならない) */
    public static int materialCount(ServerPlayer player, double base) {
        int fortune = attrsOf(player)[4];
        var rand = player.getRandom();
        return GameData.materialAmount(base * rewardMult(player), fortune, rand::nextDouble);
    }

    /* ---------------- 属性・祝福 ---------------- */

    /** 剛力,韻律,敏捷,忍耐,幸運 */
    public static int[] attrsOf(ServerPlayer player) {
        int[] a = new int[5];
        try {
            String[] p = player.getData(ModAttachments.ATTRS.get()).split(",");
            for (int i = 0; i < Math.min(5, p.length); i++) a[i] = Math.max(0, Integer.parseInt(p[i].trim()));
        } catch (Exception ignored) {}
        return a;
    }

    public static void setAttrs(ServerPlayer player, int[] a) {
        player.setData(ModAttachments.ATTRS.get(), a[0] + "," + a[1] + "," + a[2] + "," + a[3] + "," + a[4]);
    }

    public static int attrSpent(ServerPlayer player) {
        int s = 0;
        for (int v : attrsOf(player)) s += v;
        return s;
    }

    public static java.util.Map<String, Integer> boonsOf(ServerPlayer player) {
        var map = new java.util.HashMap<String, Integer>();
        String raw = player.getData(ModAttachments.BOONS.get());
        if (raw == null || raw.isBlank()) return map;
        for (String e : raw.split(",")) {
            String[] kv = e.split(":");
            if (kv.length == 2) {
                try {
                    map.put(kv[0], map.getOrDefault(kv[0], 0) + Math.max(0, Integer.parseInt(kv[1])));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public static void addBoon(ServerPlayer player, String id) {
        var map = boonsOf(player);
        map.put(id, map.getOrDefault(id, 0) + 1);
        var sb = new StringBuilder();
        for (var e : map.entrySet()) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        player.setData(ModAttachments.BOONS.get(), sb.toString());
    }

    public static int boonLv(ServerPlayer player, String id) {
        return boonsOf(player).getOrDefault(id, 0);
    }

    public static void applyClass(ServerPlayer player, GameData.HeroClass cls, int level) {
        player.setData(ModAttachments.HERO_CLASS.get(), cls.id);
        player.setData(ModAttachments.LEVEL.get(), level);
        int[] a = attrsOf(player);
        var boons = boonsOf(player);
        // 原作: 剛力+3%/点, 韻律+2%/点, 敏捷+1.5%/点, 忍耐+4%/点, 幸運=会心相当
        double maxHp = GameData.heroMaxHp(cls, level)
                * (1 + a[3] * 0.04) * (1 + boons.getOrDefault("hp", 0) * 0.10);
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(Math.min(1024, maxHp));
            player.setHealth((float) Math.min(maxHp, attr.getValue()));
        }
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(GameData.heroDamage(cls, level));
        var spd = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) {
            double base = cls.mcMoveSpeed();
            spd.setBaseValue(base * (1 + a[2] * 0.015) * (1 + boons.getOrDefault("speed", 0) * 0.06));
        }
        var atkSpd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpd != null) {
            atkSpd.setBaseValue(cls.attackSpeed
                    * (1 + a[1] * 0.02) * (1 + boons.getOrDefault("as", 0) * 0.10));
        }
        player.displayClientMessage(Component.literal(
                "§6職業: " + cls.name + "「" + cls.title + "」 必殺技: " + cls.skillName()), false);
    }

    public static double attackDamageOf(ServerPlayer player) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        double base = atk != null ? atk.getValue() : 10;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int level = player.getData(ModAttachments.LEVEL.get());
        int[] a = attrsOf(player);
        double mult = (1 + a[0] * 0.03) * (1 + boonLv(player, "dmg") * 0.12)
                * (1 + player.getData(ModAttachments.OATH_DMG.get()) * 0.08)
                * (1 + forgeLv(player.getMainHandItem()) * 0.08);
        return Math.max(1, GameData.heroDamage(cls, level) * 0.35 + base * 0.15) * mult;
    }

    /** 鍛造レベル (武器CustomData "abyss_forge") */
    public static int forgeLv(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (ModItems.styleOf(stack.getItem()) == null) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return Math.max(0, Math.min(5, data.copyTag().getInt("abyss_forge").orElse(0)));
    }

    public static void setForgeLv(net.minecraft.world.item.ItemStack stack, int lv) {
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putInt("abyss_forge", Math.max(0, Math.min(5, lv)));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
    }

    /* ---------------- 武器特性・レアリティ (原作WEAPON_TRAITS/RARITY準拠) ---------------- */

    public record TraitDef(String id, String name) {}

    public static final TraitDef[] TRAITS = {
            new TraitDef("sharp", "鋭刃"), new TraitDef("vampiric", "吸血鬼"),
            new TraitDef("explosive", "爆砕"), new TraitDef("terminator", "断末魔"),
            new TraitDef("chain_l", "連鎖雷"), new TraitDef("infernus", "業火"),
            new TraitDef("venomous", "猛毒"), new TraitDef("rend", "裂傷"),
            new TraitDef("frenzy", "狂戦士"), new TraitDef("fortify", "不壊"),
    };

    public static String traitName(String id) {
        for (var t : TRAITS) {
            if (t.id().equals(id)) return t.name();
        }
        return null;
    }

    public static String traitOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String id = data.copyTag().getString("abyss_trait").orElse("");
        return id.isEmpty() ? null : id;
    }

    public static String rarityOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "normal";
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String r = data.copyTag().getString("abyss_rarity").orElse("");
        return r.isEmpty() ? "normal" : r;
    }

    public static double rarityMult(String rarity) {
        return switch (rarity) {
            case "mythic" -> 1.7;
            case "legendary" -> 1.45;
            case "rare" -> 1.25;
            case "magic" -> 1.1;
            default -> 1.0;
        };
    }

    private static String rarityLabel(String rarity) {
        return switch (rarity) {
            case "mythic" -> "§d神話";
            case "legendary" -> "§6レジェンド";
            case "rare" -> "§eレア";
            case "magic" -> "§b魔法";
            default -> "§7通常";
        };
    }

    /** ランダム武器生成 (レアリティ抽選+特性付与) */
    public static net.minecraft.world.item.ItemStack makeWeapon(
            GameData.WeaponStyle style, net.minecraft.util.RandomSource rand) {
        var stack = new net.minecraft.world.item.ItemStack(ModItems.itemOf(style));
        double roll = rand.nextDouble() * 100;
        String rarity = roll < 2.5 ? "mythic" : roll < 10 ? "legendary" : roll < 25 ? "rare"
                : roll < 54 ? "magic" : "normal";
        String trait = null;
        if (!rarity.equals("normal") && !rarity.equals("magic")) {
            trait = TRAITS[rand.nextInt(TRAITS.length)].id();
        } else if (rand.nextFloat() < (rarity.equals("magic") ? 0.3f : 0.1f)) {
            trait = TRAITS[rand.nextInt(TRAITS.length)].id();
        }
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("abyss_rarity", rarity);
        if (trait != null) tag.putString("abyss_trait", trait);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        String name = rarityLabel(rarity) + "の" + style.weaponName;
        if (trait != null) name += "〔" + traitName(trait) + "〕";
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(name));
        return stack;
    }

    /** 会心率 (原作crit + 幸運0.5%/点 + 心眼+5%/Lv) */
    public static double critOf(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        // 幸運(運+7/点)は素材・ドロップ側で使う. 会心は職業値+心眼の刻印のみ
        return Math.min(0.95, cls.crit + boonLv(player, "crit") * 0.05);
    }

    /** 奥義・必殺の威力倍率 (武の祝福+12%/Lv) */
    public static double artMultOf(ServerPlayer player) {
        return 1 + boonLv(player, "art") * 0.12;
    }

    /** 出撃: 現在地の近くに試練アリーナを整地し第1ウェーブを出す */
    public static void startRun(ServerPlayer player) {
        int floor = 1;
        String mode = player.getData(ModAttachments.MODE.get());
        player.setData(ModAttachments.FLOOR.get(), floor);
        player.setData(ModAttachments.KILLS.get(), 0);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.SOUL.get(), 0f);
        player.setData(ModAttachments.COMBO.get(), 0);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.BOSSES.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        player.setData(ModAttachments.SHOP.get(), "");
        player.setData(ModAttachments.TRAPS.get(), "");
        player.setData(ModAttachments.DESTRUCT.get(), "");
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int level = "bossrush".equals(mode) ? 10 : player.getData(ModAttachments.LEVEL.get());
        // 継承装備があれば受け取る
        var heirloom = player.getData(ModAttachments.HEIRLOOM.get());
        if (heirloom != null && !heirloom.isEmpty()) {
            player.getInventory().add(heirloom.copy());
            player.sendSystemMessage(Component.literal("§6血脈の装備を受け継いだ: " + heirloom.getHoverName().getString()));
            player.setData(ModAttachments.HEIRLOOM.get(), net.minecraft.world.item.ItemStack.EMPTY.copy());
        }
        applyClass(player, cls, level);
        buildArena(player);
        spawnWave(player, floor);
        double soul = rewardMult(player);
        player.displayClientMessage(Component.literal(
                "§5深淵潜行開始 — 第1層 [" + mode + "/" + player.getData(ModAttachments.DIFF.get())
                        + "] 魂倍率×" + String.format("%.2f", soul)), false);
    }

    /** 踏破勝利: 報酬 + 継承の案内 */
    public static void victory(ServerPlayer player) {
        if (isDaily(player)) {
            player.sendSystemMessage(Component.literal("§6★ 日替わり深淵【" + dailyPreset() + "】踏破！ ★"));
        } else {
            player.sendSystemMessage(Component.literal("§6★ 深淵踏破！虚無神を打ち果たした！ ★"));
        }
        player.sendSystemMessage(Component.literal("§e報酬: 魂の欠片×16 / Abyss武器 / 手持ち武器1点を /abyss keep で継承可能"));
        player.sendSystemMessage(Component.literal("§5真の深淵が開いた… /abyss true で《ニヒル・ゼロ》に挑め"));
        int shards = materialCount(player, 16);
        player.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.SOUL_SHARD.get(), shards));
        player.sendSystemMessage(Component.literal("§e魂の欠片 ×" + shards + " (呪印・難易度・幸運の倍率込み)"));
        var styles = GameData.WeaponStyle.values();
        player.getInventory().add(makeWeapon(styles[player.getRandom().nextInt(styles.length)],
                player.getRandom()));
        CombatHandler.grantAch(player, "full-clear", "深淵を終わらせた者", 16);
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
    }

    public static boolean isTrueFloor(ServerPlayer player) {
        return player.getData(ModAttachments.FLOOR.get()) == 26;
    }

    /** 層の異変を抽選 (原作FLOOR_MUTATIONS準拠の簡易版) */
    public static String rollMutation(ServerPlayer player, int floor) {
        if (floor < 3 || isTrueFloor(player)) return "";
        var rand = player.getRandom();
        if (rand.nextFloat() > 0.45f) return "";
        String[] pool = {"blood", "frail", "swift", "rich", "heavy"};
        String m = pool[rand.nextInt(pool.length)];
        player.setData(ModAttachments.MUTATION.get(), m);
        player.sendSystemMessage(Component.literal("§d《異変 — " + mutationName(m) + "》" + mutationDesc(m)));
        return m;
    }

    public static String mutationName(String m) {
        return switch (m) {
            case "blood" -> "血月";
            case "frail" -> "虚弱の霧";
            case "swift" -> "疾風の層";
            case "rich" -> "富鉱の層";
            case "heavy" -> "重圧の層";
            default -> "静穏";
        };
    }

    public static String mutationDesc(String m) {
        return switch (m) {
            case "blood" -> "敵攻撃+20%・魂+25%";
            case "frail" -> "敵HP-20%";
            case "swift" -> "敵移動+20%";
            case "rich" -> "敵HP+10%・ドロップ2倍";
            case "heavy" -> "被ダメージ+15%";
            default -> "";
        };
    }

    public static boolean hasMutation(ServerPlayer player, String m) {
        return m.equals(player.getData(ModAttachments.MUTATION.get()));
    }

    /** 真・最終決戦 (原作TRUE_FLOOR=26) */
    public static void startTrue(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.setData(ModAttachments.FLOOR.get(), 26);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 1);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        buildArena(player);
        double hpMult = GameData.enemyHpMult(sigilsOf(player)) * diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(sigilsOf(player)) * diffDmgMult(player);
        Monster boss = EntityType.WARDEN.create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("true boss create failed");
        BlockPos pos = player.blockPosition().offset(0, 1, -10);
        boss.setPos(pos.getX(), pos.getY(), pos.getZ());
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(4000 * hpMult);
            boss.setHealth(boss.getMaxHealth());
        }
        var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(30 * dmgMult);
        boss.setCustomName(Component.literal("§f原初虚無 《ニヒル・ゼロ》"));
        boss.setCustomNameVisible(true);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        var bar = new ServerBossEvent(Component.literal("§f原初虚無 《ニヒル・ゼロ》"),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        bar.setProgress(1f);
        BOSS_BARS.put(boss, bar);
        player.sendSystemMessage(Component.literal("§f◆ 真・最終決戦 — 3フェーズの弾幕を耐え抜け！"));
    }

    /** 真ボスのフェーズ弾幕 (5秒ごとに残HP割合で変化) */
    public static void tickTrueBoss(ServerLevel level, Monster boss) {
        String name = boss.getCustomName() != null ? boss.getCustomName().getString() : "";
        if (!name.contains("ニヒル")) return;
        double ratio = boss.getHealth() / Math.max(1, boss.getMaxHealth());
        var at = boss.position();
        if (ratio > 0.66) {
            // 虚無の胎動: 小パルス
            WeaponArts.enemyPulse(level, at, 6, 8, ParticleTypes.PORTAL);
        } else if (ratio > 0.33) {
            // 深淵の咆哮: 大パルス+取り巻き
            WeaponArts.enemyPulse(level, at, 9, 14, ParticleTypes.FLAME);
            summonMinion(level, boss, EntityType.SKELETON);
        } else {
            // 終焉・万物の帰無: 特大パルス+取り巻き×2
            WeaponArts.enemyPulse(level, at, 12, 20, ParticleTypes.SOUL);
            summonMinion(level, boss, EntityType.SKELETON);
            summonMinion(level, boss, EntityType.SPIDER);
        }
    }

    private static void summonMinion(ServerLevel level, Monster boss, EntityType<? extends Monster> type) {
        long count = level.getEntitiesOfClass(Monster.class,
                new net.minecraft.world.phys.AABB(boss.blockPosition()).inflate(24),
                m -> !BOSS_BARS.containsKey(m)).size();
        if (count >= 6) return;
        Monster m = (Monster) type.create(level, EntitySpawnReason.EVENT);
        if (m == null) return;
        var p = boss.blockPosition().offset(
                boss.getRandom().nextInt(9) - 4, 1, boss.getRandom().nextInt(9) - 4);
        m.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        var target = level.getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), 48, false);
        if (target instanceof ServerPlayer sp) m.setTarget(sp);
        else if (target != null) m.setTarget(target);
        level.addFreshEntity(m);
    }

    /** 真ボス撃破 */
    public static void trueVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§f★ 虚無を_zeroに還した！ ★"));
        // 神話武器確定
        var styles = GameData.WeaponStyle.values();
        var style = styles[player.getRandom().nextInt(styles.length)];
        var stack = makeWeapon(style, player.getRandom());
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString("abyss_rarity", "mythic");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§d神話の" + style.weaponName));
        player.getInventory().add(stack);
        CombatHandler.grantAch(player, "true-clear", "虚無を_zeroに還した者", 32);
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
    }

    public static void nextFloor(ServerPlayer player) {
        String mode = player.getData(ModAttachments.MODE.get());
        int cur = player.getData(ModAttachments.FLOOR.get());
        if (cur >= 26) {
            player.sendSystemMessage(Component.literal("§7ここが最深層だ (真ボスを討て)"));
            return;
        }
        int floor = cur + 1;
        // 踏破判定 (原作FINAL_FLOOR=25 / 巡礼は8層主)
        if (("bossrush".equals(mode) && floor > 8) || (!"endless".equals(mode) && !"bossrush".equals(mode) && floor > 25)) {
            victory(player);
            return;
        }
        player.setData(ModAttachments.FLOOR.get(), floor);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        player.setData(ModAttachments.SHOP.get(), "");
        player.setData(ModAttachments.TRAPS.get(), "");
        player.setData(ModAttachments.DESTRUCT.get(), "");
        // 層移動回復 (飢餓の呪印で減衰)
        heal(player, player.getMaxHealth() * 0.25);
        player.teleportTo(player.getX(), player.getY(), player.getZ());
        spawnWave(player, floor);
        if (floor % 5 == 0) {
            player.displayClientMessage(Component.literal("§4◆ 第" + floor + "層 — 層主出現！"), false);
        } else {
            player.displayClientMessage(Component.literal("§5— 第" + floor + "層 —"), false);
        }
    }

    private static void buildArena(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = player.blockPosition();
        int r = 20;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                BlockPos p = center.offset(x, -1, z);
                level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                // 壁
                if (x * x + z * z > (r - 2) * (r - 2)) {
                    level.setBlock(center.offset(x, 0, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                    level.setBlock(center.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        player.displayClientMessage(Component.literal("§7試練のアリーナを展開した"), false);
    }

    /** ウェーブ湧き: 原作式 waveTotal = 9 + floor*2.4 をMC向けに圧縮 */
    public static void spawnWave(ServerPlayer player, int floor) {
        ServerLevel level = (ServerLevel) player.level();
        Set<String> sigils = sigilsOf(player);
        rollMutation(player, floor);
        double hpMult = GameData.enemyHpMult(sigils) * diffHpMult(player) * (1 + floor * 0.22);
        double dmgMult = GameData.enemyDmgMult(sigils) * diffDmgMult(player) * (1 + floor * 0.08);
        if (isDaily(player)) {
            hpMult *= dailyHpMult();
            dmgMult *= dailyDmgMult();
        }
        if (hasMutation(player, "frail")) hpMult *= 0.8;
        if (hasMutation(player, "rich")) hpMult *= 1.1;
        if (hasMutation(player, "blood")) dmgMult *= 1.2;
        boolean bossFloor = floor % 5 == 0 || isBossrush(player);
        var trial = GameData.trialRules(sigils);
        double eliteChance = GameData.eliteChance(floor, sigils);
        var rand = player.getRandom();

        int count = bossFloor ? 4 + floor / 2 : Math.min(24, 5 + (int) (floor * 1.6));
        player.setData(ModAttachments.WAVE_TOTAL.get(), count + (bossFloor ? 1 : 0));
        BlockPos center = player.blockPosition();

        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2 * i) / count;
            BlockPos pos = center.offset((int) (Math.cos(ang) * 14), 1, (int) (Math.sin(ang) * 14));
            Monster mob = pickMob(level, floor, i);
            mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            var hp = mob.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(Math.min(1024, mob.getMaxHealth() * hpMult));
                mob.setHealth(mob.getMaxHealth());
            }
            var atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(atk.getBaseValue() * dmgMult);
            var spd = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (spd != null) {
                // 疾風 ×1.16 / 深淵 ×1.10 (trialRules に集約)
                double base = spd.getBaseValue() * trial.enemySpeed();
                if (hasMutation(player, "swift")) base *= 1.2;
                spd.setBaseValue(base);
            }
            // 注視 ×1.45 / 深淵 ×1.10 の索敵距離
            var fol = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (fol != null) fol.setBaseValue(GameData.BASE_FOLLOW_RANGE * trial.detection());
            // 乱舞を含む原作式のエリート抽選 (発光+体力2倍)
            if (rand.nextDouble() < eliteChance) {
                var h2 = mob.getAttribute(Attributes.MAX_HEALTH);
                if (h2 != null) {
                    h2.setBaseValue(Math.min(2048, h2.getValue() * 2));
                    mob.setHealth(mob.getMaxHealth());
                }
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
                mob.setCustomName(Component.literal("§6エリート " + mob.getName().getString()));
            }
            mob.setTarget(player);
            level.addFreshEntity(mob);
        }

        if (bossFloor) {
            Monster boss = pickBoss(level, floor);
            BlockPos pos = center.offset(0, 1, -10);
            boss.setPos(pos.getX(), pos.getY(), pos.getZ());
            var hp = boss.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(Math.min(4096, (60 + floor * 60) * hpMult));
                boss.setHealth(boss.getMaxHealth());
            }
            var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue((8 + floor * 1.6) * dmgMult);
            boss.setCustomName(Component.literal("§4層主 第" + floor + "層: " + boss.getType().getDescription().getString()));
            boss.setCustomNameVisible(true);
            boss.setTarget(player);
            level.addFreshEntity(boss);
            // ボスバー表示
            var bar = new ServerBossEvent(
                    Component.literal("§4層主 第" + floor + "層"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(player);
            bar.setProgress(1f);
            BOSS_BARS.put(boss, bar);
        }
        placeHazards(player, level, center, floor);
    }

    /* ---------------- 罠・破壊物 ---------------- */

    /** 5層〜感圧罠 / 3層〜破壊物を配置 */
    private static void placeHazards(ServerPlayer player, ServerLevel level, BlockPos center, int floor) {
        var rand = player.getRandom();
        if (floor >= 5) {
            int n = Math.min(4, 2 + floor / 8);
            var sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double dist = 6 + rand.nextDouble() * 10;
                int x = center.getX() + (int) (Math.cos(ang) * dist);
                int z = center.getZ() + (int) (Math.sin(ang) * dist);
                int y = center.getY();
                if (!sb.isEmpty()) sb.append(";");
                sb.append(x).append(",").append(y).append(",").append(z);
                level.sendParticles(ParticleTypes.SMOKE, x + 0.5, y + 0.2, z + 0.5, 4, 0.3, 0.2, 0.3, 0.01);
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

    /** 感圧罠チェック (10tickごと) */
    public static void tickTraps(ServerPlayer player) {
        String raw = player.getData(ModAttachments.TRAPS.get());
        if (raw == null || raw.isBlank()) return;
        var level = (ServerLevel) player.level();
        var keep = new java.util.ArrayList<String>();
        boolean changed = false;
        for (String t : raw.split(";")) {
            String[] p = t.split(",");
            if (p.length != 3) continue;
            try {
                double x = Double.parseDouble(p[0]) + 0.5;
                double y = Double.parseDouble(p[1]);
                double z = Double.parseDouble(p[2]) + 0.5;
                double d = Math.hypot(player.getX() - x, player.getZ() - z);
                if (d < 2.5 && Math.abs(player.getY() - y) < 3) {
                    WeaponArts.trapBlast(level, player, new net.minecraft.world.phys.Vec3(x, y + 1, z));
                    player.sendSystemMessage(Component.literal("§c感圧罠が作動した！ (敵味方共通)"));
                    changed = true;
                } else {
                    keep.add(t);
                    if (d < 8) {
                        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 1, 0.2, 0.1, 0.2, 0.01);
                    }
                }
            } catch (NumberFormatException ignored) {
                changed = true;
            }
        }
        if (changed) player.setData(ModAttachments.TRAPS.get(), String.join(";", keep));
    }

    /* ---------------- 層間イベント ---------------- */

    /** 魂の欠片の所持数 */
    public static int countShards(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) n += s.getCount();
        }
        return n;
    }

    /** 魂の欠片をn個消費 (不足ならfalse) */
    public static boolean takeShards(ServerPlayer player, int n) {
        if (countShards(player) < n) return false;
        var inv = player.getInventory();
        int left = n;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            var s = inv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return true;
    }

    /** 層クリア時に選択肢を提示 (原作の rest/forge/oath/merchant) */
    public static void offerEvent(ServerPlayer player) {
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) return;
        // 商店の品揃えを生成
        var styles = GameData.WeaponStyle.values();
        var rand = player.getRandom();
        var shop = new java.util.ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var st = styles[rand.nextInt(styles.length)];
            int price = 2 + rand.nextInt(5);
            shop.add(st.id + ":" + price);
        }
        player.setData(ModAttachments.SHOP.get(), String.join(",", shop));
        var msg = Component.literal("§e◆ 層間イベント (1つ選択): ");
        msg.append(btn("§a[休息 HP50%回復]", "/abyss event rest"));
        msg.append(btn("§5[不滅の誓い 賭け]", "/abyss event oath"));
        msg.append(btn("§6[商店 欠片で購入]", "/abyss event shop"));
        msg.append(btn("§b[鍛造 武器合成]", "/abyss event forge"));
        player.sendSystemMessage(msg);
        player.sendSystemMessage(Component.literal("§7所持欠片: " + countShards(player) + "個"));
    }

    private static Component btn(String label, String cmd) {
        return Component.literal(label + " ").withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
                .withUnderlined(true));
    }

    public static void doEvent(ServerPlayer player, String kind) {
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) {
            player.sendSystemMessage(Component.literal("§cこの層のイベントは使用済み"));
            return;
        }
        switch (kind) {
            case "rest" -> {
                player.setData(ModAttachments.EVENT_DONE.get(), 1);
                heal(player, player.getMaxHealth() * 0.5);
                player.sendSystemMessage(Component.literal("§a休息した。HP50%回復。"));
            }
            case "oath" -> {
                player.setData(ModAttachments.EVENT_DONE.get(), 1);
                if (player.getRandom().nextBoolean()) {
                    player.setData(ModAttachments.OATH_DMG.get(),
                            player.getData(ModAttachments.OATH_DMG.get()) + 1);
                    player.sendSystemMessage(Component.literal("§6誓い成就！ 攻撃+8% (累積"
                            + player.getData(ModAttachments.OATH_DMG.get()) + "回)"));
                } else {
                    float dmg = player.getMaxHealth() * 0.25f;
                    player.hurtServer((ServerLevel) player.level(),
                            player.level().damageSources().magic(), dmg);
                    player.sendSystemMessage(Component.literal("§c誓い破れたり… 最大HP25%の反動！"));
                }
            }
            case "shop" -> {
                String raw = player.getData(ModAttachments.SHOP.get());
                if (raw == null || raw.isBlank()) {
                    player.sendSystemMessage(Component.literal("§c商品なし"));
                    return;
                }
                var msg = Component.literal("§6商店 (所持欠片" + countShards(player) + "): ");
                String[] items = raw.split(",");
                for (int i = 0; i < items.length; i++) {
                    String[] kv = items[i].split(":");
                    var st = GameData.WeaponStyle.byId(kv[0]);
                    msg.append(btn("§e[" + (i + 1) + ":" + st.weaponName + " " + kv[1] + "欠片] ",
                            "/abyss buy " + (i + 1)));
                }
                player.sendSystemMessage(msg);
            }
            case "forge" -> {
                var main = player.getMainHandItem();
                var off = player.getOffhandItem();
                if (ModItems.styleOf(main.getItem()) == null || ModItems.styleOf(off.getItem()) == null) {
                    player.sendSystemMessage(Component.literal("§c両手にAbyss武器を持ってね (メイン+オフハンド合成・欠片3)"));
                    return;
                }
                int lv = forgeLv(main);
                if (lv >= 5) {
                    player.sendSystemMessage(Component.literal("§cこの武器は鍛造MAX (覚醒・神錬相当)"));
                    return;
                }
                if (!takeShards(player, 3)) {
                    player.sendSystemMessage(Component.literal("§c欠片3個が足りない (所持" + countShards(player) + ")"));
                    return;
                }
                off.shrink(1);
                setForgeLv(main, lv + 1);
                player.setData(ModAttachments.EVENT_DONE.get(), 1);
                player.sendSystemMessage(Component.literal(
                        "§b神錬！ " + main.getHoverName().getString() + " 鍛造+" + (lv + 1) + " (攻撃+8%/Lv)"));
            }
            default -> player.sendSystemMessage(Component.literal("§crest/oath/shop/forge で指定"));
        }
    }

    public static void doBuy(ServerPlayer player, int index) {
        String raw = player.getData(ModAttachments.SHOP.get());
        if (raw == null || raw.isBlank()) {
            player.sendSystemMessage(Component.literal("§c商店は開いていない (/abyss event shop)"));
            return;
        }
        String[] items = raw.split(",");
        if (index < 1 || index > items.length) {
            player.sendSystemMessage(Component.literal("§c1〜" + items.length + "で指定"));
            return;
        }
        String[] kv = items[index - 1].split(":");
        int price;
        try {
            price = Integer.parseInt(kv[1]);
        } catch (Exception ex) {
            return;
        }
        var st = GameData.WeaponStyle.byId(kv[0]);
        if (!takeShards(player, price)) {
            player.sendSystemMessage(Component.literal("§c欠片" + price + "個が足りない (所持" + countShards(player) + ")"));
            return;
        }
        player.getInventory().add(makeWeapon(st, player.getRandom()));
        player.setData(ModAttachments.EVENT_DONE.get(), 1);
        player.sendSystemMessage(Component.literal("§6購入: " + st.weaponName + " (奥義「" + st.artName + "」)"));
    }

    private static Monster pickMob(ServerLevel level, int floor, int i) {
        EntityType<? extends Monster> type;
        // 層帯で出現表を切替 (原作ENEMIESのminFloor準拠の簡易版)
        if (floor >= 9 && i % 4 == 0) type = EntityType.BLAZE;
        else if (floor >= 7 && i % 5 == 0) type = EntityType.HUSK; // 岩塊ゴーレム相当のタンク枠
        else if (floor >= 6 && i % 3 == 0) type = EntityType.WITCH; // 怨念レイス相当
        else if (floor >= 4 && i % 4 == 1) type = EntityType.CREEPER; // 自爆ガエル相当
        else if (floor >= 3 && i % 3 == 1) type = EntityType.CAVE_SPIDER;
        else if (floor >= 2 && i % 2 == 0) type = EntityType.SKELETON;
        else type = EntityType.ZOMBIE;
        Monster mob = (Monster) type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            Monster fallback = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
            if (fallback == null) throw new IllegalStateException("mob create failed");
            return fallback;
        }
        return mob;
    }

    private static Monster pickBoss(ServerLevel level, int floor) {
        EntityType<? extends Monster> type =
                floor >= 20 ? EntityType.WARDEN :
                floor >= 15 ? EntityType.VINDICATOR :
                floor >= 10 ? EntityType.EVOKER :
                EntityType.PILLAGER;
        Monster mob = (Monster) type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) throw new IllegalStateException("boss create failed");
        return mob;
    }
}
