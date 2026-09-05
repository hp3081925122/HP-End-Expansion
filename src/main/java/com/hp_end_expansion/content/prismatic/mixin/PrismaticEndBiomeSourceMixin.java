package com.hp_end_expansion.content.prismatic.mixin;

import com.hp_end_expansion.content.prismatic.worldgen.PrismaticBiomeAccess;
import com.hp_end_expansion.content.prismatic.worldgen.PrismaticBiomeRouting;
import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TheEndBiomeSource.class)
public abstract class PrismaticEndBiomeSourceMixin implements PrismaticBiomeAccess {
    @Shadow @Final @Mutable public static MapCodec<TheEndBiomeSource> CODEC;
    @Unique private Holder<Biome> prismatic$biome;

    // 编解码包装逻辑放在普通包，避免目标类引用受隔离的混入内部类型。
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void prismatic$extendCodec(CallbackInfo callback) {
        CODEC = PrismaticBiomeRouting.withBiome(CODEC);
    }

    // 引用在来源开始取样和缓存可能群系之前写入，不使用全局静态群系缓存。
    @Override public void prismatic$setBiome(Holder<Biome> biome) {
        prismatic$biome = biome;
    }

    // 原版定位指令和特征排序必须能枚举到新增群系。
    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
    private void prismatic$appendPossible(CallbackInfoReturnable<Stream<Holder<Biome>>> callback) {
        if (prismatic$biome != null) callback.setReturnValue(Stream.concat(callback.getReturnValue(), Stream.of(prismatic$biome)));
    }

    // 仅改写原版高地和中地结果；中央岛、小岛、贫瘠地与其他自定义群系保持原样。
    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void prismatic$sample(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        Holder<Biome> original = callback.getReturnValue();
        if (prismatic$biome != null && (original.is(Biomes.END_HIGHLANDS) || original.is(Biomes.END_MIDLANDS))
                && PrismaticBiomeRouting.patch(QuartPos.toBlock(x), QuartPos.toBlock(z), 384, 0x5042494f4d45L) > 0.56) {
            callback.setReturnValue(prismatic$biome);
        }
    }
}
