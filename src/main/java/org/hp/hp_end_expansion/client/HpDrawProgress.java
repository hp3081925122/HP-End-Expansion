package org.hp.hp_end_expansion.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.item.StarBowItem;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid=Hp_end_expansion.MODID,value=Dist.CLIENT)
public record HpDrawProgress(boolean crossbow) implements RangeSelectItemModelProperty {
    private static final MapCodec<HpDrawProgress> BOW=MapCodec.unit(new HpDrawProgress(false));
    private static final MapCodec<HpDrawProgress> CROSSBOW=MapCodec.unit(new HpDrawProgress(true));
    @SubscribeEvent public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,"star_bow_pull"),BOW);
        event.register(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,"tide_crossbow_pull"),CROSSBOW);
    }
    @Override public float get(ItemStack stack,@Nullable ClientLevel level,@Nullable ItemOwner owner,int seed) {
        LivingEntity entity=owner==null?null:owner.asLivingEntity();
        if(entity==null || !entity.isUsingItem() || entity.getUseItem()!=stack) return 0;
        return crossbow?(float)(stack.getUseDuration(entity)-entity.getUseItemRemainingTicks())/CrossbowItem.getChargeDuration(stack,entity):StarBowItem.getPullProgress(stack,entity);
    }
    @Override public MapCodec<HpDrawProgress> type() { return crossbow?CROSSBOW:BOW; }
}
