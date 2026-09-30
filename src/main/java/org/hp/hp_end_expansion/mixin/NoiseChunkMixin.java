package org.hp.hp_end_expansion.mixin;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.hp.hp_end_expansion.worldgen.StarwreckNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {
    @Unique private StarwreckNoise hp_noise;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hp_source(int cells, RandomState state, int x, int z, NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier,
                           NoiseGeneratorSettings settings, Aquifer.FluidPicker fluid, Blender blender, CallbackInfo ci) {
        hp_noise = StarwreckNoise.forSampler(state.sampler());
    }

    @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
    private void hp_sampler(NoiseRouter router, List<Climate.ParameterPoint> targets, CallbackInfoReturnable<Climate.Sampler> cir) {
        StarwreckNoise.register(cir.getReturnValue(), hp_noise);
    }
}
