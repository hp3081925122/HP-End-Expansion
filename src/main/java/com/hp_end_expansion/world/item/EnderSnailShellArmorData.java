package com.hp_end_expansion.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class EnderSnailShellArmorData {
    // 盔甲自定义数据里保存蜗牛壳层数的字段名。
    public static final String SHELL_COUNT_TAG = "HpEndExpansionEnderSnailShells";

    // 工具类只提供静态读写入口，不允许实例化。
    private EnderSnailShellArmorData() {
    }

    // 读取盔甲上的蜗牛壳层数。
    public static int getShellCount(ItemStack 物品栈) {
        CustomData 自定义数据 = 物品栈.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag 标签 = 自定义数据.copyTag();
        return Math.max(0, 标签.getInt(SHELL_COUNT_TAG));
    }

    // 设置盔甲上的蜗牛壳层数。
    public static void setShellCount(ItemStack 物品栈, int 蜗牛壳数量) {
        CustomData.update(DataComponents.CUSTOM_DATA, 物品栈, 标签 -> {
            if (蜗牛壳数量 <= 0) {
                标签.remove(SHELL_COUNT_TAG);
            } else {
                标签.putInt(SHELL_COUNT_TAG, 蜗牛壳数量);
            }
        });
    }
}
