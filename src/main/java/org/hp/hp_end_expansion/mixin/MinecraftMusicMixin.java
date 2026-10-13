package org.hp.hp_end_expansion.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.hp.hp_end_expansion.client.StarwreckMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMusicMixin {
    @Inject(method = "getSituationalMusic", at = @At("RETURN"), cancellable = true)
    private void hp_selectMusic(CallbackInfoReturnable<Music> result) {
        result.setReturnValue(StarwreckMusic.selectMusic(result.getReturnValue()));
    }
}
