package org.hp.hp_end_expansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class StarwreckStructure extends Structure {
    public static final MapCodec<StarwreckStructure> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),Codec.BOOL.fieldOf("platform").forGetter(s -> s.platform)).apply(i,StarwreckStructure::new));
    private final boolean platform;
    public StarwreckStructure(StructureSettings settings, boolean platform) { super(settings); this.platform=platform; }
    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x=context.chunkPos().getMiddleBlockX(),z=context.chunkPos().getMiddleBlockZ();
        if (!StarwreckConfig.enabled() || !context.validBiome().test(context.biomeSource().getNoiseBiome(x >> 2, 16, z >> 2, context.randomState().sampler()))) return Optional.empty();
        int y=context.chunkGenerator().getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
        if (y<55 || !StarwreckConfig.enabled()) return Optional.empty();
        int radius=platform ? 7 : 24+context.random().nextInt(9);
        int depth=platform ? 0 : 10+context.random().nextInt(5);
        if (!platform) {
            int unsupported=0;
            for (int n=0;n<16;n++) {
                double a=n*Math.PI/8;
                int h=context.chunkGenerator().getFirstOccupiedHeight(x+(int)Math.round(Math.cos(a)*radius),z+(int)Math.round(Math.sin(a)*radius),Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
                if(h<y-5) unsupported++;
            }
            if(unsupported>4) return Optional.empty();
            for(int dx=-radius;dx<=radius;dx+=3) for(int dz=-radius;dz<=radius;dz+=3) {
                double q=(dx*dx+dz*dz)/(double)(radius*radius);
                if(q>0.9) continue;
                int bottom=y-(int)Math.round(depth*(1-q))-4;
                for (int[] cavity : new int[][]{{-9, 0}, {9, 0}, {0, 10}}) {
                    int dd=(dx-cavity[0])*(dx-cavity[0])+(dz-cavity[1])*(dz-cavity[1]);
                    if(dd<=64) bottom=Math.min(bottom,y-depth-6-(int)Math.ceil(Math.sqrt(64-dd))-4);
                }
                NoiseColumn column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
                for(int yy=bottom;yy<=y-(int)Math.round(depth*(1-q));yy++) if(!column.getBlock(yy).is(Blocks.END_STONE)) return Optional.empty();
            }
        }
        int height=platform ? y+20+context.random().nextInt(11) : y;
        BlockPos pos=new BlockPos(x,height,z);
        long seed=context.random().nextLong();
        Rotation rotation=Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(new BlockPos(x,y,z),builder -> {
            builder.addPiece(new StarwreckPiece(pos,y,radius,depth,platform,seed));
            if(!platform) builder.addPiece(new StarwreckMeteorPiece(context.structureTemplateManager(),new BlockPos(x-8,y-depth-1,z-8),rotation));
        }));
    }
    @Override
    public StructureType<?> type() { return StarwreckWorldgen.STRUCTURE.get(); }
}
