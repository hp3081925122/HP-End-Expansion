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
    public static int getShellCount(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return Math.max(0, tag.getInt(SHELL_COUNT_TAG));
    }

    // 设置盔甲上的蜗牛壳层数。
    public static void setShellCount(ItemStack stack, int shellCount) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (shellCount <= 0) {
                tag.remove(SHELL_COUNT_TAG);
            } else {
                tag.putInt(SHELL_COUNT_TAG, shellCount);
            }
        });
    }
}
