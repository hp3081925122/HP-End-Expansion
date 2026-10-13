package org.hp.hp_end_expansion.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializationContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.hp.hp_end_expansion.Hp_end_expansion;

public record TagEnchantmentFunction(TagKey<Enchantment> options) implements LootItemFunction {
    public static final DeferredRegister<LootItemFunctionType> TYPES = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Hp_end_expansion.MODID);
    public static final RegistryObject<LootItemFunctionType> TYPE = TYPES.register("tag_enchantment", () -> new LootItemFunctionType(new Serializer()));

    @Override public LootItemFunctionType getType() { return TYPE.get(); }
    @Override public ItemStack apply(ItemStack stack, LootContext context) {
        return BuiltInRegistries.ENCHANTMENT.getTag(options)
            .flatMap(tag -> tag.getRandomElement(context.getRandom()))
            .map(enchantment -> EnchantRandomlyFunction.randomEnchantment().withEnchantment(enchantment.value()).build().apply(stack, context))
            .orElse(stack);
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<TagEnchantmentFunction> {
        @Override public void serialize(JsonObject json, TagEnchantmentFunction value, JsonSerializationContext context) {
            json.addProperty("options", "#" + value.options.location());
        }
        @Override public TagEnchantmentFunction deserialize(JsonObject json, JsonDeserializationContext context) {
            String options = json.get("options").getAsString();
            if (!options.startsWith("#")) throw new IllegalArgumentException("Enchantment options must reference a tag");
            return new TagEnchantmentFunction(TagKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(options.substring(1))));
        }
    }
}
