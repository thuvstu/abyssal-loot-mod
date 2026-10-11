package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 魂ゲージ・奥義/必殺/回避の発動・見切り・独自ダメージ式.
 * 操作 (v0.1, キー不要):
 *  - 素手/武器で右クリック: 魂100で武器奥義
 *  - スニーク+右クリック: 必殺技 (魂不要, 60秒CTなしの簡易版)
 *  - /abyss dash: 回避ダッシュ (0.5秒無敵, 見切り延長あり)
 */
public final class CombatHandler {
    private CombatHandler() {}

    /** 遅延実行キュー (サーバtickで減衰。WeaponArtsの隕石・爆縮用) */
    private record DelayTask(int ticksLeft, Runnable task) {}
    private static final java.util.List<DelayTask> PENDING =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    /** 最終撃破tick (コンボ減衰用) */
    private static final java.util.Map<java.util.UUID, Long> LAST_KILL_TICK =
            new java.util.HashMap<>();

    public static void after(int ticks, Runnable task) {
        PENDING.add(new DelayTask(ticks, task));
    }

    /** 撃破で魂ゲージ蓄積 (原作 artChargeGain の簡易版: 固定+敵最大HP比) */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 奥義ゲージは撃破では増えない (原作: 与ダメ・被ダメで蓄積, onDamage 側で処理)
        player.setData(ModAttachments.KILLS.get(), player.getData(ModAttachments.KILLS.get()) + 1);
        player.setData(ModAttachments.COMBO.get(), player.getData(ModAttachments.COMBO.get()) + 1);
        // ドロップ: 6%で深淵武器 / 20%で魂の欠片. 幸運の運(+7/点)で武器率が上がり、
        // 魂の欠片は報酬倍率(呪印・難易度・契約)と幸運の素材倍率(+4%/点)を受ける
        var rand = player.getRandom();
        boolean rich = RunManager.hasMutation(player, "rich");
        int luck = GameData.luckOf(RunManager.attrsOf(player)[4]);
        double weaponChance = (rich ? 0.12 : 0.06) + GameData.luckDropBonus(luck);
        if (rand.nextDouble() < weaponChance) {
            var styles = GameData.WeaponStyle.values();
            var style = styles[rand.nextInt(styles.length)];
            var drop = RunManager.makeWeapon(style, rand);
            giveOrDrop(player, drop);
            player.sendSystemMessage(Component.literal(
                    "§b戦利品: " + drop.getHoverName().getString() + " (持ち替えで奥義が変わる)"));
        } else if (rand.nextDouble() < (rich ? 0.40 : 0.20)) {
            int shards = RunManager.materialCount(player, 1.0);
            if (shards > 0) giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        }
        // レベルアップ: LvL→L+1 に (L-1)(L+4) 撃破
        int kills = player.getData(ModAttachments.KILLS.get());
        int lv = player.getData(ModAttachments.LEVEL.get());
        while (kills >= needFor(lv + 1)) {
            lv++;
            var cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
            RunManager.applyClass(player, cls, lv);
            RunManager.heal(player, player.getMaxHealth() * 0.25);
            player.sendSystemMessage(Component.literal("§e◆ レベルアップ！ Lv" + lv + " (HP25%回復)"));
            if (RunManager.isCovenant(player)) {
                player.sendSystemMessage(Component.literal("§5不幸の盟約: 祝福は得られない (魂×3.0で償う)"));
            } else {
                offerBoons(player);
            }
        }
        // 層主撃破の記録 + ボスバー除去
        var bars = RunManager.BOSS_BARS;
        if (bars.containsKey(event.getEntity())) {
            var bar = bars.remove(event.getEntity());
            for (ServerPlayer p : level.players()) bar.removePlayer(p);
            int floor = player.getData(ModAttachments.FLOOR.get());
            String rec = player.getData(ModAttachments.BOSSES.get());
            player.setData(ModAttachments.BOSSES.get(), rec.isBlank() ? String.valueOf(floor) : rec + "," + floor);
            player.sendSystemMessage(Component.literal("§6◆ 層主を撃破！ 第" + floor + "層 (記録: " + player.getData(ModAttachments.BOSSES.get()) + ")"));
            RunManager.addGauge(player, 30);
        }
        // 武器特性: 吸血鬼 (撃破時HP3%回復) / 爆砕 (周囲爆発)
        String killTrait = RunManager.traitOf(player.getMainHandItem());
        if ("vampiric".equals(killTrait)) {
            RunManager.heal(player, player.getMaxHealth() * 0.03);
        }
        if ("explosive".equals(killTrait)) {
            var at = event.getEntity().position();
            WeaponArts.pulse(level, player, at, 4, 1.2, false,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
        // 実績
        LAST_KILL_TICK.put(player.getUUID(), (long) tickCounter);
        grantAch(player, "first-blood", "深淵への第一歩", 2);
        if (player.getData(ModAttachments.KILLS.get()) >= 100) {
            grantAch(player, "hundred-kills", "百鬼夜行", 8);
        }
        String bossRec = player.getData(ModAttachments.BOSSES.get());
        if (bossRec.contains("10")) grantAch(player, "floor-ten", "深層探査者", 10);
        // 真ボス撃破判定
        var deadName = event.getEntity().getCustomName();
        if (deadName != null && deadName.getString().contains("ニヒル")) {
            RunManager.trueVictory(player);
        }
        // 層クリア判定
        // 蘇生師・門の子は層のウェーブに含めない (数えると転移門が早く開いてしまう)
        if (!event.getEntity().getTags().contains(Enemies.SUMMON_TAG)) {
            player.setData(ModAttachments.FLOOR_KILLS.get(), player.getData(ModAttachments.FLOOR_KILLS.get()) + 1);
        }
        // 呪われた召喚門の破壊 (原作: 戦績に保存され実績「門を断つ者」)
        if (event.getEntity().getTags().contains(Enemies.GATE_TAG)) {
            grantAch(player, "gate-breaker", "門を断つ者", 8);
        }
        int total = player.getData(ModAttachments.WAVE_TOTAL.get());
        if (total > 0 && player.getData(ModAttachments.PORTAL_OPEN.get()) == 0
                && player.getData(ModAttachments.FLOOR_KILLS.get()) >= total) {
            player.setData(ModAttachments.PORTAL_OPEN.get(), 1);
            player.sendSystemMessage(Component.literal("§a◆ 転移門が開いた！ /abyss next で次の層へ進め"));
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
            RunManager.offerEvent(player);
        }
    }

    /* ---------------- 破壊物 ---------------- */

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        String raw = player.getData(ModAttachments.DESTRUCT.get());
        if (raw == null || raw.isBlank()) return;
        var pos = event.getPos();
        var keep = new java.util.ArrayList<String>();
        boolean hit = false;
        for (String d : raw.split(";")) {
            String[] p = d.split(",");
            if (p.length != 4) continue;
            try {
                int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
                if (!hit && x == pos.getX() && y == pos.getY() && z == pos.getZ()) {
                    hit = true;
                    triggerDestructible(player, level, p[0], pos);
                } else {
                    keep.add(d);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (hit) player.setData(ModAttachments.DESTRUCT.get(), String.join(";", keep));
    }

    private static void triggerDestructible(ServerPlayer player, ServerLevel level, String kind,
                                            net.minecraft.core.BlockPos pos) {
        var at = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        switch (kind) {
            case "red" -> {
                // 赤水晶: 周囲の敵を爆破 (自分にも当たる)
                WeaponArts.trapBlast(level, player, at);
                player.sendSystemMessage(Component.literal("§c赤水晶が爆発した！"));
            }
            case "blue" -> {
                // 青瓶: HP30%+魂30回復
                RunManager.heal(player, player.getMaxHealth() * 0.3);
                RunManager.addGauge(player, 30);
                player.sendSystemMessage(Component.literal("§b青の霊薬！ HPと魂が回復した"));
            }
            default -> {
                // 運命の祭壇: 賭け
                int roll = player.getRandom().nextInt(4);
                switch (roll) {
                    case 0 -> {
                        RunManager.heal(player, player.getMaxHealth());
                        player.sendSystemMessage(Component.literal("§6運命の加護【満月】HP全回復！"));
                    }
                    case 1 -> {
                        player.setData(ModAttachments.SOUL.get(), 100f);
                        player.sendSystemMessage(Component.literal("§d運命の加護【新月】魂MAX！"));
                    }
                    case 2 -> {
                        for (int i = 0; i < 5; i++) {
                            giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
                        }
                        player.sendSystemMessage(Component.literal("§e運命の加護【黄金】欠片×5！"));
                    }
                    default -> {
                        RunManager.spawnWave(player, player.getData(ModAttachments.FLOOR.get()));
                        player.sendSystemMessage(Component.literal("§4運命の災厄【蝕】追っ手が来た！"));
                    }
                }
            }
        }
    }

    private static int needFor(int level) {
        return (level - 1) * (level + 4);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            var level = (ServerLevel) player.level();
            var e = new net.minecraft.world.entity.item.ItemEntity(
                    level, player.getX(), player.getY() + 1, player.getZ(), stack);
            level.addFreshEntity(e);
        }
    }

    /** 実績付与 (重複なし・欠片報酬) */
    public static void grantAch(ServerPlayer player, String id, String title, int shards) {
        String raw = player.getData(ModAttachments.ACH.get());
        var have = new java.util.HashSet<String>();
        if (raw != null && !raw.isBlank()) have.addAll(java.util.List.of(raw.split(",")));
        if (have.contains(id)) return;
        have.add(id);
        player.setData(ModAttachments.ACH.get(), String.join(",", have));
        player.sendSystemMessage(Component.literal("§e★ 実績解除: " + title + " (欠片+" + shards + ")"));
        for (int i = 0; i < shards; i++) {
            giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
        }
    }

    /* ---------------- 祝福3択 ---------------- */

    public record BoonDef(String id, String name, String desc) {}

    public static final BoonDef[] BOON_POOL = {
            new BoonDef("dmg", "剛力の刻印", "攻撃+12%"),
            new BoonDef("as", "迅風の刻印", "攻速+10%"),
            new BoonDef("crit", "心眼の刻印", "会心+5%"),
            new BoonDef("hp", "巨躯の刻印", "最大HP+10%"),
            new BoonDef("armor", "鉄壁の刻印", "被ダメ-6%"),
            new BoonDef("speed", "疾走の刻印", "移動+6%"),
            new BoonDef("art", "武の刻印", "奥義・必殺+12%"),
            new BoonDef("life", "吸血の刻印", "与ダメの4%回復"),
            new BoonDef("orbit", "回転刃", "周囲を刃が周回する"),
            new BoonDef("aura", "聖なる領域", "周囲の敵を焼く"),
            new BoonDef("chain", "連鎖雷撃", "命中時に雷が連鎖"),
            new BoonDef("missile", "追尾魔弾", "自動で魔弾を発射"),
            new BoonDef("nova", "氷霜新星", "6秒ごとに氷結波"),
            new BoonDef("familiar", "影の眷属", "使い魔が援護射撃"),
            new BoonDef("quake", "大地の怒り", "回避着地で爆発"),
    };

    public static BoonDef boonById(String id) {
        for (var b : BOON_POOL) {
            if (b.id().equals(id)) return b;
        }
        return null;
    }

    public static void offerBoons(ServerPlayer player) {
        var pool = new java.util.ArrayList<>(java.util.List.of(BOON_POOL));
        java.util.Collections.shuffle(pool, new java.util.Random(player.getRandom().nextLong()));
        var pick = pool.subList(0, Math.min(3, pool.size()));
        var ids = new java.util.ArrayList<String>();
        var msg = Component.literal("§e祝福を選べ: ");
        for (var b : pick) {
            ids.add(b.id());
            var btn = Component.literal("§6[" + b.name() + " " + b.desc() + "] ")
                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss boon " + b.id()))
                            .withUnderlined(true));
            msg.append(btn);
        }
        player.setData(ModAttachments.OFFER.get(), String.join(",", ids));
        player.sendSystemMessage(msg);
    }

    /**
     * 鉄壁の護衛兵: 前方±60°からの被ダメ30%、側面・背面は135% (原作 guardMultiplier).
     * 他の補正の後に掛けるため LOW 優先度.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGuardHit(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer || !victim.getTags().contains(Enemies.GUARD_TAG)) return;
        Vec3 src = event.getSource().getSourcePosition();
        if (src == null) return;
        Vec3 toSource = new Vec3(src.x - victim.getX(), 0, src.z - victim.getZ());
        Vec3 facing = victim.getLookAngle();
        facing = new Vec3(facing.x, 0, facing.z);
        double a = toSource.length();
        double b = facing.length();
        if (a < 1e-6 || b < 1e-6) return;
        double cos = (toSource.x * facing.x + toSource.z * facing.z) / (a * b);
        double mult = cos >= Math.cos(Math.PI / 3) ? 0.30 : 1.35;
        event.setNewDamage(event.getNewDamage() * (float) mult);
    }

    /** 無敵時間の適用 + 武器ダメージの原作式上書き */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel) {
            float iframe = player.getData(ModAttachments.IFRAME.get());
            if (iframe > 0) {
                event.setNewDamage(0f);
                // 見切り: 無敵中に受けた攻撃は魂+14
                RunManager.addGauge(player, 14);
                player.displayClientMessage(Component.literal("§a見切り！"), true);
                return;
            }
            // 鉄壁の刻印: 被ダメ軽減 (上限50%)
            int armor = RunManager.boonLv(player, "armor");
            if (armor > 0) {
                event.setNewDamage(event.getNewDamage() * (float) Math.max(0.5, 1 - 0.06 * armor));
            }
            // 不壊: 命中から10秒は被ダメ-10%
            if (player.getData(ModAttachments.FORTIFY.get()) > 0) {
                event.setNewDamage(event.getNewDamage() * 0.9f);
            }
            // 重圧の層: 被ダメ+15%
            if (RunManager.hasMutation(player, "heavy")) {
                event.setNewDamage(event.getNewDamage() * 1.15f);
            }
            // 被ダメ由来の奥義ゲージ (原作: 残HPまでの被害 / 最大HP * 35)
            float taken = Math.min(event.getNewDamage(), player.getHealth());
            RunManager.addGauge(player, GameData.damageTakenCharge(taken, player.getMaxHealth()));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            // 奥義・罠・二次反応の命中は威力を上書きせず、ゲージも増やさない
            if (WeaponArts.inArtHit()) return;
            ItemStack held = player.getMainHandItem();
            if (ModItems.styleOf(held.getItem()) != null) {
                double atk = RunManager.attackDamageOf(player);
                boolean crit = player.getRandom().nextFloat() < RunManager.critOf(player);
                float dmg = (float) (atk * (crit ? 2.0 : 1.0));
                // レアリティ倍率
                dmg *= (float) RunManager.rarityMult(RunManager.rarityOf(held));
                String trait = RunManager.traitOf(held);
                // 反撃窓: 回避直後の初撃×1.5 (原作カウンター)
                if (player.getData(ModAttachments.COUNTER.get()) > 0) {
                    dmg *= 1.5f;
                    player.setData(ModAttachments.COUNTER.get(), 0f);
                    player.displayClientMessage(Component.literal("§e反撃！"), true);
                }
                LivingEntity target = event.getEntity();
                if ("sharp".equals(trait)) dmg *= 1.08f;
                if ("terminator".equals(trait)
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= 1.5f;
                }
                if ("frenzy".equals(trait) && player.getData(ModAttachments.COMBO.get()) >= 30) {
                    dmg *= 1.25f;
                }
                event.setNewDamage(dmg);
                // 与ダメ由来の奥義ゲージ (残HPまで=オーバーキル除外)
                float actual = Math.min(dmg, target.getHealth());
                RunManager.addGauge(player, GameData.artChargeGain(actual, atk, false));
                // 吸血の刻印 (祝福)
                int life = RunManager.boonLv(player, "life");
                if (life > 0 && dmg > 0) RunManager.heal(player, dmg * 0.04 * life);
                // 不壊の付与
                if ("fortify".equals(trait)) player.setData(ModAttachments.FORTIFY.get(), 10f);
                // 状態異常 (業火/猛毒/裂傷)
                var rand = player.getRandom();
                if ("infernus".equals(trait) && rand.nextFloat() < 0.15f) {
                    target.setRemainingFireTicks(60);
                }
                if ("venomous".equals(trait) && rand.nextFloat() < 0.12f) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                }
                if ("rend".equals(trait) && rand.nextFloat() < 0.10f) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
                // 連鎖雷 (武器特性 / 祝福): 他の敵へ飛び移る
                int chainLv = RunManager.boonLv(player, "chain");
                boolean weaponChain = "chain_l".equals(trait);
                if ((weaponChain || chainLv > 0) && rand.nextFloat() < (weaponChain ? 1f : 0.25f)) {
                    float chainDmg = (float) (atk * (weaponChain ? 0.5 : 0.6));
                    chainStrike(level, player, target, chainDmg);
                }
            }
        }
    }

    /** 連鎖雷撃: 目標以外の最寄り2体へ飛び移る */
    private static void chainStrike(ServerLevel level, ServerPlayer player, LivingEntity from, float dmg) {
        var pos = from.position();
        var others = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos.x - 8, pos.y - 4, pos.z - 8, pos.x + 8, pos.y + 4, pos.z + 8),
                e -> e.isAlive() && e != from && !(e instanceof ServerPlayer));
        others.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
        int n = 0;
        for (var e : others) {
            if (n++ >= 2) break;
            WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    e.getX(), e.getY() + 1, e.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
        }
        if (n > 0) {
            level.playSound(null, from.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    /** 右クリック=奥義 / スニーク+右クリック=必殺 / 魂の欠片=魂+25 */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel)) return;
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.SOUL_SHARD.get())) {
            RunManager.addGauge(player, 25);
            float soul = player.getData(ModAttachments.SOUL.get());
            stack.shrink(1);
            player.displayClientMessage(Component.literal(String.format("§d魂+25 (%.0f/100)", soul)), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        GameData.WeaponStyle style = ModItems.styleOf(stack.getItem());
        if (style == null) return;
        // 武器を持ち替えたらその武器種の奥義になる (原作仕様)
        if (player.isShiftKeyDown()) {
            trySkill(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (tryArt(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 武器奥義の発動判定 (右クリック / Rキー / コマンド共通) */
    public static boolean tryArt(ServerPlayer player) {
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        if (style == null) {
            player.displayClientMessage(Component.literal("§cAbyss武器を手に持ってね"), true);
            return false;
        }
        float soul = player.getData(ModAttachments.SOUL.get());
        if (soul < 100f) {
            player.displayClientMessage(
                    Component.literal(String.format("§7魂ゲージ %.0f/100", soul)), true);
            return false;
        }
        if (WeaponArts.execute(player, style,
                RunManager.attackDamageOf(player) * RunManager.artMultOf(player))) {
            player.setData(ModAttachments.SOUL.get(), 0f);
            return true;
        }
        return false;
    }

    /** 必殺技の発動 (スニーク+右クリック / Fキー / コマンド共通) */
    public static void trySkill(ServerPlayer player) {
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        WeaponArts.executeSkill(player, cls,
                RunManager.attackDamageOf(player) * RunManager.artMultOf(player));
    }

    /** 無敵・ダッシュCT・反撃窓の減衰 + ボスバー更新 + 罠 */
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        tickCounter++;
        // 遅延タスク
        if (!PENDING.isEmpty()) {
            var done = new java.util.ArrayList<DelayTask>();
            synchronized (PENDING) {
                for (int i = 0; i < PENDING.size(); i++) {
                    DelayTask d = PENDING.get(i);
                    if (d.ticksLeft() <= 0) {
                        done.add(d);
                    } else {
                        PENDING.set(i, new DelayTask(d.ticksLeft() - 1, d.task()));
                    }
                }
                PENDING.removeAll(done);
            }
            for (DelayTask d : done) {
                try {
                    d.task().run();
                } catch (Exception ex) {
                    AbyssalLootMod.LOGGER.warn("delayed task failed", ex);
                }
            }
        }
        EnemyAI.tickGuards(server);
        EnemyAI.tick(server, tickCounter);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            float iframe = player.getData(ModAttachments.IFRAME.get());
            if (iframe > 0) player.setData(ModAttachments.IFRAME.get(), Math.max(0, iframe - 0.05f));
            float dashCd = player.getData(ModAttachments.DASH_CD.get());
            if (dashCd > 0) player.setData(ModAttachments.DASH_CD.get(), Math.max(0, dashCd - 0.05f));
            float counter = player.getData(ModAttachments.COUNTER.get());
            if (counter > 0) player.setData(ModAttachments.COUNTER.get(), Math.max(0, counter - 0.05f));
            float fortify = player.getData(ModAttachments.FORTIFY.get());
            if (fortify > 0) player.setData(ModAttachments.FORTIFY.get(), Math.max(0, fortify - 0.05f));
            if (tickCounter % 10 == 0) RunManager.tickTraps(player);
            if (tickCounter % 20 == 0) tickAbilityBoons(player);
            // コンボ減衰 (5秒撃破なしでリセット・原作comboT準拠)
            Long last = LAST_KILL_TICK.get(player.getUUID());
            if (player.getData(ModAttachments.COMBO.get()) > 0
                    && (last == null || tickCounter - last > 100)) {
                player.setData(ModAttachments.COMBO.get(), 0);
            }
            if (tickCounter % 20 == 0) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new ModPackets.HudSync(
                                player.getData(ModAttachments.SOUL.get()),
                                player.getData(ModAttachments.FLOOR.get()),
                                player.getData(ModAttachments.LEVEL.get()),
                                player.getData(ModAttachments.KILLS.get()),
                                player.getData(ModAttachments.COMBO.get())));
            }
        }
        // ボスバー進捗 + 真ボス弾幕
        var dead = new java.util.ArrayList<net.minecraft.world.entity.monster.Monster>();
        synchronized (RunManager.BOSS_BARS) {
            for (var e : RunManager.BOSS_BARS.entrySet()) {
                var boss = e.getKey();
                if (!boss.isAlive() || boss.isRemoved()) {
                    dead.add(boss);
                    continue;
                }
                e.getValue().setProgress((float) (boss.getHealth() / Math.max(1, boss.getMaxHealth())));
                if (tickCounter % 100 == 0 && boss.level() instanceof ServerLevel sl) {
                    RunManager.tickTrueBoss(sl, boss);
                }
            }
        }
        for (var boss : dead) {
            var bar = RunManager.BOSS_BARS.remove(boss);
            if (bar != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.removePlayer(p);
            }
        }
    }

    /** 祝福能力の自動発動 (回転刃/聖域/魔弾・1秒ごと) */
    private static void tickAbilityBoons(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.isAlive()) return;
        var pos = player.position();
        if (RunManager.boonLv(player, "orbit") > 0) {
            WeaponArts.pulse(level, player, pos, 3.5, 0.8, false,
                    net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
        }
        if (RunManager.boonLv(player, "aura") > 0) {
            WeaponArts.pulse(level, player, pos, 5, 0.5, false,
                    net.minecraft.core.particles.ParticleTypes.FLAME);
        }
        if (RunManager.boonLv(player, "missile") > 0) {
            var target = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos.x - 16, pos.y - 6, pos.z - 16,
                            pos.x + 16, pos.y + 6, pos.z + 16),
                    e -> e.isAlive() && !(e instanceof ServerPlayer)).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                    .orElse(null);
            if (target != null) {
                WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                        (float) (RunManager.attackDamageOf(player) * 0.7));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                        target.getX(), target.getY() + 1, target.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
            }
        }
        // 影の眷属: 3秒ごとに使役が援護 (2秒周期tickの3回に1回≒6秒では遅いので毎tick判定で別カウンタ)
        if (RunManager.boonLv(player, "familiar") > 0 && tickCounter % 60 == 0) {
            var target = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos.x - 12, pos.y - 6, pos.z - 12,
                            pos.x + 12, pos.y + 6, pos.z + 12),
                    e -> e.isAlive() && !(e instanceof ServerPlayer)).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                    .orElse(null);
            if (target != null) {
                WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                        (float) (RunManager.attackDamageOf(player) * 0.5));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                        target.getX(), target.getY() + 1, target.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
            }
        }
        // 氷霜新星: 6秒ごとに凍結波
        if (RunManager.boonLv(player, "nova") > 0 && tickCounter % 120 == 0) {
            WeaponArts.pulse(level, player, pos, 6, 0.4, false,
                    net.minecraft.core.particles.ParticleTypes.SNOWFLAKE);
            for (var e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos.x - 6, pos.y - 4, pos.z - 6,
                            pos.x + 6, pos.y + 4, pos.z + 6),
                    e -> e.isAlive() && !(e instanceof ServerPlayer))) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
            }
        }
    }

    /** 回避ダッシュ: 前方6m瞬動 + 0.5秒無敵 (原作dashの簡易版) */
    public static void dash(ServerPlayer player) {
        float cd = player.getData(ModAttachments.DASH_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7回避CT %.1fs", cd)), true);
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dest = player.position().add(look.x * 6, 0, look.z * 6);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.setData(ModAttachments.IFRAME.get(), 0.5f);
        // 原作: 職業別の回避CT. 敏捷1点につき-1% (下限0.28秒)
        var cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        player.setData(ModAttachments.DASH_CD.get(),
                (float) GameData.dashCooldown(cls, RunManager.attrsOf(player)[2]));
        player.setData(ModAttachments.COUNTER.get(), 1.0f);
        player.displayClientMessage(Component.literal("§b回避！"), true);
        ((ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                dest.x, dest.y + 1, dest.z, 12, 0.8, 0.6, 0.8, 0.05);
        // 大地の怒り: 回避着地で爆発
        if (RunManager.boonLv(player, "quake") > 0) {
            WeaponArts.pulse((ServerLevel) player.level(), player,
                    new Vec3(dest.x, dest.y + 1, dest.z), 5, 1.5, false,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
    }

    public static void showStatus(ServerPlayer player) {
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        int[] a = RunManager.attrsOf(player);
        player.sendSystemMessage(Component.literal(String.format(
                "§6[%s %s Lv%d] §f第%d層[%s/%s] §d魂%.0f/100 §c攻撃%.1f §aHP%.0f/%.0f §e撃破%d §b奥義:%s",
                cls.name, cls.title, player.getData(ModAttachments.LEVEL.get()),
                player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.MODE.get()), player.getData(ModAttachments.DIFF.get()),
                player.getData(ModAttachments.SOUL.get()),
                RunManager.attackDamageOf(player),
                player.getHealth(), player.getMaxHealth(),
                player.getData(ModAttachments.KILLS.get()),
                style != null ? style.artName : "武器を持ってね")));
        player.sendSystemMessage(Component.literal(String.format(
                "§7属性 剛%d 韻%d 敏%d 忍%d 幸%d §8| §7祝福 %s §8| §7層主 %s",
                a[0], a[1], a[2], a[3], a[4],
                RunManager.boonsOf(player).toString(),
                player.getData(ModAttachments.BOSSES.get()))));
        var held = player.getMainHandItem();
        if (ModItems.styleOf(held.getItem()) != null) {
            String trait = RunManager.traitOf(held);
            player.sendSystemMessage(Component.literal(String.format(
                    "§7武器 %s (希少:%s 特性:%s 鍛造+%d)",
                    held.getHoverName().getString(),
                    RunManager.rarityOf(held),
                    trait != null ? RunManager.traitName(trait) : "なし",
                    RunManager.forgeLv(held))));
        }
    }
}
