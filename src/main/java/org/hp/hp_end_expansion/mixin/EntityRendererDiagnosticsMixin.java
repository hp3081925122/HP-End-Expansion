package org.hp.hp_end_expansion.mixin;

import com.mojang.logging.LogUtils;
import java.util.Map;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRendererDiagnosticsMixin {
    @Unique
    private static final Logger hp$rendererLogger = LogUtils.getLogger();

    @Shadow
    private Map<EntityType<?>, EntityRenderer<?>> renderers;

    @Inject(method = "shouldRender", at = @At("HEAD"))
    private void hp$reportMissingRenderer(Entity entity, Frustum frustum, double x, double y, double z,
                                         CallbackInfoReturnable<Boolean> result) {
        EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) (Object) this;
        if (dispatcher.getRenderer(entity) != null) {
            return;
        }
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        hp$rendererLogger.error("Client entity renderer missing: type {}, class {}, entity id {}, position {}, dimension {}, renderer count {}, type key present {}",
            typeId, entity.getClass().getName(), entity.getId(), entity.position(), entity.level().dimension().location(),
            this.renderers.size(), this.renderers.containsKey(entity.getType()));
        CrashReport report = CrashReport.forThrowable(new IllegalStateException("Missing entity renderer for " + typeId), "Checking entity visibility");
        CrashReportCategory category = report.addCategory("Entity missing client renderer");
        entity.fillCrashReportCategory(category);
        category.setDetail("Entity class", entity.getClass().getName());
        category.setDetail("Registered renderer count", this.renderers.size());
        category.setDetail("Type key present", this.renderers.containsKey(entity.getType()));
        category.setDetail("Registered renderer types", this.renderers.keySet().stream()
            .map(type -> String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type))).sorted().toList().toString());
        throw new ReportedException(report);
    }
}
