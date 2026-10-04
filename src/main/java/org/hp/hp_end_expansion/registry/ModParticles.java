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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAR_EMBER = PARTICLE_TYPES.register("star_ember", () -> new SimpleParticleType(false));
    // 裂隙火花：短寿命星形闪光
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SPARK = PARTICLE_TYPES.register("rift_spark", () -> new SimpleParticleType(false));
    // 裂隙碎片：带重力与旋转的晶片
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SHARD = PARTICLE_TYPES.register("rift_shard", () -> new SimpleParticleType(false));
    // 星雨碎石：贴地滚动的星骸岩屑；星灰：裂隙边缘剥落、几乎不落的暗色薄片
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAR_DEBRIS = PARTICLE_TYPES.register("star_debris", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAR_ASH = PARTICLE_TYPES.register("star_ash", () -> new SimpleParticleType(false));
    // 负星者：白金圣焰、狂暴赤焰、金色碎晶
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEARER_FLAME = PARTICLE_TYPES.register("bearer_flame", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEARER_RAGE = PARTICLE_TYPES.register("bearer_rage", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEARER_SHARD = PARTICLE_TYPES.register("bearer_shard", () -> new SimpleParticleType(false));
    // 负星者的负光招式：吸光的虚蚀微粒、青白星芒
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEARER_VOID = PARTICLE_TYPES.register("bearer_void", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEARER_GLINT = PARTICLE_TYPES.register("bearer_glint", () -> new SimpleParticleType(false));
    // 群星之弓蓄力：星流、聚能环和满蓄力脉冲（只在客户端生成）
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAR_CHARGE = PARTICLE_TYPES.register("star_charge", () -> new SimpleParticleType(true));
    // 裂天之主：天幕碎屑
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SKY_MOTE = PARTICLE_TYPES.register("sky_mote", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
