package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 裂天之主的自绘血条，替掉原版红条。贴图 textures/gui/skyrender_bossbar.png 由 art/skyrender_bossbar.py 手绘生成。
 * 黑曜石边框，血槽里是慢慢流动的夜空；掉进第二阶段夜空裂开熔纹，第三阶段整条烧透。
 * 血量前沿是一道烧着的裂边，掉血先闪一下白，后面拖一截橙色残影再追上来；
 * 60% 和 25% 两道阶段刻痕被越过就烧亮，左端徽记里的天裂随阶段一点点撕开。
 * 服务端把血条名字包在 {@link SkyrenderEntity#BAR_KEY} 里，这里只认这个键，别的首领血条不受影响。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class SkyrenderBossBar {
    private static final ResourceLocation TEX = SkyrenderClient.id("textures/gui/skyrender_bossbar.png");
    private static final int TW = 256, TH = 64, W = 220, H = 22;
    private static final int WIN_X = 15, WIN_Y = 9, WIN_W = 190, WIN_H = 6, TILE = 128;
    /** 残影：掉血后停 10 拍，再按每拍 1.2% 往下追。 */
    private static final float CHIP_HOLD = 10, CHIP_SPEED = 0.012F;
    /** 每条血条一份：{残影位置, 上一帧血量, 开始追的时刻, 上一帧时刻, 最近一次掉血时刻}。 */
    private static final Map<UUID, float[]> STATE = new HashMap<>();

    private SkyrenderBossBar() {}

    @SubscribeEvent public static void bar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !SkyrenderEntity.BAR_KEY.equals(tc.getKey())) return;
        event.setCanceled(true);
        event.setIncrement(H + 10);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        GuiGraphics g = event.getGuiGraphics();
        float time = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        float p = Mth.clamp(boss.getProgress(), 0, 1);
        int phase = p > SkyrenderEntity.PHASE2_AT ? 1 : p > SkyrenderEntity.PHASE3_AT ? 2 : 3;

        // 残影
        STATE.values().removeIf(s -> time - s[3] > 200 || time < s[3]);
        float[] s = STATE.computeIfAbsent(boss.getId(), id -> new float[]{p, p, time, time, -100});
        if (p < s[1] - 1.0E-4F) {
            s[2] = time + CHIP_HOLD;
            s[4] = time;
        }
        if (p >= s[0]) s[0] = p;
        else if (time > s[2]) s[0] = Math.max(p, s[0] - (time - Math.max(s[3], s[2])) * CHIP_SPEED);
        s[1] = p;
        s[3] = time;
        float flash = 1 - Mth.clamp((time - s[4]) / 4, 0, 1);

        int x = g.guiWidth() / 2 - W / 2, y = event.getY();
        int wx = x + WIN_X, wy = y + WIN_Y;
        int fw = Math.round(WIN_W * p), cw = Math.round(WIN_W * s[0]);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(TEX, x, y, 0, 0, W, H, TW, TH);
        tile(g, wx, wy, 42, WIN_W, 0);
        if (cw > fw) {
            // 停着的时候亮，开始追就慢慢暗下去
            float k = time < s[2] ? 1 : 0.7F;
            g.fill(wx + fw, wy, wx + cw, wy + 1, argb(k, 255, 214, 140));
            g.fill(wx + fw, wy + 1, wx + cw, wy + WIN_H, argb(k, 226, 112, 52));
        }
        tile(g, wx, wy, 24 + 6 * (phase - 1), fw, (int) (time * 0.25F) % TILE);
        if (flash > 0 && fw > 0) g.fill(wx, wy, wx + fw, wy + WIN_H, argb(0.55F * flash, 255, 246, 226));
        for (float at : new float[]{SkyrenderEntity.PHASE2_AT, SkyrenderEntity.PHASE3_AT}) {
            int nx = wx + Math.round(WIN_W * at) - 1;
            g.blit(TEX, nx, y + 5, p <= at ? 144 : 140, 24, 3, 12, TW, TH);
        }
        if (fw > 0 && fw < WIN_W) {
            int frame = ((int) (time / (phase == 3 ? 2 : 4))) & 1;
            g.blit(TEX, wx + fw - 2, y + 7, frame == 0 ? 130 : 135, 24, 4, 10, TW, TH);
        }
        g.blit(TEX, x + 5, y + 4, 150 + 6 * (phase - 1), 24, 5, 14, TW, TH);
        RenderSystem.disableBlend();

        Component name = boss.getName();
        int color = phase == 3 ? 0xFFFFC890 : phase == 2 ? 0xFFF0D8B8 : 0xFFE4DCF0;
        g.drawString(mc.font, name, g.guiWidth() / 2 - mc.font.width(name) / 2, y - 6, color, true);
    }

    /** 把 128 宽的可平铺条从 off 处开始横向铺满 width。 */
    private static void tile(GuiGraphics g, int x, int y, int v, int width, int off) {
        int drawn = 0;
        while (drawn < width) {
            int u = (off + drawn) % TILE, w = Math.min(TILE - u, width - drawn);
            g.blit(TEX, x + drawn, y, u, v, w, WIN_H, TW, TH);
            drawn += w;
        }
    }

    private static int argb(float a, int r, int g, int b) {
        return (Math.round(Mth.clamp(a, 0, 1) * 255) << 24) | (r << 16) | (g << 8) | b;
    }
}
