package com.hp_end_expansion.world.item.crafting;

import com.hp_end_expansion.registry.ModItems;
import com.hp_end_expansion.registry.ModRecipeSerializers;
import com.hp_end_expansion.world.item.EnderSnailShellArmorData;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class EnderSnailShellArmorRecipe extends CustomRecipe {
    // 创建末影蜗牛壳强化盔甲配方。
    public EnderSnailShellArmorRecipe(CraftingBookCategory category) {
        super(category);
    }

    // 匹配任意一件盔甲加任意一个末影蜗牛壳。
    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack armorStack = ItemStack.EMPTY;
        ItemStack shellStack = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() instanceof ArmorItem) {
                    if (!armorStack.isEmpty()) {
                        return false;
                    }
                    armorStack = stack;
                } else if (stack.is(ModItems.ENDER_SNAIL_SHELL.get())) {
                    if (!shellStack.isEmpty()) {
                        return false;
                    }
                    shellStack = stack;
                } else {
                    return false;
                }
            }
        }
        return !armorStack.isEmpty() && !shellStack.isEmpty();
    }

    // 输出继承原盔甲组件，并把蜗牛壳层数增加一层。
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack armorStack = ItemStack.EMPTY;
        boolean hasShell = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() instanceof ArmorItem) {
                    if (!armorStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    armorStack = stack.copyWithCount(1);
                } else if (stack.is(ModItems.ENDER_SNAIL_SHELL.get())) {
                    if (hasShell) {
                        return ItemStack.EMPTY;
                    }
                    hasShell = true;
                } else {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (armorStack.isEmpty() || !hasShell) {
            return ItemStack.EMPTY;
        }
        EnderSnailShellArmorData.setShellCount(armorStack, EnderSnailShellArmorData.getShellCount(armorStack) + 1);
        return armorStack;
    }

    // 至少需要两格合成空间。
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    // 返回末影蜗牛壳盔甲强化配方序列化器。
    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ENDER_SNAIL_SHELL_ARMOR.get();
    }
}
