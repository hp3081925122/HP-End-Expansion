package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarRain;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;

/** 天瞳兆石：在坠星巨坑里用，撕开坑上的天幕召来裂天之主。 */
public final class SkyEyeOmenItem extends Item {
    private static final ResourceKey<Structure> CRATER = ResourceKey.create(Registries.STRUCTURE,
        ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "starfall_crater"));

    public SkyEyeOmenItem(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.sky_eye_omen").withStyle(ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        // 只能在星骸荒原的坠星巨坑里用；同维度只有一只，星雨期间不行
        if (!server.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME)) return fail(player, stack, "wrong_biome");
        Structure crater = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CRATER);
        if (crater == null) return fail(player, stack, "not_crater");
        StructureStart start = server.structureManager().getStructureWithPieceAt(player.blockPosition(), crater);
        if (!start.isValid()) return fail(player, stack, "not_crater");
        if (SkyrenderEntity.present(server)) return fail(player, stack, "present");
        if (StarRain.active(server)) return fail(player, stack, "starfall");
        // 坑中心、坑半径按结构外框算
        BoundingBox box = start.getBoundingBox();
        Vec3 center = new Vec3((box.minX() + box.maxX() + 1) / 2.0, player.getY(), (box.minZ() + box.maxZ() + 1) / 2.0);
        double radius = Math.max(16, Math.min(box.getXSpan(), box.getZSpan()) / 2.0 - 2);
        if (SkyrenderEntity.summon(server, center, radius, player) == null) return fail(player, stack, "no_room");
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    private static InteractionResultHolder<ItemStack> fail(Player player, ItemStack stack, String key) {
        player.displayClientMessage(Component.translatable("message.hp_end_expansion.sky_eye_omen." + key), true);
        return InteractionResultHolder.fail(stack);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return use(context.getLevel(), player, context.getHand()).getResult();
    }
}
