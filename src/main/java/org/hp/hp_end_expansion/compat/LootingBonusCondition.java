package org.hp.hp_end_expansion.compat;

import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.hp.hp_end_expansion.Hp_end_expansion;

public record LootingBonusCondition(float unenchantedChance, float firstLevelChance, float perLevel) implements LootItemCondition {
    public static final DeferredRegister<LootItemConditionType> TYPES = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Hp_end_expansion.MODID);
    public static final RegistryObject<LootItemConditionType> TYPE = TYPES.register("looting_bonus", () -> new LootItemConditionType(new Serializer()));

    @Override public LootItemConditionType getType() { return TYPE.get(); }
    @Override public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY, LootContextParams.KILLER_ENTITY, LootContextParams.DAMAGE_SOURCE);
    }
    @Override public boolean test(LootContext context) {
        int level = context.getLootingModifier();
        return context.getRandom().nextFloat() < chance(level);
    }
    public float chance(int level) {
        return level <= 0 ? unenchantedChance : firstLevelChance + (level - 1) * perLevel;
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<LootingBonusCondition> {
        @Override public void serialize(JsonObject json, LootingBonusCondition value, JsonSerializationContext context) {
            json.addProperty("unenchanted_chance", value.unenchantedChance);
            json.addProperty("first_level_chance", value.firstLevelChance);
            json.addProperty("per_level", value.perLevel);
        }
        @Override public LootingBonusCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            return new LootingBonusCondition(json.get("unenchanted_chance").getAsFloat(),
                json.get("first_level_chance").getAsFloat(), json.get("per_level").getAsFloat());
        }
    }
}
