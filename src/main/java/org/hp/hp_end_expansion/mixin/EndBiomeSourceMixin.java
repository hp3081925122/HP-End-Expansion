package org.hp.hp_end_expansion.mixin;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.*;
import org.hp.hp_end_expansion.compat.TerrablenderCompat;
import org.hp.hp_end_expansion.worldgen.*;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TheEndBiomeSource.class)
public abstract class EndBiomeSourceMixin implements StarwreckBiomeAccess {
    @Shadow @Final @Mutable public static MapCodec<TheEndBiomeSource> CODEC;
    @Unique private Holder<Biome> hp_starwreck;
    @Unique private Holder<Biome> hp_tidelight;
    @Unique private volatile StarwreckNoise.Sampling hp_sampling;
    @Override
    public void hp_setStarwreck(Holder<Biome> biome) { hp_starwreck = biome; }
    @Override
    public void hp_setTidelight(Holder<Biome> biome) { hp_tidelight = biome; }
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void hp_codec(CallbackInfo ci) {
        if (TerrablenderCompat.isLoaded()) return;
        CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(RegistryOps.<Biome, TheEndBiomeSource>retrieveGetter(Registries.BIOME))
            .apply(instance, TheEndBiomeSource::create));
    }
    @Inject(method = "create", at = @At("RETURN"))
    private static void hp_create(HolderGetter<Biome> registry, CallbackInfoReturnable<TheEndBiomeSource> cir) {
        if (TerrablenderCompat.isLoaded()) return;
        if (!registry.getOrThrow(Biomes.END_HIGHLANDS).isBound()) return;
        registry.get(StarwreckWorldgen.BIOME).ifPresent(biome -> ((StarwreckBiomeAccess) cir.getReturnValue()).hp_setStarwreck(biome));
        registry.get(TidelightWorldgen.BIOME).ifPresent(biome -> ((StarwreckBiomeAccess) cir.getReturnValue()).hp_setTidelight(biome));
    }
    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
    private void hp_possible(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        if (TerrablenderCompat.isLoaded()) return;
        if (hp_starwreck != null && StarwreckConfig.enabled()) cir.setReturnValue(Stream.concat(cir.getReturnValue(), Stream.of(hp_starwreck)));
        if (hp_tidelight != null && StarwreckConfig.tidelightEnabled()) cir.setReturnValue(Stream.concat(cir.getReturnValue(), Stream.of(hp_tidelight)));
    }
    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void hp_replace(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (TerrablenderCompat.isLoaded()) return;
        if ((hp_starwreck == null || !StarwreckConfig.enabled()) && (hp_tidelight == null || !StarwreckConfig.tidelightEnabled())) return;
        Holder<Biome> original = cir.getReturnValue();
        if (!original.is(Biomes.END_HIGHLANDS) && !original.is(Biomes.END_MIDLANDS) && !original.is(Biomes.END_BARRENS)) return;
        StarwreckNoise.Sampling sampling = hp_sampling;
        if (sampling == null || sampling.sampler() != sampler) hp_sampling = sampling = new StarwreckNoise.Sampling(sampler, StarwreckNoise.forSampler(sampler));
        StarwreckNoise.Region region = sampling.noise().region(x * 4, z * 4);
        if (region == StarwreckNoise.Region.TIDELIGHT && hp_tidelight != null && StarwreckConfig.tidelightEnabled()) {
            cir.setReturnValue(hp_tidelight);
        } else if (region == StarwreckNoise.Region.STARWRECK && hp_starwreck != null && StarwreckConfig.enabled()) cir.setReturnValue(hp_starwreck);
    }
}
