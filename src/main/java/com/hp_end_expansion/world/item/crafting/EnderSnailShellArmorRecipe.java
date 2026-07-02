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
    public EnderSnailShellArmorRecipe(CraftingBookCategory 分类) {
        super(分类);
    }

    // 匹配任意一件盔甲加任意一个末影蜗牛壳。
    @Override
    public boolean matches(CraftingInput 输入物品, Level 世界) {
        ItemStack 盔甲物品栈 = ItemStack.EMPTY;
        ItemStack 蜗牛壳物品栈 = ItemStack.EMPTY;
        for (int 索引 = 0; 索引 < 输入物品.size(); 索引++) {
            ItemStack 物品栈 = 输入物品.getItem(索引);
            if (!物品栈.isEmpty()) {
                if (物品栈.getItem() instanceof ArmorItem) {
                    if (!盔甲物品栈.isEmpty()) {
                        return false;
                    }
                    盔甲物品栈 = 物品栈;
                } else if (物品栈.is(ModItems.ENDER_SNAIL_SHELL.get())) {
                    if (!蜗牛壳物品栈.isEmpty()) {
                        return false;
                    }
                    蜗牛壳物品栈 = 物品栈;
                } else {
                    return false;
                }
            }
        }
        return !盔甲物品栈.isEmpty() && !蜗牛壳物品栈.isEmpty();
    }

    // 输出继承原盔甲组件，并把蜗牛壳层数增加一层。
    @Override
    public ItemStack assemble(CraftingInput 输入物品, HolderLookup.Provider registries) {
        ItemStack 盔甲物品栈 = ItemStack.EMPTY;
        boolean 有蜗牛壳 = false;
        for (int 索引 = 0; 索引 < 输入物品.size(); 索引++) {
            ItemStack 物品栈 = 输入物品.getItem(索引);
            if (!物品栈.isEmpty()) {
                if (物品栈.getItem() instanceof ArmorItem) {
                    if (!盔甲物品栈.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    盔甲物品栈 = 物品栈.copyWithCount(1);
                } else if (物品栈.is(ModItems.ENDER_SNAIL_SHELL.get())) {
                    if (有蜗牛壳) {
                        return ItemStack.EMPTY;
                    }
                    有蜗牛壳 = true;
                } else {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (盔甲物品栈.isEmpty() || !有蜗牛壳) {
            return ItemStack.EMPTY;
        }
        EnderSnailShellArmorData.setShellCount(盔甲物品栈, EnderSnailShellArmorData.getShellCount(盔甲物品栈) + 1);
        return 盔甲物品栈;
    }

    // 至少需要两格合成空间。
    @Override
    public boolean canCraftInDimensions(int 宽度, int height) {
        return 宽度 * height >= 2;
    }

    // 返回末影蜗牛壳盔甲强化配方序列化器。
    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ENDER_SNAIL_SHELL_ARMOR.get();
    }
}
