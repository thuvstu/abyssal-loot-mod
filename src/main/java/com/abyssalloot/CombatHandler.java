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
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 魂ゲージ・奥義/必殺/小技/回避の発動・見切り・独自ダメージ式.
 * 操作:
 *  - 武器で右クリック: 武器小技 (CT1~3秒で連発)
 *  - スニーク+右クリック: 必殺技 (魂不要, CT25秒)
 *  - Rキー / /abyss art: 武器奥義 (魂ゲージ100で発動)
 *  - Xキー / /abyss dash: 回避ダッシュ (0.5秒無敵, 見切り延長あり)
 */
public final class CombatHandler {
    private CombatHandler() {}

    /** 遅延実行キュー (絶対tickで発火。WeaponArtsの隕石・爆縮用) */
    private record DelayTask(long targetTick, Runnable task) {}
    private static final java.util.List<DelayTask> PENDING =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    /** 最終撃破tick (コンボ減衰用) */
    private static final java.util.Map<java.util.UUID, Long> LAST_KILL_TICK =
            new java.util.HashMap<>();

    /** 深淵タグ持ちか (ドロップ抑止・放浪判定の共用) */
    private static boolean hasAbyssTag(net.minecraft.world.entity.Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith("abyss_")) return true;
        }
        return false;
    }

    /**
     * 深淵MOBのバニラドロップを消す (骨・矢・火薬などのゴミ拾い防止).
     * Mod の報酬 (武器・欠片) は直接配布のため影響なし。経験値オーブは残る.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMobDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof ServerPlayer) return;
        boolean ours = RunManager.BOSS_BARS.containsKey(e) || hasAbyssTag(e);
        if (ours) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    public static void after(int ticks, Runnable task) {
        PENDING.add(new DelayTask(tickCounter + Math.max(0, ticks), task));
    }

    /** 装備変更で即時ステータス再計算 (防具の着脱・持替でHP等が追従する) */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isAlive()) return;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()), true);
    }

    /** 魔弾の威力倍率 (castWandBolt が hurtServer 直前に設定し、命中処理で消費) */
    public static double boltMult = 0;

    /** 出撃中の死亡: 死なずにリタイア処理 (装備・欠片は保持)。出撃外では通常死亡 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getData(ModAttachments.RUN_LOCK.get()) == 0
                && player.getData(ModAttachments.WAVE_TOTAL.get()) == 0) return;
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth() * 0.5f);
        player.removeAllEffects();
        RunManager.retire(player);
        player.sendSystemMessage(Component.literal("§c敗北…深淵の加護で生還した (装備・欠片は保持)"));
    }

    /** 撃破で魂ゲージ蓄積 (原作 artChargeGain の簡易版: 固定+敵最大HP比) */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 奥義ゲージは撃破では増えない (原作: 与ダメ・被ダメで蓄積, onDamage 側で処理)
        player.setData(ModAttachments.KILLS.get(), player.getData(ModAttachments.KILLS.get()) + 1);
        player.setData(ModAttachments.COMBO.get(), player.getData(ModAttachments.COMBO.get()) + 1);
        // スコア用: 最大コンボを記録
        if (player.getData(ModAttachments.COMBO.get()) > player.getData(ModAttachments.BEST_COMBO.get())) {
            player.setData(ModAttachments.BEST_COMBO.get(), player.getData(ModAttachments.COMBO.get()));
        }
        // 雷天の共闘: 雷雨中の討伐は深淵に響く (バニラの天候との融合)
        if (level.isThundering()) RunManager.addGauge(player, 5);
        // 放浪の討伐: 試練外でのバニラ敵討伐もカウントし、25体ごとに欠片が戻る
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                && !RunManager.BOSS_BARS.containsKey(event.getEntity())
                && !hasAbyssTag(event.getEntity())) {
            int roam = player.getData(ModAttachments.ROAM_KILLS.get()) + 1;
            if (roam >= 25) {
                roam = 0;
                int shards = RunManager.materialCount(player, 1);
                if (shards > 0) {
                    giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
                    player.sendSystemMessage(Component.literal(
                            "§7放浪の討伐 ×25 — 魂が欠片となって戻った +" + shards));
                    RunManager.addGauge(player, 15);
                }
            }
            player.setData(ModAttachments.ROAM_KILLS.get(), roam);
        }
        // 遺物「魂喰いの爪」: 撃破ごとに魂+8
        if (RunManager.relicFlagsOf(player).contains("soulfang")) {
            RunManager.addGauge(player, 8);
        }
        // ドロップ: 6%で深淵武器 / 20%で魂の欠片. 幸運の運(+7/点)で武器率が上がり、
        // 魂の欠片は報酬倍率(呪印・難易度・契約)と幸運の素材倍率(+4%/点)を受ける
        var rand = player.getRandom();
        var mut = RunManager.currentMutation(player);
        double loot = mut != null ? mut.loot() : 1.0;
        var dun = RunManager.currentDungeon(player);
        if (dun != null) loot *= dun.loot();
        // fate「呪いの霧」: 戦利品2倍
        if (RunManager.hasFate(player, "mist")) loot *= 2;
        int luck = StatCalc.luckOf(player);
        // 原作: 敵ドロップ率 × 異変の loot 倍率 (豊穣の刻は1.85倍)
        double weaponChance = 0.06 * loot + GameData.luckDropBonus(luck);
        if (rand.nextDouble() < weaponChance) {
            // 原作 rollRarity: 運・層で上位が出やすい。武器65%・防具35%
            var drop = GearFactory.makeEquipment(rand, luck,
                    Math.max(1, player.getData(ModAttachments.FLOOR.get())), 0);
            giveOrDrop(player, drop);
            player.sendSystemMessage(Component.literal(
                    "§b戦利品: " + drop.getHoverName().getString()
                            + (ModItems.slotOf(drop.getItem()) != null ? " (右クリックで装備)" : " (持ち替えで奥義が変わる)")));
            if ("mythic".equals(GearData.rarityOf(drop))) {
                var a = GameData.achievementById("mythic-hunter");
                grantAch(player, "mythic-hunter", a.title(), GameData.shardsForReward(a.reward()));
            }
        } else if (rand.nextDouble() < 0.20) {
            int shards = RunManager.materialCount(player, 1.0);
            if (shards > 0) giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        } else if (rand.nextDouble() < 0.08) {
            giveOrDrop(player, ModItems.randomConsumable(rand));
        }
        // 満腹度: 深淵の討伐でも食料が手に入る (バニラの食料システムとの融合)
        // バニラのドロップ消去 (onMobDrops) で腹は満たせないためここで補う
        if (rand.nextDouble() < 0.12) {
            var foods = new net.minecraft.world.item.Item[]{
                    net.minecraft.world.item.Items.COOKED_BEEF,
                    net.minecraft.world.item.Items.BREAD,
                    net.minecraft.world.item.Items.COOKED_PORKCHOP,
                    net.minecraft.world.item.Items.COOKED_CHICKEN,
                    net.minecraft.world.item.Items.COOKED_MUTTON};
            giveOrDrop(player, new ItemStack(foods[rand.nextInt(foods.length)], 1 + rand.nextInt(2)));
        }
        // モブの固有能力 (死亡時): BURST=爆発 / SPLIT=子スライム
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob deadMob && deadMob.isDeadOrDying()) {
            String dmab = Enemies.mobAbility(deadMob);
            Vec3 dp = deadMob.position();
            if ("BURST".equals(dmab)) {
                WeaponArts.enemyPulse(level, dp, 3, Math.max(4, deadMob.getMaxHealth() * 0.08),
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            } else if ("SPLIT".equals(dmab) && rand.nextFloat() < 0.4f) {
                var child = ModEntities.SLIME.get().create(level,
                        net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (child instanceof net.minecraft.world.entity.Mob cm) {
                    int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
                    cm.setPos(dp.x + (rand.nextDouble() - 0.5) * 2, dp.y + 0.5, dp.z + (rand.nextDouble() - 0.5) * 2);
                    Enemies.apply(cm, Enemies.byId("slime"), Enemies.scaleFor(player, floor), 0.5);
                    Enemies.tagByRole(cm, Enemies.byId("slime"));
                    cm.setTarget(player);
                    level.addFreshEntity(cm);
                }
            }
        }
        // 素材ドロップ (原作 MATERIALS): 精鋭30%・番人80%・層主は虚空確定+追加
        var deadTags = event.getEntity().getTags();
        var deadNm = event.getEntity().getCustomName();
        boolean isElite = deadNm != null && deadNm.getString().contains("エリート");
        boolean isWarden = deadTags.contains(Enemies.WARDEN_TAG);
        boolean isBoss = RunManager.BOSS_BARS.containsKey(event.getEntity());
        if (isBoss) {
            giveOrDrop(player, new ItemStack(ModItems.VOID_ESSENCE.get(), 1));
            if (rand.nextFloat() < 0.5) giveOrDrop(player, randomMaterial(rand));
            giveOrDrop(player, randomMaterial(rand));
            giveOrDrop(player, randomMaterial(rand));
            // 奥義の断片: 層主は1個以上を必ず落とす (武器の決まった入手経路になる)
            int artShards = 1 + (rand.nextFloat() < 0.4 ? 1 : 0);
            for (int i = 0; i < artShards; i++) {
                giveOrDrop(player, ModItems.randomArtShard(rand));
            }
            player.sendSystemMessage(Component.literal("§5素材: 虚空のエッセンスと奥義の断片 ×" + artShards + " を得た"));
        } else if (isWarden && rand.nextFloat() < 0.8) {
            giveOrDrop(player, randomMaterial(rand));
            player.sendSystemMessage(Component.literal("§5素材: 番人の落とし物を得た"));
        } else if (isElite && rand.nextFloat() < 0.3) {
            giveOrDrop(player, randomMaterial(rand));
            eliteMaterialDrop(player, rand);
            if (rand.nextFloat() < 0.06) {
                var setDrop = GearFactory.makeSetArmor(null, null, rand,
                        StatCalc.luckOf(player),
                        Math.max(1, player.getData(ModAttachments.FLOOR.get())), 0);
                giveOrDrop(player, setDrop);
                player.sendSystemMessage(Component.literal(
                        "§b戦利品: " + setDrop.getHoverName().getString() + " (右クリックで装備)"));
            }
        }
        // レベルアップ: LvL→L+1 に (L-1)(L+4) 撃破
        int kills = player.getData(ModAttachments.KILLS.get());
        int lv = player.getData(ModAttachments.LEVEL.get());
        while (kills >= needFor(lv + 1)) {
            lv++;
            var cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
            StatCalc.applyClass(player, cls, lv);
            RunManager.heal(player, player.getMaxHealth() * 0.25);
            player.sendSystemMessage(Component.literal("§e◆ レベルアップ！ Lv" + lv + " (HP25%回復)"));
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.8f, 1.2f);
            if (lv >= 20) {
                var a = GameData.achievementById("level-twenty");
                grantAch(player, "level-twenty", a.title(), GameData.shardsForReward(a.reward()));
            }
            // 原作 engine.ts: レベル1につき祝福選択が1回 pend する (盟約モードでは無し)
            if (!RunManager.isCovenant(player)) {
                RunManager.addPendingBoons(player, 1);
            } else {
                player.sendSystemMessage(Component.literal("§5不幸の盟約: 祝福は得られない (魂×3.0で償う)"));
            }
        }
        // 未選択の祝福が残っていれば1度だけ提示 (既に提示中なら上書きしない)
        if (RunManager.pendingBoonsOf(player) > 0
                && player.getData(ModAttachments.OFFER.get()).isBlank()) {
            offerBoons(player);
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
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.ENDER_DRAGON_DEATH, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.4f, 1.2f);
            RunManager.addGauge(player, 30);
            // 層主撃破の実績 + 技能の3択 (原作: 主撃破ごとにパッシブ1つ。未選択があれば再提示)
            if (floor == 5) {
                var a = GameData.achievementById("floor-five");
                grantAch(player, "floor-five", a.title(), GameData.shardsForReward(a.reward()));
            }
            if (floor == 20) {
                var a = GameData.achievementById("floor-twenty");
                grantAch(player, "floor-twenty", a.title(), GameData.shardsForReward(a.reward()));
            }
            int bossCount = player.getData(ModAttachments.BOSSES.get()).split(",").length;
            if (bossCount >= 3) {
                var a = GameData.achievementById("boss-three");
                grantAch(player, "boss-three", a.title(), GameData.shardsForReward(a.reward()));
            }
            if (bossCount >= 5) {
                var a = GameData.achievementById("boss-five");
                grantAch(player, "boss-five", a.title(), GameData.shardsForReward(a.reward()));
            }
            String pendingPassive = player.getData(ModAttachments.PASSIVE_OFFER.get());
            if (pendingPassive == null || pendingPassive.isBlank()) {
                RunManager.offerPassives(player);
            } else {
                RunManager.resendPassiveOffer(player);
            }
        }
        // 武器特性: 吸血鬼 (撃破時HP3%回復) / 爆砕 (周囲爆発) / 血の契約 (祝福・撃破回復)
        String killTrait = GearData.traitOf(player.getMainHandItem());
        if ("vampiric".equals(killTrait)) {
            RunManager.heal(player, player.getMaxHealth() * 0.03);
        }
        // 武器スキル (撃破時) + 靴スキル (撃破で回避CTリセット)
        triggerWeaponSkill(player, level, event.getEntity());
        var boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if ("b_dash".equals(GearData.skillOf(boots))) {
            player.setData(ModAttachments.DASH_CD.get(), 0f);
        }
        if (RunManager.boonLv(player, "blood") > 0) {
            RunManager.heal(player, player.getMaxHealth() * 0.05 * RunManager.boonLv(player, "blood"));
        }
        // 恩寵「爆砕の刻印」・槌・魔導書・接辞: 撃破時に爆裂
        double explodeChance = RunManager.boonLv(player, "explode") * 0.18
                + StatCalc.wbonusOf(player).explodePct() / 100.0
                + (StatCalc.affixOf(player, "explodePct") + StatCalc.extraStat(player, "explodePct")) / 100.0
                + StatCalc.attrEff(player, 7) * 0.008;
        if (explodeChance > 0 && rand.nextFloat() < explodeChance) {
            WeaponArts.pulse(level, player, event.getEntity().position(), 4, 1.0, true,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
        // 恩寵「刈り取り」: 強敵 (精鋭・番人・層主) の撃破で全ステータス強化 (+2%攻撃/+1%HP・上限20)
        var deadName0 = event.getEntity().getCustomName();
        boolean strong = (deadName0 != null && deadName0.getString().contains("エリート"))
                || event.getEntity().getTags().contains(Enemies.WARDEN_TAG)
                || RunManager.BOSS_BARS.containsKey(event.getEntity());
        if (strong && RunManager.boonLv(player, "harvest") > 0
                && player.getData(ModAttachments.HARVEST.get()) < 20) {
            player.setData(ModAttachments.HARVEST.get(), player.getData(ModAttachments.HARVEST.get()) + 1);
            var clsH = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
            StatCalc.applyClass(player, clsH, player.getData(ModAttachments.LEVEL.get()));
            player.displayClientMessage(Component.literal("§a刈り取り！ 全ステータス強化 ("
                    + player.getData(ModAttachments.HARVEST.get()) + ")"), true);
        }
        // 鎌の収穫スタック: 鎌で撃破ごとに攻速+6% (最大10)。原作 scythe の収穫型ボーナス
        if (ModItems.styleOf(player.getMainHandItem().getItem()) == GameData.WeaponStyle.SCYTHE
                && player.getData(ModAttachments.SCYTHE.get()) < 10) {
            player.setData(ModAttachments.SCYTHE.get(), player.getData(ModAttachments.SCYTHE.get()) + 1);
            StatCalc.refreshAttackSpeed(player);
            player.displayClientMessage(Component.literal("§a収穫 +" +
                    player.getData(ModAttachments.SCYTHE.get()) + " (攻速上昇)"), true);
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
        if (player.getData(ModAttachments.KILLS.get()) >= 1000) {
            var a = GameData.achievementById("thousand-kills");
            grantAch(player, "thousand-kills", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.COMBO.get()) >= 50) {
            var a = GameData.achievementById("combo-fifty");
            grantAch(player, "combo-fifty", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.COMBO.get()) >= 100) {
            var a = GameData.achievementById("combo-hundred");
            grantAch(player, "combo-hundred", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.PERFECT.get()) >= 20) {
            var a = GameData.achievementById("perfect-twenty");
            grantAch(player, "perfect-twenty", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.HARVEST.get()) >= 10) {
            var a = GameData.achievementById("harvest-ten");
            grantAch(player, "harvest-ten", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.KILLS.get()) >= 5000) {
            var a = GameData.achievementById("kills-five-thousand");
            grantAch(player, "kills-five-thousand", a.title(), GameData.shardsForReward(a.reward()));
        }
        String bossRec = player.getData(ModAttachments.BOSSES.get());
        if (bossRec.contains("10")) grantAch(player, "floor-ten", "深層探査者", 10);
        // 真ボス・頂点ボス撃破判定
        var deadName = event.getEntity().getCustomName();
        if (deadName != null && deadName.getString().contains("ニヒル")) {
            RunManager.trueVictory(player);
        }
        if (deadName != null && deadName.getString().contains("タルタロス")) {
            RunManager.pinnacleVictory(player);
        }
        if (deadName != null && deadName.getString().contains("超越者")) {
            RunManager.ascensionVictory(player);
        }
        // 分裂スライム母: 死亡時に子を2体残す (原作 split)
        if (event.getEntity().getTags().contains(Enemies.SPLIT_TAG)
                && event.getEntity() instanceof net.minecraft.world.entity.Mob dead) {
            Enemies.handleSplit(level, dead, player);
        }
        // 精鋭「爆砕」: 死の間際、周囲を炸裂させる (原作 burst)
        if (event.getEntity().getTags().contains(Enemies.ET_BURST)) {
            var atk = event.getEntity().getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            double boom = atk != null ? atk.getValue() * 2 : 20;
            WeaponArts.enemyPulse(level, event.getEntity().position(), 5, boom,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
        // 精鋭撃破の10%で遺物の3択 (原作の遺物拾いに対応)
        if (deadName != null && deadName.getString().contains("エリート")
                && player.getRandom().nextFloat() < 0.10) {
            RunManager.offerRelics(player);
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
            player.sendSystemMessage(Component.literal("§a◆ 転移門が開いた！ ")
                    .append(Component.literal("§b[次の層へ進む]").withStyle(s -> s
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss next"))
                            .withUnderlined(true))));
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
            RunManager.offerEvent(player);
            // 層の締め前に未選択の祝福を確実に提示 (消える前に選ばせる)
            if (RunManager.pendingBoonsOf(player) > 0
                    && player.getData(ModAttachments.OFFER.get()).isBlank()) {
                player.sendSystemMessage(Component.literal(
                        "§e◆ レベルアップの祝福が未選択だ (" + RunManager.pendingBoonsOf(player) + "件)"));
                offerBoons(player);
            } else if (!player.getData(ModAttachments.OFFER.get()).isBlank()) {
                resendBoonOffer(player);
            }
        }
    }

    /* ---------------- 破壊物 ---------------- */

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 生活ツール能力: 対応ブロックで欠片・素材ボーナス
        triggerToolAbility(player, level, event.getPos());
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

    /** 生活ツール能力。手持ちの深淵ツールに応じた採集ボーナス */
    private static void triggerToolAbility(ServerPlayer player, ServerLevel level,
                                           net.minecraft.core.BlockPos pos) {
        var held = player.getMainHandItem().getItem();
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock()).getPath();
        var rand = player.getRandom();
        Runnable bonus = () -> {
            giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
            player.displayClientMessage(Component.literal("§b深淵の恵み: 魂の欠片+1"), true);
        };
        if (held == ModItems.ABYSS_PICKAXE.get()) {
            // 鉱石から12%で欠片 (採掘属性で+0.5%/点)
            if (path.contains("ore") && rand.nextDouble() < 0.12 + StatCalc.attrEff(player, 8) * 0.005) bonus.run();
        } else if (held == ModItems.ABYSS_TOOL_AXE.get()) {
            // 原木・幹から10%で欠片 (採掘属性で+0.5%/点)
            if ((path.contains("log") || path.contains("stem"))
                    && rand.nextDouble() < 0.10 + StatCalc.attrEff(player, 8) * 0.005) {
                bonus.run();
            }
        } else if (held == ModItems.ABYSS_SHOVEL.get()) {
            // 土・砂・砂利から8%で欠片 (採掘属性で+0.5%/点)
            if ((path.contains("dirt") || path.contains("sand") || path.contains("gravel"))
                    && rand.nextDouble() < 0.08 + StatCalc.attrEff(player, 8) * 0.005) {
                bonus.run();
            }
        } else if (held == ModItems.ABYSS_HOE.get()) {
            // 作物から25%で種+1
            if ((path.contains("wheat") || path.contains("carrot") || path.contains("potato")
                    || path.contains("beetroot") || path.contains("melon") || path.contains("pumpkin"))
                    && rand.nextDouble() < 0.25) {
                giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
            }
        } else if (held == ModItems.ABYSS_SHEARS.get()) {
            // 蜘蛛の巣から50%で糸+2、葉から10%で棒
            if (path.contains("cobweb") && rand.nextDouble() < 0.5) {
                giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.STRING, 2));
            } else if (path.contains("leaves") && rand.nextDouble() < 0.10) {
                giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.STICK));
            }
        }
    }

    /** 釣果2倍 (深淵の釣竿・30%で各ドロップ複製) */
    @SubscribeEvent
    public static void onFished(net.neoforged.neoforge.event.entity.player.ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().getItem() != ModItems.ABYSS_FISHING_ROD.get()
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).getItem()
                        != ModItems.ABYSS_FISHING_ROD.get()) return;
        if (player.getRandom().nextFloat() < 0.30f) {
            for (ItemStack s : java.util.List.copyOf(event.getDrops())) {
                event.getDrops().add(s.copy());
            }
            player.displayClientMessage(Component.literal("§b大漁！"), true);
        }
    }

    private static void triggerDestructible(ServerPlayer player, ServerLevel level, String kind,
                                            net.minecraft.core.BlockPos pos) {
        var at = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        switch (kind) {
            case "red" -> {
                // 赤水晶: 原作通り 攻撃×4.2 の爆発 (220px ≒ 半径4)。自分にも当たる
                WeaponArts.pulse(level, player, at, 4, 4.2, true,
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
                player.sendSystemMessage(Component.literal("§c赤水晶が爆発した！"));
            }
            case "blue" -> {
                // 青瓶: 原作通り 周囲の敵を凍結 (250px ≒ 半径5)。回復はしない
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        at.x, at.y + 1, at.z, 30, 2, 1.5, 2, 0.1);
                for (var e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(at.x - 5, at.y - 4, at.z - 5,
                                at.x + 5, at.y + 4, at.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4));
                    e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 1));
                }
                player.sendSystemMessage(Component.literal("§b青の霊薬！ 周囲の敵が凍結した"));
            }
            default -> {
                // 運命の祭壇: 原作の5 fate から1つ (rage/gold/haste/armor/scourge)
                String[] fates = {"rage", "gold", "haste", "armor", "scourge"};
                String fate = fates[player.getRandom().nextInt(fates.length)];
                int dur = fate.equals("scourge") ? 400 : fate.equals("gold") || fate.equals("haste") ? 600 : 500;
                RunManager.setShrine(player, fate, dur);
                String label = switch (fate) {
                    case "rage" -> "§c怒りの加護: 与ダメ+60%・被ダメ+20% (25秒)";
                    case "gold" -> "§6黄金の祝福: 素材+150%・最大HP-20 (30秒)";
                    case "haste" -> "§a風の加護: 攻速・移動が上昇 (30秒)";
                    case "armor" -> "§7大地の加護: 被ダメ-50%・回避不可 (25秒)";
                    default -> "§5深淵の試練: 毎秒2損耗・運+100 (20秒)";
                };
                player.sendSystemMessage(Component.literal("§e【運命の祭壇】" + label));
            }
        }
    }

    /** 素材の抽選 (古代50/混沌30/虚空20。原作 MATERIALS の重みに対応) */
    private static ItemStack randomMaterial(net.minecraft.util.RandomSource rand) {
        float r = rand.nextFloat();
        if (r < 0.12f) return new ItemStack(ModItems.VOID_ESSENCE.get());
        if (r < 0.30f) return new ItemStack(ModItems.CHAOS_GEM.get());
        if (r < 0.48f) return new ItemStack(ModItems.ANCIENT_STONE.get());
        var extra = new ItemStack[]{
                new ItemStack(ModItems.ABYSS_BLOOD_CRYSTAL.get()),
                new ItemStack(ModItems.ABYSS_SHADOW_STONE.get()),
                new ItemStack(ModItems.ABYSS_STAR_DUST.get()),
                new ItemStack(ModItems.ABYSS_WYRM_SCALE.get()),
                new ItemStack(ModItems.ABYSS_ANCIENT_CORE.get()),
                new ItemStack(ModItems.ABYSS_VOID_PIECE.get())};
        return extra[rand.nextInt(extra.length)];
    }

    /** 精鋭の追加ドロップ: 新素材1〜2個 (18%) */
    private static void eliteMaterialDrop(ServerPlayer player, net.minecraft.util.RandomSource rand) {
        if (rand.nextFloat() < 0.18f) {
            giveOrDrop(player, randomMaterial(rand));
            if (rand.nextFloat() < 0.4f) giveOrDrop(player, randomMaterial(rand));
        }
    }

    private static int needFor(int level) {
        return (level - 1) * (level + 4);
    }

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            var level = (ServerLevel) player.level();
            var e = new net.minecraft.world.entity.item.ItemEntity(
                    level, player.getX(), player.getY() + 1, player.getZ(), stack);
            level.addFreshEntity(e);
        }
    }

    /** 実績付与 (重複なし・欠片報酬)。レベルアップ音で確実な存在感を出す */
    public static void grantAch(ServerPlayer player, String id, String title, int shards) {
        String raw = player.getData(ModAttachments.ACH.get());
        var have = new java.util.HashSet<String>();
        if (raw != null && !raw.isBlank()) have.addAll(java.util.List.of(raw.split(",")));
        if (have.contains(id)) return;
        have.add(id);
        player.setData(ModAttachments.ACH.get(), String.join(",", have));
        player.sendSystemMessage(Component.literal("§e★ 実績解除: " + title + " (欠片+" + shards + ")"));
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS,
                0.7f, 1.5f);
        if (shards > 0) {
            giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        }
    }

    /* ---------------- 祝福3択 ---------------- */

    /**
     * 祝福3択の提示。原作 engine.ts drainRewards 相当:
     * 未選択の祝福 (PENDING_BOONS) がある間、1回ずつ選択画面 (GUI+チャット) で提示し、
     * 選ぶまで消えない (層移動などで消される前に PENDING_BOONS が保存される)。
     */
    public static void offerBoons(ServerPlayer player) {
        if (RunManager.isCovenant(player)) return;
        var rand = player.getRandom();
        var pick = Boons.rollBoons(rand::nextDouble, 3);
        var ids = new java.util.ArrayList<String>();
        var msg = Component.literal("§e◆ 祝福を選べ (画面のボタンかクリック): ");
        for (var b : pick) {
            ids.add(b.id());
            var btn = Component.literal("§6[" + b.name() + " " + b.desc() + "] ")
                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss boon " + b.id()))
                            .withUnderlined(true));
            msg.append(btn);
        }
        player.setData(ModAttachments.OFFER.get(), String.join(",", ids));
        player.sendSystemMessage(msg);
        presentBoonGui(player);
    }

    /** 未選択分から祝福3択を提示する (既に提示中なら何もしない)。盟約モードでは無視 */
    static void drainPendingBoons(ServerPlayer player) {
        if (RunManager.isCovenant(player)) return;
        if (RunManager.pendingBoonsOf(player) <= 0
                || !player.getData(ModAttachments.OFFER.get()).isBlank()) return;
        offerBoons(player);
    }

    /** 選択GUI + フラッシュHUDをクライアントへ送る */
    private static void presentBoonGui(ServerPlayer player) {
        String raw = player.getData(ModAttachments.OFFER.get());
        if (raw == null || raw.isBlank()) return;
        var ids = new java.util.ArrayList<String>(java.util.List.of(raw.split(",")));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ModPackets.BoonSync(ids, RunManager.pendingBoonsOf(player)));
        sendHud(player);
    }

    /** HUDの即時更新 (未選択カウントを出すため) */
    static void sendHud(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ModPackets.HudSync(
                        player.getData(ModAttachments.SOUL.get()),
                        player.getData(ModAttachments.FLOOR.get()),
                        player.getData(ModAttachments.LEVEL.get()),
                        player.getData(ModAttachments.KILLS.get()),
                        player.getData(ModAttachments.COMBO.get()),
                        StatCalc.attrPointsLeft(player),
                        player.getData(ModAttachments.PORTAL_OPEN.get()),
                        RunManager.pendingBoonsOf(player)));
    }

    /** 未選択の祝福を再提示 (上書きしない。GUIを開く・チャットにもボタンを出す) */
    public static void resendBoonOffer(ServerPlayer player) {
        String raw = player.getData(ModAttachments.OFFER.get());
        if (raw != null && !raw.isBlank()) {
            var msg = Component.literal("§e祝福を選べ (再提示): ");
            for (String id : raw.split(",")) {
                var b = Boons.boonById(id);
                if (b == null) continue;
                var btn = Component.literal("§6[" + b.name() + " " + b.desc() + "] ")
                        .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss boon " + b.id()))
                                .withUnderlined(true));
                msg.append(btn);
            }
            player.sendSystemMessage(msg);
            presentBoonGui(player);
            return;
        }
        // 提示分が無い場合は未選択残数から再ロール (startRun・startTrue で OFFER を消去済みでも復元できる)
        if (RunManager.pendingBoonsOf(player) > 0 && !RunManager.isCovenant(player)) {
            offerBoons(player);
            return;
        }
        player.sendSystemMessage(Component.literal("§7選択待ちの祝福は無い"));
    }

    /**
     * 祝福の決定 (GUI・コマンド共通)。提示中の3択からの選択のみ有効。
     * 決定で未選択カウントを消費し、まだ残っていれば次の3択を提示する (原作 drainRewards の逐次開示)。
     */
    public static boolean chooseBoon(ServerPlayer player, String id) {
        var def = Boons.boonById(id);
        if (def == null) return false;
        String offer = player.getData(ModAttachments.OFFER.get());
        if (offer == null || !java.util.List.of(offer.split(",")).contains(id)) {
            player.sendSystemMessage(Component.literal("§c今はその祝福を選べない (レベルアップ時の3択から選んでね)"));
            return false;
        }
        RunManager.addBoon(player, id);
        player.setData(ModAttachments.OFFER.get(), "");
        int left = Math.max(0, RunManager.pendingBoonsOf(player) - 1);
        RunManager.setPendingBoons(player, left);
        GameData.HeroClass cls = GameData.HeroClass.byId(
                player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()));
        player.sendSystemMessage(Component.literal(
                "§e祝福を得た: " + def.name() + " (" + def.desc() + ")"
                        + (left > 0 ? " — 残り" + left + "件の選択待ち" : "")));
        sendHud(player);
        if (left > 0) drainPendingBoons(player);
        return true;
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
        // エリート「魔盾」: 盾持ちの被ダメ軽減 (原作のシールドエリート)
        if (event.getEntity().getTags().contains(Enemies.ET_SHIELD)
                && !(event.getEntity() instanceof ServerPlayer)) {
            event.setNewDamage(event.getNewDamage() * 0.7f);
        }
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            float iframe = player.getData(ModAttachments.IFRAME.get());
            if (iframe > 0) {
                event.setNewDamage(0f);
                // 見切り: 無敵中に受けた攻撃は魂+14 (兜の直感で+6)
                RunManager.addGauge(player, 14);
                if (ModItems.slotOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()) != null) {
                    RunManager.addGauge(player, 6);
                }
                int perfect = player.getData(ModAttachments.PERFECT.get()) + 1;
                player.setData(ModAttachments.PERFECT.get(), perfect);
                if (perfect >= 10) {
                    var a = GameData.achievementById("perfect-ten");
                    grantAch(player, "perfect-ten", a.title(), GameData.shardsForReward(a.reward()));
                }
                // 見切りの成立時刻 (恩寵「完全主義」の3秒窓)
                player.setData(ModAttachments.PARRY_TICK.get(), player.level().getGameTime());
                // 遺物「嵐召喚者の冠」: 見切りのたび落雷が3体を焼く
                if (RunManager.relicFlagsOf(player).contains("storm")) {
                    stormStrike(level, player);
                }
                player.displayClientMessage(Component.literal("§a見切り！"), true);
                return;
            }
            // 鉄壁の刻印: 被ダメ軽減 (上限50%)
            int armor = RunManager.boonLv(player, "armor");
            if (armor > 0) {
                event.setNewDamage(event.getNewDamage() * (float) Math.max(0.5, 1 - 0.06 * armor));
            }
            // 呪い武器 (armorPct): 防御低下 (呪喰らいで無効化)
            var heldDef = player.getMainHandItem();
            if (GearData.curseActive(player, heldDef) && "armorPct".equals(GearData.curseOf(heldDef))) {
                event.setNewDamage(event.getNewDamage()
                        * (float) (1 - GearData.curseValueOf(heldDef) / 100.0));
            }
            // 接辞 (armorPct): 被ダメ軽減
            double affixArmor = StatCalc.affixOf(player, "armorPct")
                    + StatCalc.extraStat(player, "armorPct") + StatCalc.attrEff(player, 6) * 0.5;
            if (affixArmor != 0) {
                event.setNewDamage(event.getNewDamage()
                        * (float) Math.max(0.3, 1 - affixArmor / 100.0));
            }
            // 防具スキル: 荊棘の鎧 (反射20%) / 落法 (落下半減)
            var chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if ("c_thorns".equals(GearData.skillOf(chest))
                    && event.getSource().getEntity() instanceof LivingEntity atk && atk.isAlive()) {
                atk.hurtServer(level, level.damageSources().magic(), event.getNewDamage() * 0.2f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        atk.getX(), atk.getY() + 1, atk.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
            }
            // 防具スキル「霜の鎧」: 被弾時に周囲を凍結
            if ("c_frost".equals(GearData.skillOf(chest))) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(4),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getY() + 1, player.getZ(), 12, 1.2, 0.8, 1.2, 0.1);
            }
            // 防具スキル「魂導の鎧」: 被弾で魂ゲージ+4
            if ("c_soul".equals(GearData.skillOf(chest))) {
                RunManager.addGauge(player, 4);
            }
            // 防具スキル「鉄壁の鎧」: 被ダメ追加軽減 -8%
            if ("c_guard".equals(GearData.skillOf(chest))) {
                event.setNewDamage(event.getNewDamage() * 0.92f);
            }
            var legsF = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            if ("l_rock".equals(GearData.skillOf(legsF))
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage() * 0.2f);
            }
            // 全武器の固有能力 (GUARD系は被弾時反射)
            var gheld = player.getMainHandItem();
            var gstyle = ModItems.styleOf(gheld.getItem());
            var gab = gstyle != null ? GameData.WEAPON_ABILITIES.get(gstyle.id) : null;
            if (gab != null && "GUARD".equals(gab.kind())
                    && player.getRandom().nextFloat() < gab.chance()
                    && event.getSource().getEntity() instanceof LivingEntity gatk && gatk.isAlive()) {
                gatk.hurtServer(level, level.damageSources().magic(),
                        event.getNewDamage() * (float) gab.value());
            }
            var legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            if ("l_leap".equals(GearData.skillOf(legs))
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage() * 0.5f);
            }
            // 防具スロット能力: 鎧=重装 (被ダメ-5%) / 脚=軽業 (落下-25%)
            if (ModItems.slotOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()) != null) {
                event.setNewDamage(event.getNewDamage() * 0.95f);
            }
            if (ModItems.slotOf(legs.getItem()) != null
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage() * 0.75f);
            }
            // 跳躍属性: 落下軽減 (最大-40%)
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage()
                        * (float) (1 - Math.min(0.4, StatCalc.attrEff(player, 9) * 0.02)));
            }
            // 恩寵「障壁の刻印」: 立ち止まると軽減が上がる (動いていなければ +8%/Lv)
            if (RunManager.boonLv(player, "warden") > 0
                    && player.getDeltaMovement().horizontalDistanceSqr() < 0.0015) {
                event.setNewDamage(event.getNewDamage()
                        * (float) Math.max(0.4, 1 - 0.08 * RunManager.boonLv(player, "warden")));
            }
            // 恩寵「強欲の刻印」: 被ダメ+10%/Lv
            int greedLv = RunManager.boonLv(player, "greed");
            if (greedLv > 0) {
                event.setNewDamage(event.getNewDamage() * (float) (1 + 0.10 * greedLv));
            }
            // 弾幕の獄: 被弾は軽い (敵の弾が濃いかわりに)
            if ("danmaku".equals(player.getData(ModAttachments.MODE.get()))) {
                event.setNewDamage(event.getNewDamage() * 0.6f);
            }
            // 技能の被ダメ補正 (原作 passiveMods 対応分)
            var passives = RunManager.passivesOf(player);
            double armorPct = GameData.passiveArmorPct(passives);
            if (passives.contains("last_stand")
                    && player.getHealth() / Math.max(1, player.getMaxHealth()) < 0.3) {
                armorPct += 25;
            }
            if (armorPct != 0) {
                event.setNewDamage(event.getNewDamage() * (float) Math.max(0.3, 1 - armorPct / 100.0));
            }
            // 技能「勢い」: コンボが切れると被ダメ+15%
            if (passives.contains("momentum") && player.getData(ModAttachments.COMBO.get()) <= 0) {
                event.setNewDamage(event.getNewDamage() * 1.15f);
            }
            // 不壊: 命中から10秒は被ダメ-10%
            if (player.getData(ModAttachments.FORTIFY.get()) > 0) {
                event.setNewDamage(event.getNewDamage() * 0.9f);
            }
            // fate「呪いの霧」: 被ダメ+30%
            if (RunManager.hasFate(player, "mist")) {
                event.setNewDamage(event.getNewDamage() * 1.3f);
            }
            // 祭壇「怒り」: 被ダメ+20% / 「大地」: 被ダメ-50%
            String shrine = RunManager.activeShrine(player);
            if ("rage".equals(shrine)) event.setNewDamage(event.getNewDamage() * 1.2f);
            if ("armor".equals(shrine)) event.setNewDamage(event.getNewDamage() * 0.5f);
            // 致死回避: 不滅の誓い (確定・1ラン1回) / 呪われた鏡 (40%・1ラン1回)
            if (event.getNewDamage() >= player.getHealth() && player.getHealth() > 0
                    && player.getData(ModAttachments.MIRROR_USED.get()) == 0) {
                boolean undying = passives.contains("undying");
                boolean mirror = RunManager.relicFlagsOf(player).contains("mirror");
                if (undying || (mirror && player.getRandom().nextFloat() < 0.40)) {
                    player.setData(ModAttachments.MIRROR_USED.get(), 1);
                    event.setNewDamage(0f);
                    player.setHealth(player.getMaxHealth() * 0.5f);
                    player.displayClientMessage(Component.literal(
                            undying ? "§f不滅の誓いが発動！ HP50%で復活" : "§b呪われた鏡が身代わりになった！"), false);
                    return;
                }
            }
            // 技能「棘の王」: 棘+80を反射
            if (passives.contains("thorn_lord") && event.getSource().getEntity() instanceof LivingEntity atk) {
                WeaponArts.artHurt((ServerLevel) player.level(), atk,
                        player.level().damageSources().playerAttack(player), 80f);
            }
            // 精鋭「吸魂」: 攻撃命中で自身のHPを吸収する
            if (event.getSource().getEntity() instanceof LivingEntity leech
                    && leech.getTags().contains(Enemies.ET_VAMPIRE)) {
                leech.heal(Math.min(event.getNewDamage() * 0.5f, leech.getMaxHealth() * 0.1f));
            }
            // 恩寵「復讐の刻印」: 被弾時に周囲へ爆発
            int vengeance = RunManager.boonLv(player, "vengeance");
            if (vengeance > 0 && event.getNewDamage() > 0) {
                WeaponArts.pulse(level, player, player.position(), 4, 0.5 * vengeance, true,
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            }
            // 恩寵「棘甲の刻印」: 棘ダメージ+25/Lv (攻撃者へ反射)
            int thorns = RunManager.boonLv(player, "thorns");
            if (thorns > 0 && event.getSource().getEntity() instanceof LivingEntity atk) {
                WeaponArts.artHurt(level, atk,
                        level.damageSources().playerAttack(player), 25f * thorns);
            }
            // 重圧の層: 被ダメ+15%
            // 被ダメ由来の奥義ゲージ (原作: 残HPまでの被害 / 最大HP * 35)
            float taken = Math.min(event.getNewDamage(), player.getHealth());
            RunManager.addGauge(player, GameData.damageTakenCharge(taken, player.getMaxHealth()));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            // 奥義・罠・二次反応の命中は威力を上書きせず、ゲージも増やさない
            if (WeaponArts.inArtHit()) {
                // スコア用: 奥義等の与ダメも累積する
                player.setData(ModAttachments.DMG_DEALT.get(),
                        player.getData(ModAttachments.DMG_DEALT.get()) + (int) event.getNewDamage());
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (ModItems.styleOf(held.getItem()) != null) {
                double atk = StatCalc.attackDamageOf(player);
                boolean crit = player.getRandom().nextFloat() < StatCalc.critOf(player);
                // 致命の刻印: 会心威力 +45%/Lv
                float dmg = (float) (atk * (crit ? StatCalc.critDamageMult(player) : 1.0));
                // レアリティ倍率
                dmg *= (float) GearData.rarityMult(GearData.rarityOf(held));
                // 魔弾 (ロッド・ワンドの自動射撃): 最終威力に倍率
                if (boltMult > 0) dmg *= (float) boltMult;
                String trait = GearData.traitOf(held);
                // 反撃窓: 回避直後の初撃×1.5 (技能「完全主義」は×1.9・原作カウンター)
                if (player.getData(ModAttachments.COUNTER.get()) > 0) {
                    boolean perfection = RunManager.passivesOf(player).contains("perfection");
                    dmg *= perfection ? 1.9f : 1.5f;
                    player.setData(ModAttachments.COUNTER.get(), 0f);
                    player.displayClientMessage(Component.literal("§e反撃！"), true);
                }
                LivingEntity target = event.getEntity();
                if ("sharp".equals(trait)) dmg *= 1.08f;
                if ("terminator".equals(trait)
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= 1.5f;
                }
                // 祝福「処刑人」: 瀕死の敵へ+90%
                if (RunManager.boonLv(player, "execute") > 0
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= 1.9f;
                }
                // 全武器の固有能力 (EXECUTE系はここで威力に反映)
                var wstyle = ModItems.styleOf(held.getItem());
                var wab = wstyle != null ? GameData.WEAPON_ABILITIES.get(wstyle.id) : null;
                if (wab != null && "EXECUTE".equals(wab.kind())
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= (float) (1 + wab.value());
                }
                if ("frenzy".equals(trait) && player.getData(ModAttachments.COMBO.get()) >= 30) {
                    dmg *= 1.25f;
                }
                // 技能「巨人殺し」: エリート・層主へ+45%、雑魚へ-15%
                var pflags = RunManager.passiveFlagsOf(player);
                if (pflags.contains("giant_slayer")) {
                    boolean big = target.getTags().contains(Enemies.WARDEN_TAG)
                            || RunManager.BOSS_BARS.containsKey(target)
                            || (target.getCustomName() != null
                                    && target.getCustomName().getString().contains("エリート"));
                    dmg *= big ? 1.45f : 0.85f;
                }
                // 技能「群れの克星」: 標的の周囲8mに3体以上いれば+35%
                if (pflags.contains("swarm_bane")) {
                    var tp = target.position();
                    long near = level.getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(tp.x - 8, tp.y - 4, tp.z - 8,
                                    tp.x + 8, tp.y + 4, tp.z + 8),
                            e -> e.isAlive() && !(e instanceof ServerPlayer)).size();
                    if (near >= 3) dmg *= 1.35f;
                }
                event.setNewDamage(dmg);
                // 一打の連撃・横斬り (通常武器命中のみ。奥義ヒット側は inArtHit で対象外)
                applyShapeStrikes(player, level, target, dmg);
                // 武器ごとの左クリック追撃 (N打ごとの固有ボーナス)
                var styleOfHit = ModItems.styleOf(held.getItem());
                if (styleOfHit != null) {
                    applyFollowup(player, level, target, dmg, styleOfHit.id);
                }
                // スコア用: 累積与ダメ (切り捨て)
                player.setData(ModAttachments.DMG_DEALT.get(),
                        player.getData(ModAttachments.DMG_DEALT.get()) + (int) dmg);
                // 与ダメ由来の奥義ゲージ (残HPまで=オーバーキル除外)
                float actual = Math.min(dmg, target.getHealth());
                RunManager.addGauge(player, GameData.artChargeGain(actual, atk, false));
                // 吸血の刻印 (祝福) / 血の盟約 (技能・+6%/自然回復喪失は回復量で近似) / 接辞
                int life = RunManager.boonLv(player, "life");
                if (life > 0 && dmg > 0) RunManager.heal(player, dmg * 0.04 * life);
                double affixLeech = StatCalc.affixOf(player, "lifestealPct")
                        + StatCalc.extraStat(player, "lifestealPct");
                if (affixLeech > 0 && dmg > 0) RunManager.heal(player, dmg * affixLeech / 100.0);
                if (RunManager.passivesOf(player).contains("blood_pact") && dmg > 0) {
                    RunManager.heal(player, dmg * 0.06);
                }
                // 不壊の付与
                if ("fortify".equals(trait)) player.setData(ModAttachments.FORTIFY.get(), 10f);
                // 状態異常 (業火/猛毒/裂傷/業火の刻/氷縛の理)
                var rand = player.getRandom();
                if ("infernus".equals(trait) && rand.nextFloat() < 0.15f) {
                    target.setRemainingFireTicks(60);
                }
                if (RunManager.passivesOf(player).contains("slow_burn") && rand.nextFloat() < 0.35f) {
                    target.setRemainingFireTicks(40);
                }
                // 恩寵「凍牙の刻印」・接辞: 命中時に氷結鈍足
                double freezeChance = RunManager.boonLv(player, "freeze") * 0.20
                        + (StatCalc.affixOf(player, "freezePct") + StatCalc.extraStat(player, "freezePct")) / 100.0;
                if (freezeChance > 0 && rand.nextFloat() < freezeChance) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 0));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                            target.getX(), target.getY() + 1, target.getZ(), 8, 0.4, 0.6, 0.4, 0.05);
                }
                if (RunManager.passivesOf(player).contains("frostbound") && rand.nextFloat() < 0.40f) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                }
                if ("venomous".equals(trait) && rand.nextFloat() < 0.12f) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                }
                if ("rend".equals(trait) && rand.nextFloat() < 0.10f) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
                // 連鎖雷 (武器特性 / 祝福): 他の敵へ飛び移る
                int chainLv = RunManager.boonLv(player, "chain");
                boolean weaponChain = "chain_l".equals(trait) || StatCalc.affixOf(player, "chain") > 0
                        || StatCalc.extraStat(player, "chain") > 0;
                if ((weaponChain || chainLv > 0) && rand.nextFloat() < (weaponChain ? 1f : 0.25f)) {
                    float chainDmg = (float) (atk * (weaponChain ? 0.5 : 0.6));
                    // 貫通の刻印・弓・輪刃: 飛移り +2/個
                    int jumps = 2 + RunManager.boonLv(player, "pierce") * 2
                            + StatCalc.wbonusOf(player).pierce() * 2
                            + (int) (StatCalc.affixOf(player, "pierce") + StatCalc.extraStat(player, "pierce")) * 2;
                    chainStrike(level, player, target, chainDmg, jumps);
                }
                // 恩寵「渦刃」: 命中のたびに刃の渦が飛ぶ (周囲に小斬撃)
                if (RunManager.boonLv(player, "bladespin") > 0) {
                    WeaponArts.pulse(level, player, target.position(), 3, 0.35, false,
                            net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
                }
                // 恩寵「天雷の刻印」: 命中時に8%/Lvで落雷
                int thunder = RunManager.boonLv(player, "thunderfury");
                if (thunder > 0 && rand.nextFloat() < 0.08 * thunder) {
                    thunderStrike(level, player, target);
                }
                // 技能「残響の型」: 15%でフィニッシャーが再放される
                if (RunManager.passiveFlagsOf(player).contains("echo_strike")
                        && rand.nextFloat() < 0.15f) {
                    WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player), dmg);
                }
                // 拳套: 3連撃ごとに衝撃波 (原作 gauntlet の連打型ボーナス)
                if (ModItems.styleOf(player.getMainHandItem().getItem()) == GameData.WeaponStyle.GAUNTLET) {
                    int hits = player.getData(ModAttachments.GCNT.get()) + 1;
                    player.setData(ModAttachments.GCNT.get(), hits);
                    if (hits % 3 == 0) {
                        WeaponArts.pulse(level, player, target.position(), 3, 1.0, true,
                                net.minecraft.core.particles.ParticleTypes.CRIT);
                    }
                }
                // 全武器の固有能力 (確率発動系)
                if (wab != null) {
                    switch (wab.kind()) {
                        case "CHAIN" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                chainStrike(level, player, target, (float) (atk * wab.value()), 2);
                            }
                        }
                        case "FROST" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                                WeaponArts.artHurt(level, target,
                                        level.damageSources().playerAttack(player),
                                        (float) (atk * wab.value()));
                                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                                        target.getX(), target.getY() + 1, target.getZ(),
                                        8, 0.4, 0.6, 0.4, 0.05);
                            }
                        }
                        case "SOUL" -> {
                            if (rand.nextFloat() < wab.chance()) RunManager.addGauge(player, wab.value());
                        }
                        case "BURST" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                WeaponArts.pulse(level, player, target.position(), 3, wab.value(), false,
                                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
                            }
                        }
                        case "POISON" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                                        target.getX(), target.getY() + 1, target.getZ(), 6, 0.4, 0.6, 0.4, 0.05);
                            }
                        }
                        default -> {
                        }
                    }
                }
                // モブの固有能力 (被弾時): THORNS=反射 / WARP=転移
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    String mab = Enemies.mobAbility(mob);
                    if ("THORNS".equals(mab)) {
                        player.hurtServer(level, level.damageSources().magic(), dmg * 0.15f);
                    } else if ("WARP".equals(mab) && rand.nextFloat() < 0.3f) {
                        Vec3 mp = mob.position();
                        mob.teleportTo(mp.x + (rand.nextDouble() - 0.5) * 10, mp.y + 1,
                                mp.z + (rand.nextDouble() - 0.5) * 10);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                                mp.x, mp.y + 1, mp.z, 20, 1, 1, 1, 0.3);
                    }
                }
            }
        }
        // モブの固有能力 (加害時): LEECH=吸収 / SNARE=鈍足
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.Mob m
                && event.getEntity() instanceof ServerPlayer p2
                && p2.level() instanceof ServerLevel lv2 && event.getNewDamage() > 0) {
            String mab2 = Enemies.mobAbility(m);
            if ("LEECH".equals(mab2)) {
                m.heal(event.getNewDamage() * 0.1f);
            } else if ("SNARE".equals(mab2) && p2.getRandom().nextFloat() < 0.25f) {
                p2.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
            }
        }
    }

    /**
     * 通常武器命中に付随する「一打の連撃」と「横斬り」.
     * 連撃: 標的以外の近接を strikes 体まで同時に 45% で打つ (artHurt 経由で inArtHit ガードが回帰を遮断).
     * 横斬り: 前方 sweepArc 以内の別敵に 40% の薙ぎ (奥義の slashCone とは別物で通常打に付随).
     */
    private static void applyShapeStrikes(ServerPlayer player, ServerLevel level,
                                          LivingEntity target, float dmg) {
        Vec3 look = player.getLookAngle();
        double reach = 4 + StatCalc.reachOf(player);
        int strikes = StatCalc.strikesOf(player);
        if (strikes > 0) {
            var cands = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(
                            target.position().x - reach, target.position().y - 4, target.position().z - reach,
                            target.position().x + reach, target.position().y + 4, target.position().z + reach),
                    e -> e.isAlive() && e != target && !(e instanceof ServerPlayer));
            cands.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(target.position())));
            int n = 0;
            for (LivingEntity e : cands) {
                if (n++ >= strikes) break;
                WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg * 0.45f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.5, 0.3, 0.1);
            }
        }
        double sweep = StatCalc.sweepOf(player);
        if (sweep > 0) {
            double arc = StatCalc.sweepArc(player);
            double baseAng = Math.atan2(look.z, look.x);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(
                            player.getX() - reach, player.getY() - 4, player.getZ() - reach,
                            player.getX() + reach, player.getY() + 4, player.getZ() + reach),
                    x -> x.isAlive() && x != target && !(x instanceof ServerPlayer))) {
                Vec3 d = e.position().subtract(player.position());
                if (d.length() > reach) continue;
                double diff = Math.abs(normAngle(Math.atan2(d.z, d.x) - baseAng));
                if (diff < arc) {
                    WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg * 0.4f);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                            e.getX(), e.getY() + 1, e.getZ(), 4, 0.3, 0.4, 0.3, 0.05);
                }
            }
        }
    }

    /** 角度差の正規化 (-PI..PI) */
    private static double normAngle(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /**
     * 武器の左クリック追撃 (GameData.WEAPON_FOLLOWUP)。
     * FOLLOW_HIT 添付で通常攻撃をカウントし、規定回数に達するたびに追加効果を発火する。
     * artHurt 経由なので inArtHit ガードが再帰を遮断する。
     */
    private static void applyFollowup(ServerPlayer player, ServerLevel level,
                                      LivingEntity target, float dmg, String styleId) {
        String kind = GameData.WEAPON_FOLLOWUP.get(styleId);
        if (kind == null) return;
        int hits = player.getData(ModAttachments.FOLLOW_HIT.get()) + 1;
        player.setData(ModAttachments.FOLLOW_HIT.get(), hits);
        Vec3 look = player.getLookAngle();
        switch (kind) {
            case "pierce3" -> {
                if (hits % 3 != 0) return;
                Vec3 at = player.position().add(look.scale(4));
                WeaponArts.pulse(level, player, at, 2.5, 0.7, false, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            }
            case "burst4" -> {
                if (hits % 4 != 0) return;
                WeaponArts.pulse(level, player, target.position(), 3, 0.8, true, net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            }
            case "backflip4" -> {
                if (hits % 4 != 0) return;
                player.push(-look.x * 0.9, 0.5, -look.z * 0.9);
                RunManager.addGauge(player, 2);
                player.displayClientMessage(Component.literal("§7跳ね返った"), true);
            }
            case "soul3" -> {
                if (hits % 3 != 0) return;
                RunManager.addGauge(player, 3);
            }
            case "swipe5" -> {
                if (hits % 5 != 0) return;
                WeaponArts.pulse(level, player, player.position().add(look.scale(3)), 4, 0.9, false,
                        net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
            }
            case "thorn3" -> {
                if (hits % 3 != 0) return;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(3),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    Vec3 kb = e.position().subtract(player.position()).normalize().scale(1.4);
                    e.push(kb.x, 0.4, kb.z);
                }
            }
            case "heal6" -> {
                if (hits % 6 != 0) return;
                RunManager.heal(player, 3);
            }
            case "crit3" -> {
                if (hits % 3 != 0) return;
                WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player), dmg * 0.5f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        target.getX(), target.getY() + 1, target.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
            }
            default -> { }
        }
    }

    /** 遺物「嵐召喚者の冠」: 見切りのたび、空から落雷が3体の敵を焼く */    private static void stormStrike(ServerLevel level, ServerPlayer player) {
        var pos = player.position();
        var targets = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos.x - 16, pos.y - 6, pos.z - 16,
                        pos.x + 16, pos.y + 6, pos.z + 16),
                e -> e.isAlive() && !(e instanceof ServerPlayer));
        targets.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
        int n = 0;
        for (var e : targets) {
            if (n++ >= 3) break;
            WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player),
                    (float) StatCalc.attackDamageOf(player));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    e.getX(), e.getY() + 1, e.getZ(), 16, 0.4, 0.8, 0.4, 0.1);
        }
        if (n > 0) {
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }

    /** 恩寵「天雷の刻印」: 1体に強めの落雷 */
    private static void thunderStrike(ServerLevel level, ServerPlayer player, LivingEntity target) {
        var pos = target.position();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y + 1, pos.z, 24, 0.4, 1, 0.4, 0.15);
        WeaponArts.artHurt(level, target,
                level.damageSources().playerAttack(player),
                (float) (StatCalc.attackDamageOf(player) * 1.5));
        level.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.0f);
    }

    /** 連鎖雷撃: 目標以外の最寄り n 体へ飛び移る */
    private static void chainStrike(ServerLevel level, ServerPlayer player, LivingEntity from, float dmg, int n) {
        var pos = from.position();
        var others = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos.x - 8, pos.y - 4, pos.z - 8, pos.x + 8, pos.y + 4, pos.z + 8),
                e -> e.isAlive() && e != from && !(e instanceof ServerPlayer));
        others.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
        int count = 0;
        for (var e : others) {
            if (count++ >= Math.max(1, n)) break;
            WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    e.getX(), e.getY() + 1, e.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
        }
        if (count > 0) {
            level.playSound(null, from.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    /** 右クリック=武器小技 / スニーク+右クリック=必殺技 / 魂の欠片=魂+25 */
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
        // 武器を持ち替えたらその武器種の小技になる
        if (player.isShiftKeyDown()) {
            trySkill(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (tryLightSkill(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 武器小技の発動 (右クリック共通。CT1~3秒で連発できる) */
    public static boolean tryLightSkill(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel)) return false;
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        if (style == null) {
            player.displayClientMessage(Component.literal("§cAbyss武器を手に持ってね"), true);
            return false;
        }
        float cd = player.getData(ModAttachments.LIGHT_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7小技CT %.1fs", cd)), true);
            return false;
        }
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        WeaponArts.executeLight(player, style, StatCalc.attackDamageOf(player));
        player.setData(ModAttachments.LIGHT_CD.get(),
                (float) GameData.lightCooldown(cls, style));
        return true;
    }

    /** 武器奥義の発動判定 (Rキー / コマンド共通) */
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
                StatCalc.attackDamageOf(player) * StatCalc.artMultOf(player)
                        * GameData.skillTierMult(player.getData(ModAttachments.LEVEL.get())))) {
            // 恩寵「残響の刻印」: 奥義後に25%/Lvで魂ゲージが半分残る
            if (player.getRandom().nextFloat() < 0.25 * RunManager.boonLv(player, "echo")) {
                player.setData(ModAttachments.SOUL.get(), 50f);
                player.displayClientMessage(Component.literal("§d残響！ 魂ゲージが半分残った"), true);
            } else {
                player.setData(ModAttachments.SOUL.get(), 0f);
            }
            int uses = player.getData(ModAttachments.ART_USES.get()) + 1;
            player.setData(ModAttachments.ART_USES.get(), uses);
            String artTier = GameData.skillTierName(player.getData(ModAttachments.LEVEL.get()));
            if (!artTier.isEmpty()) {
                player.displayClientMessage(Component.literal("§d奥義解放" + artTier + "！"), true);
            }
            if (uses >= 10) {
                var a = GameData.achievementById("art-master");
                grantAch(player, "art-master", a.title(), GameData.shardsForReward(a.reward()));
            }
            return true;
        }
        return false;
    }

    /** 必殺技の発動 (スニーク+右クリック / Vキー / コマンド共通。CT25秒) */
    public static void trySkill(ServerPlayer player) {
        float cd = player.getData(ModAttachments.SKILL_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7必殺技CT %.1fs", cd)), true);
            return;
        }
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int lv = player.getData(ModAttachments.LEVEL.get());
        WeaponArts.executeSkill(player, cls,
                StatCalc.attackDamageOf(player) * StatCalc.artMultOf(player)
                        * GameData.skillTierMult(lv));
        player.setData(ModAttachments.SKILL_CD.get(), 25f);
        String tierName = GameData.skillTierName(lv);
        if (!tierName.isEmpty()) {
            player.displayClientMessage(Component.literal("§d必殺技解放" + tierName + "！"), true);
        }
    }

    /** 無敵・ダッシュCT・反撃窓の減衰 + ボスバー更新 + 罠 */
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        tickCounter++;
        // 遅延タスク (絶対tick到達で発火・除去)
        if (!PENDING.isEmpty()) {
            var done = new java.util.ArrayList<DelayTask>();
            synchronized (PENDING) {
                for (DelayTask d : PENDING) {
                    if (d.targetTick() <= tickCounter) done.add(d);
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
        EnemyAI.tickGuards(server, tickCounter);
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
            float skillCd = player.getData(ModAttachments.SKILL_CD.get());
            if (skillCd > 0) player.setData(ModAttachments.SKILL_CD.get(), Math.max(0, skillCd - 0.05f));
            float lightCd = player.getData(ModAttachments.LIGHT_CD.get());
            if (lightCd > 0) player.setData(ModAttachments.LIGHT_CD.get(), Math.max(0, lightCd - 0.05f));
            if (tickCounter % 10 == 0) ArenaManager.tickTraps(player);
            if (tickCounter % 20 == 0) tickAbilityBoons(player);
            if (tickCounter % 20 == 0) RunManager.tickRunEffects(player);
            // コンボ減衰 (5秒撃破なしでリセット・原作comboT準拠)
            Long last = LAST_KILL_TICK.get(player.getUUID());
            if (player.getData(ModAttachments.COMBO.get()) > 0
                    && (last == null || tickCounter - last > 100)) {
                player.setData(ModAttachments.COMBO.get(), 0);
            }
            if (tickCounter % 20 == 0) {
                sendHud(player);
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
                    RunManager.tickPinnacleBoss(sl, boss);
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
        // 魔術武器 (ロッド・ワンド) の自動魔弾 (1.5秒ごと)。銃魔は弾数ボーナスで多重化
        if (tickCounter % 30 == 0) {
            var style = ModItems.styleOf(player.getMainHandItem().getItem());
            if (style == GameData.WeaponStyle.ROD) {
                int targets = 1 + StatCalc.wbonusOf(player).projectiles()
                        + (int) (StatCalc.affixOf(player, "projectiles")
                                + StatCalc.extraStat(player, "projectiles"));
                castWandBolt(player, 12, 0.7, targets);
            } else if (style == GameData.WeaponStyle.WAND) {
                castWandBolt(player, 16, 0.9, 1);
            }
        }
        if (RunManager.boonLv(player, "orbit") > 0) {
            WeaponArts.pulse(level, player, pos, 3.5, 0.8, false,
                    net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
        }
        if (RunManager.boonLv(player, "aura") > 0) {
            WeaponArts.pulse(level, player, pos, 5, 0.5, false,
                    net.minecraft.core.particles.ParticleTypes.FLAME);
        }
        if (RunManager.boonLv(player, "missile") > 0) {
            // 分裂の刻印・弓の追加弾: 発射弾数 +1/Lv
            int shots = 1 + RunManager.boonLv(player, "proj")
                    + StatCalc.wbonusOf(player).projectiles()
                    + (int) (StatCalc.affixOf(player, "projectiles") + StatCalc.extraStat(player, "projectiles"));
            for (int i = 0; i < shots; i++) {
                var target = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 16, pos.y - 6, pos.z - 16,
                                pos.x + 16, pos.y + 6, pos.z + 16),
                        e -> e.isAlive() && !(e instanceof ServerPlayer)).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                        .orElse(null);
                if (target != null) {
                    WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                            (float) (StatCalc.attackDamageOf(player) * 0.7));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                            target.getX(), target.getY() + 1, target.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
                    if (shots > 1) target.teleportTo(target.getX(), target.getY(), target.getZ());
                }
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
                        (float) (StatCalc.attackDamageOf(player) * 0.5));
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

    /** 魔術武器の魔弾: 照準錐内の敵に通常攻撃を遠隔命中させる。
     * 通常命中の全処理 (会心・特性・連鎖・ゲージ) を通し、最終威力に mult を掛ける */
    public static void castWandBolt(ServerPlayer player, double range, double mult, int maxTargets) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return;
        Vec3 origin = player.position().add(0, 1.5, 0);
        Vec3 look = player.getLookAngle();
        var cands = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(origin.x - range, origin.y - range / 2, origin.z - range,
                        origin.x + range, origin.y + range / 2, origin.z + range),
                e -> e.isAlive() && !(e instanceof ServerPlayer));
        var hits = new java.util.ArrayList<LivingEntity>();
        for (var e : cands) {
            Vec3 d = e.position().add(0, e.getBbHeight() / 2, 0).subtract(origin);
            double dist = d.length();
            if (dist > range + 1 || dist < 0.5) continue;
            double cos = Math.max(-1, Math.min(1, d.normalize().dot(look)));
            if (Math.acos(cos) > 0.22) continue;
            hits.add(e);
        }
        hits.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(origin)));
        int n = 0;
        for (var t : hits) {
            if (n++ >= Math.max(1, maxTargets)) break;
            boltMult = mult;
            try {
                t.hurtServer(level, level.damageSources().playerAttack(player),
                        (float) StatCalc.attackDamageOf(player));
            } finally {
                boltMult = 0;
            }
            Vec3 tp = t.position().add(0, t.getBbHeight() / 2, 0);
            int steps = Math.max(1, (int) (origin.distanceTo(tp) * 2));
            for (int i = 1; i <= steps; i++) {
                Vec3 p = origin.lerp(tp, (double) i / steps);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 武器スキル (撃破時発動)。主手持ちの abyss_skill を読む */
    private static void triggerWeaponSkill(ServerPlayer player, ServerLevel level, LivingEntity dead) {
        String skill = GearData.skillOf(player.getMainHandItem());
        if (skill == null) return;
        Vec3 pos = dead.position();
        switch (skill) {
            case "w_soul" -> RunManager.addGauge(player, 8);
            case "w_feast" -> RunManager.heal(player, player.getMaxHealth() * 0.05);
            case "w_chain" -> WeaponArts.pulse(level, player, pos, 4, 0.8, false,
                    net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            case "w_break" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 5, pos.y - 4, pos.z - 5,
                                pos.x + 5, pos.y + 4, pos.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                }
            }
            case "w_swift" -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0));
            case "w_burst" -> WeaponArts.pulse(level, player, pos, 3, 1.5, true,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            case "w_venge" -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
            case "w_tide" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 6, pos.y - 4, pos.z - 6,
                                pos.x + 6, pos.y + 4, pos.z + 6),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    Vec3 pull = pos.subtract(e.position()).normalize().scale(1.6);
                    e.push(pull.x, 0.2, pull.z);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        pos.x, pos.y + 1, pos.z, 20, 2, 1, 2, 0.1);
            }
            case "w_gale" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1));
                RunManager.addGauge(player, 6);
            }
            case "w_ignite" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 5, pos.y - 4, pos.z - 5,
                                pos.x + 5, pos.y + 4, pos.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        pos.x, pos.y + 1, pos.z, 20, 1.5, 1, 1.5, 0.1);
            }
            case "w_greed" -> {
                if (player.getRandom().nextFloat() < 0.20f) {
                    giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
                    player.displayClientMessage(Component.literal("§6強奪！ 魂の欠片を奪った"), true);
                }
            }
            case "w_hymn" -> {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 0));
                RunManager.heal(player, player.getMaxHealth() * 0.02);
            }
            case "w_warcry" -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 100, 0));
            }
            case "w_avalanche" -> {
                var cands = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 6, pos.y - 4, pos.z - 6,
                                pos.x + 6, pos.y + 4, pos.z + 6),
                        x -> x.isAlive() && !(x instanceof ServerPlayer));
                cands.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
                int n = 0;
                for (LivingEntity e : cands) {
                    if (n++ >= 3) break;
                    Vec3 pull = pos.subtract(e.position()).normalize().scale(2.0);
                    e.push(pull.x, 0.2, pull.z);
                }
            }
            case "w_storm" -> {
                var target = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 12, pos.y - 6, pos.z - 12,
                                pos.x + 12, pos.y + 6, pos.z + 12),
                        x -> x.isAlive() && !(x instanceof ServerPlayer)).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                        .orElse(null);
                if (target != null) {
                    WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                            (float) (StatCalc.attackDamageOf(player) * 1.2));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            target.getX(), target.getY() + 1, target.getZ(), 16, 0.4, 0.8, 0.4, 0.1);
                }
            }
            case "w_rend" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 5, pos.y - 4, pos.z - 5,
                                pos.x + 5, pos.y + 4, pos.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                }
            }
            default -> {
            }
        }
    }

    /** 回避ダッシュ: 移動方向へ6m瞬動 + 0.5秒無敵 (原作dashの簡易版)。静止時は前方 */
    public static void dash(ServerPlayer player) {
        float cd = player.getData(ModAttachments.DASH_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7回避CT %.1fs", cd)), true);
            return;
        }
        Vec3 mot = player.getDeltaMovement();
        Vec3 dir;
        if (mot.horizontalDistanceSqr() > 0.004) {
            dir = new Vec3(mot.x, 0, mot.z).normalize();
        } else {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0, look.z).normalize();
        }
        Vec3 dest = player.position().add(dir.scale(6));
        player.teleportTo(dest.x, dest.y, dest.z);
        player.setData(ModAttachments.IFRAME.get(), 0.5f);
        // 原作: 職業別の回避CT. 敏捷1点につき-1% (下限0.28秒)。
        // 疾走の刻印: 回避短縮12%/Lv (原作 b_speed.dashCdPct)
        var cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        double dashCd = GameData.dashCooldown(cls, (int) StatCalc.attrEff(player, 2));
        int dashPct = RunManager.boonLv(player, "speed") * 12;
        if (dashPct > 0) dashCd *= Math.max(0.5, 1 - dashPct / 100.0);
        // 防具スキル「敏捷の靴」: 回避CT-10%
        if ("b_vigor".equals(GearData.skillOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)))) {
            dashCd *= 0.9;
        }
        player.setData(ModAttachments.DASH_CD.get(), (float) dashCd);
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
        int[] a = StatCalc.attrsOf(player);
        player.sendSystemMessage(Component.literal(String.format(
                "§6[%s %s Lv%d] §f第%d層[%s/%s] §d魂%.0f/100 §c攻撃%.1f §aHP%.0f/%.0f §e撃破%d §b奥義:%s",
                cls.name, cls.title, player.getData(ModAttachments.LEVEL.get()),
                player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.MODE.get()), player.getData(ModAttachments.DIFF.get()),
                player.getData(ModAttachments.SOUL.get()),
                StatCalc.attackDamageOf(player),
                player.getHealth(), player.getMaxHealth(),
                player.getData(ModAttachments.KILLS.get()),
                style != null ? style.artName : "武器を持ってね")));
        player.sendSystemMessage(Component.literal(String.format(
                "§7属性 剛%d 韻%d 敏%d 忍%d 幸%d 集%d 活%d 技%d 採%d 跳%d §8| §7祝福%s §8| §7層主 %s",
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9],
                RunManager.boonsOf(player).toString(),
                player.getData(ModAttachments.BOSSES.get()))));
        player.sendSystemMessage(Component.literal(String.format(
                "§7区域 %s §8| §7遺物 %s §8| §7技能 %s §8| §7実績 %s",
                GameData.zoneForFloor(player.getData(ModAttachments.FLOOR.get())).name(),
                RunManager.relicsOf(player).toString(),
                RunManager.passivesOf(player).toString(),
                player.getData(ModAttachments.ACH.get()))));
        var held = player.getMainHandItem();
        if (ModItems.styleOf(held.getItem()) != null) {
            String trait = GearData.traitOf(held);
            String curse = GearData.curseOf(held);
            int affixes = 0;
            for (var ak : GameData.WEAPON_AFFIXES) {
                if (GearData.affixOf(held, ak.key()) != 0) affixes++;
            }
            player.sendSystemMessage(Component.literal(String.format(
                    "§7武器 %s (希少:%s 特性:%s%s 接辞:%d 鍛造+%d)",
                    held.getHoverName().getString(),
                    GearData.rarityOf(held),
                    trait != null ? GearData.traitName(trait) : "なし",
                    curse != null ? " 呪い:" + curse + GearData.curseValueOf(held) : "",
                    affixes,
                    GearData.forgeLv(held))));
        }
        player.sendSystemMessage(Component.literal(String.format(
                "§7スコア %d (撃破%d 与ダメ%d 最大 combo%d)",
                RunManager.calcScore(player, false),
                player.getData(ModAttachments.KILLS.get()),
                player.getData(ModAttachments.DMG_DEALT.get()),
                player.getData(ModAttachments.BEST_COMBO.get()))));
    }
}
