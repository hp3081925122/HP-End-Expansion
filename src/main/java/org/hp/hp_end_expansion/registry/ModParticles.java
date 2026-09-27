package org.hp.hp_end_expansion.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class ModParticles {
    // 粒子类型注册器
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Hp_end_expansion.MODID);
    // 裂隙火花：短寿命星形闪光
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SPARK = PARTICLE_TYPES.register("rift_spark", () -> new SimpleParticleType(false));
    // 裂隙碎片：带重力与旋转的晶片
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SHARD = PARTICLE_TYPES.register("rift_shard", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
