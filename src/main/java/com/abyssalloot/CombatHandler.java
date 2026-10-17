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

    /** 最終撃破tick (コンボ減衰用) */
    private static final java.util.Map<java.util.UUID, Long> LAST_KILL_TICK =
            new java.util.HashMap<>();

    /** 装備変更で即時ステータス再計算 (防具の着脱・持替でHP等が追従する) */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isAlive()) return;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()), true);
    }

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
                && !LootHandler.hasAbyssTag(event.getEntity())) {
            int roam = player.getData(ModAttachments.ROAM_KILLS.get()) + 1;
            if (roam >= 25) {
                roam = 0;
                int shards = RunManager.materialCount(player, 1);
                if (shards > 0) {
                    LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
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
            LootHandler.giveOrDrop(player, drop);
            player.sendSystemMessage(Component.literal(
                    "§b戦利品: " + drop.getHoverName().getString()
                            + (ModItems.slotOf(drop.getItem()) != null ? " (右クリックで装備)" : " (持ち替えで奥義が変わる)")));
            if ("mythic".equals(GearData.rarityOf(drop))) {
                var a = GameData.achievementById("mythic-hunter");
                Achievements.grantAch(player, "mythic-hunter", a.title(), GameData.shardsForReward(a.reward()));
            }
        } else if (rand.nextDouble() < 0.20) {
            int shards = RunManager.materialCount(player, 1.0);
            if (shards > 0) LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        } else if (rand.nextDouble() < 0.08) {
            LootHandler.giveOrDrop(player, ModItems.randomConsumable(rand));
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
            LootHandler.giveOrDrop(player, new ItemStack(foods[rand.nextInt(foods.length)], 1 + rand.nextInt(2)));
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
            LootHandler.giveOrDrop(player, new ItemStack(ModItems.VOID_ESSENCE.get(), 1));
            if (rand.nextFloat() < 0.5) LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            // 奥義の断片: 層主は1個以上を必ず落とす (武器の決まった入手経路になる)
            int artShards = 1 + (rand.nextFloat() < 0.4 ? 1 : 0);
            for (int i = 0; i < artShards; i++) {
                LootHandler.giveOrDrop(player, ModItems.randomArtShard(rand));
            }
            player.sendSystemMessage(Component.literal("§5素材: 虚空のエッセンスと奥義の断片 ×" + artShards + " を得た"));
        } else if (isWarden && rand.nextFloat() < 0.8) {
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            player.sendSystemMessage(Component.literal("§5素材: 番人の落とし物を得た"));
        } else if (isElite && rand.nextFloat() < 0.3) {
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            LootHandler.eliteMaterialDrop(player, rand);
            if (rand.nextFloat() < 0.06) {
                var setDrop = GearFactory.makeSetArmor(null, null, rand,
                        StatCalc.luckOf(player),
                        Math.max(1, player.getData(ModAttachments.FLOOR.get())), 0);
                LootHandler.giveOrDrop(player, setDrop);
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
                Achievements.grantAch(player, "level-twenty", a.title(), GameData.shardsForReward(a.reward()));
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
            BoonHandler.offerBoons(player);
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
                Achievements.grantAch(player, "floor-five", a.title(), GameData.shardsForReward(a.reward()));
            }
            if (floor == 20) {
                var a = GameData.achievementById("floor-twenty");
                Achievements.grantAch(player, "floor-twenty", a.title(), GameData.shardsForReward(a.reward()));
            }
            int bossCount = player.getData(ModAttachments.BOSSES.get()).split(",").length;
            if (bossCount >= 3) {
                var a = GameData.achievementById("boss-three");
                Achievements.grantAch(player, "boss-three", a.title(), GameData.shardsForReward(a.reward()));
            }
            if (bossCount >= 5) {
                var a = GameData.achievementById("boss-five");
                Achievements.grantAch(player, "boss-five", a.title(), GameData.shardsForReward(a.reward()));
            }
            String pendingPassive = player.getData(ModAttachments.PASSIVE_OFFER.get());
            if (pendingPassive == null || pendingPassive.isBlank()) {
                FloorEvents.offerPassives(player);
            } else {
                FloorEvents.resendPassiveOffer(player);
            }
        }
        // 武器特性: 吸血鬼 (撃破時HP3%回復) / 爆砕 (周囲爆発) / 血の契約 (祝福・撃破回復)
        String killTrait = GearData.traitOf(player.getMainHandItem());
        if ("vampiric".equals(killTrait)) {
            RunManager.heal(player, player.getMaxHealth() * 0.03);
        }
        // 武器スキル (撃破時) + 靴スキル (撃破で回避CTリセット)
        LootHandler.triggerWeaponSkill(player, level, event.getEntity());
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
        Achievements.grantAch(player, "first-blood", "深淵への第一歩", 2);
        if (player.getData(ModAttachments.KILLS.get()) >= 100) {
            Achievements.grantAch(player, "hundred-kills", "百鬼夜行", 8);
        }
        if (player.getData(ModAttachments.KILLS.get()) >= 1000) {
            var a = GameData.achievementById("thousand-kills");
            Achievements.grantAch(player, "thousand-kills", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.COMBO.get()) >= 50) {
            var a = GameData.achievementById("combo-fifty");
            Achievements.grantAch(player, "combo-fifty", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.COMBO.get()) >= 100) {
            var a = GameData.achievementById("combo-hundred");
            Achievements.grantAch(player, "combo-hundred", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.PERFECT.get()) >= 20) {
            var a = GameData.achievementById("perfect-twenty");
            Achievements.grantAch(player, "perfect-twenty", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.HARVEST.get()) >= 10) {
            var a = GameData.achievementById("harvest-ten");
            Achievements.grantAch(player, "harvest-ten", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (player.getData(ModAttachments.KILLS.get()) >= 5000) {
            var a = GameData.achievementById("kills-five-thousand");
            Achievements.grantAch(player, "kills-five-thousand", a.title(), GameData.shardsForReward(a.reward()));
        }
        String bossRec = player.getData(ModAttachments.BOSSES.get());
        if (bossRec.contains("10")) Achievements.grantAch(player, "floor-ten", "深層探査者", 10);
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
            FloorEvents.offerRelics(player);
        }
        // 層クリア判定
        // 蘇生師・門の子は層のウェーブに含めない (数えると転移門が早く開いてしまう)
        if (!event.getEntity().getTags().contains(Enemies.SUMMON_TAG)) {
            player.setData(ModAttachments.FLOOR_KILLS.get(), player.getData(ModAttachments.FLOOR_KILLS.get()) + 1);
        }
        // 呪われた召喚門の破壊 (原作: 戦績に保存され実績「門を断つ者」)
        if (event.getEntity().getTags().contains(Enemies.GATE_TAG)) {
            Achievements.grantAch(player, "gate-breaker", "門を断つ者", 8);
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
            FloorEvents.offerEvent(player);
            // 層の締め前に未選択の祝福を確実に提示 (消える前に選ばせる)
            if (RunManager.pendingBoonsOf(player) > 0
                    && player.getData(ModAttachments.OFFER.get()).isBlank()) {
                player.sendSystemMessage(Component.literal(
                        "§e◆ レベルアップの祝福が未選択だ (" + RunManager.pendingBoonsOf(player) + "件)"));
                BoonHandler.offerBoons(player);
            } else if (!player.getData(ModAttachments.OFFER.get()).isBlank()) {
                BoonHandler.resendBoonOffer(player);
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
                    LootHandler.triggerDestructible(player, level, p[0], pos);
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
            LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
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
                LootHandler.giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
            }
        } else if (held == ModItems.ABYSS_SHEARS.get()) {
            // 蜘蛛の巣から50%で糸+2、葉から10%で棒
            if (path.contains("cobweb") && rand.nextDouble() < 0.5) {
                LootHandler.giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.STRING, 2));
            } else if (path.contains("leaves") && rand.nextDouble() < 0.10) {
                LootHandler.giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.STICK));
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

    private static int needFor(int level) {
        return (level - 1) * (level + 4);
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
                        RunManager.pendingBoonsOf(player),
                        player.getData(ModAttachments.LIGHT_CD.get()),
                        player.getData(ModAttachments.SKILL_CD.get())));
    }


    /** 無敵・ダッシュCT・反撃窓の減衰 + ボスバー更新 + 罠 */
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        tickCounter++;
        // 遅延タスク (絶対tick到達で発火・除去)
        DelayRunner.tick();
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
            // CT中は0.1秒ごとにHUDを再送して残り秒を滑らかに表示する
            boolean anyCd = player.getData(ModAttachments.LIGHT_CD.get()) > 0
                    || player.getData(ModAttachments.SKILL_CD.get()) > 0;
            if (anyCd ? tickCounter % 2 == 0 : tickCounter % 20 == 0) {
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
                SkillHandler.castWandBolt(player, 12, 0.7, targets);
            } else if (style == GameData.WeaponStyle.WAND) {
                SkillHandler.castWandBolt(player, 16, 0.9, 1);
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
