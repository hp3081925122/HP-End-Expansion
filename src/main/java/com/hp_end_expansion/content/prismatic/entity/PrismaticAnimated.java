package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

// 所有折光生物共用互斥动作控制器，物种分别引用自己的模型动作。
public interface PrismaticAnimated extends GeoEntity {
    String speciesId();

    // 同一控制器的触发动作覆盖移动姿态，死亡姿态始终保持。
    @Override
    default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String prefix = "animation." + speciesId() + ".";
        RawAnimation idle = RawAnimation.begin().thenLoop(prefix + "idle");
        RawAnimation walk = RawAnimation.begin().thenLoop(prefix + "walk");
        RawAnimation death = RawAnimation.begin().thenPlayAndHold(prefix + "death");
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            LivingEntity entity = (LivingEntity) this;
            if (entity.isDeadOrDying()) {
                return state.setAndContinue(death);
            }
            return state.setAndContinue(state.isMoving() ? walk : idle);
        }).triggerableAnim("attack", RawAnimation.begin().thenPlay(prefix + "attack"))
                .triggerableAnim("special", RawAnimation.begin().thenPlay(prefix + "special"))
                .triggerableAnim("hurt", RawAnimation.begin().thenPlay(prefix + "hurt"))
                .triggerableAnim("death", death));
    }
}
