package com.hp_end_expansion.content.prismatic.test.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestServer.class)
public abstract class PrismaticGameTestServerMixin {
    @Shadow @Final @Mutable private static WorldOptions WORLD_OPTIONS;

    // 原版测试服默认关闭结构，只在显式测试源码集中启用真实区块结构流水线。
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void prismatic$enableNaturalStructures(CallbackInfo callback) {
        WORLD_OPTIONS = WORLD_OPTIONS.withStructures(true);
        LogUtils.getLogger().info("Enabled natural structures for prismatic GameTest validation; seed={}", WORLD_OPTIONS.seed());
    }
}
