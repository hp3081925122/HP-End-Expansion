package com.hp_end_expansion.registry;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.item.crafting.EnderSnailShellArmorRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    // 配方序列化器延迟注册器。
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, HpEndExpansion.MODID);

    // 末影蜗牛壳强化盔甲的特殊合成配方序列化器。
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<EnderSnailShellArmorRecipe>> ENDER_SNAIL_SHELL_ARMOR = RECIPE_SERIALIZERS.register("ender_snail_shell_armor", () -> new SimpleCraftingRecipeSerializer<>(EnderSnailShellArmorRecipe::new));

    // 配方序列化注册类只提供静态入口，不允许实例化。
    private ModRecipeSerializers() {
    }

    // 注册配方序列化器。
    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
