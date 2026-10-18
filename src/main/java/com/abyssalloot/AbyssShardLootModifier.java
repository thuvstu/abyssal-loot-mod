package com.abyssalloot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * バニラの宝箱戦利品に「深淵の痕跡」(魂の欠片) を混入させるグローバルルートモディファイア.
 *
 * 古代都市・簡易ダンジョン・廃坑・埋もれた宝・要塞の図書館の宝箱に欠片が紛れ込み、
 * 地表・地上部のバニラ探索そのものが深淵への導線になる (バニラ進行との接続点)。
 * 出現率と個数は data/abyssalloot/loot_modifiers/*.json 側の chance/min/max で調整する。
 */
public final class AbyssShardLootModifier extends LootModifier {
    public static final MapCodec<AbyssShardLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(lm -> lm.conditions),
                    Codec.FLOAT.optionalFieldOf("chance", 0.45f).forGetter(m -> m.chance),
                    Codec.INT.optionalFieldOf("min", 1).forGetter(m -> m.min),
                    Codec.INT.optionalFieldOf("max", 2).forGetter(m -> m.max)
            ).apply(inst, AbyssShardLootModifier::new));

    private final float chance;
    private final int min;
    private final int max;

    public AbyssShardLootModifier(LootItemCondition[] conditions, float chance, int min, int max) {
        super(conditions);
        this.chance = chance;
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (chance <= 0 || context.getRandom().nextFloat() >= chance) return generatedLoot;
        int n = min + context.getRandom().nextInt(max - min + 1);
        generatedLoot.add(new ItemStack(ModItems.SOUL_SHARD.get(), n));
        return generatedLoot;
    }
}
