package org.hp.hp_end_expansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class StarwreckFeature extends Feature<StarwreckFeature.Settings> {
    public record Settings(String kind, int radiusMin, int radiusMax, int depthMin, int depthMax, double ember) implements FeatureConfiguration {
        public static final Codec<Settings> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(Settings::kind),
            Codec.intRange(0,15).optionalFieldOf("radius_min",0).forGetter(Settings::radiusMin),
            Codec.intRange(0,15).optionalFieldOf("radius_max",0).forGetter(Settings::radiusMax),
            Codec.intRange(0,9).optionalFieldOf("depth_min",0).forGetter(Settings::depthMin),
            Codec.intRange(0,9).optionalFieldOf("depth_max",0).forGetter(Settings::depthMax),
            Codec.doubleRange(0,1).optionalFieldOf("ember",0.0).forGetter(Settings::ember)
        ).apply(i,Settings::new));
    }
    public StarwreckFeature() { super(Settings.CODEC); }
    @Override
    public boolean place(FeaturePlaceContext<Settings> context) {
        WorldGenLevel level=context.level(); RandomSource random=context.random(); Settings settings=context.config();
        int minX=context.origin().getX() & ~15, minZ=context.origin().getZ() & ~15;
        BoundingBox clip=new BoundingBox(minX-16,level.getMinBuildHeight(),minZ-16,minX+31,level.getMaxBuildHeight()-1,minZ+31);
        StarwreckTerrain terrain=new StarwreckTerrain(level,clip,level.getSeed());
        if (settings.kind().equals("surface")) {
            StarwreckNoise noise=new StarwreckNoise(level.getSeed() ^ 0x4D4F5353L);
            for (int x=minX;x<minX+16;x++) for (int z=minZ;z<minZ+16;z++) {
                int y=StarwreckTerrain.surface(level,x,z);
                BlockPos top=new BlockPos(x,y,z);
                if (y<30 || !level.getBiome(top).is(StarwreckWorldgen.BIOME)) continue;
                int thickness=4+(int)(StarwreckNoise.unit(level.getSeed(),x,17,z)*3);
                for (int d=0;d<=thickness;d++) {
                    if (!level.getBlockState(top.below(d)).is(Blocks.END_STONE)) break;
                    BlockState state=d==0 && noise.sample(x/22.0,z/22.0)<0.61 ? ModStarwreck.STAR_MOSS.get().defaultBlockState() : ModStarwreck.STARWRECK_STONE.get().defaultBlockState();
                    terrain.put(x,y-d,z,state);
                }
            }
            return true;
        }
        int x=minX+8,z=minZ+8,y=StarwreckTerrain.surface(level,x,z);
        BlockPos center=new BlockPos(x,y,z);
        if (y<30 || !level.getBiome(center).is(StarwreckWorldgen.BIOME)) return false;
        if (settings.kind().equals("crater")) {
            int radius=settings.radiusMin()+random.nextInt(settings.radiusMax()-settings.radiusMin()+1);
            int depth=settings.depthMin()+random.nextInt(settings.depthMax()-settings.depthMin()+1);
            if (!StarwreckTerrain.safeCrater(level,center,radius,depth,radius>=11)) return false;
            terrain.crater(center,radius,depth,settings.ember(),false);
            if (radius>=11) terrain.nest(center.below(depth+6).offset(-3,0,0),3);
            if (radius>=7) terrain.meteor(center.below(depth).offset(radius>=11 ? 3 : 0,0,0),false);
            for (int n=0;n<random.nextInt(4);n++) {
                int px=x+random.nextInt(radius*2-1)-radius+1,pz=z+random.nextInt(radius*2-1)-radius+1;
                int py=StarwreckTerrain.surface(level,px,pz);
                if (level.getBlockState(new BlockPos(px,py,pz)).is(ModStarwreck.EMBER_STARWRECK_STONE.get()) && level.isEmptyBlock(new BlockPos(px,py+1,pz)))
                    terrain.put(px,py+1,pz,StarwreckTerrain.crystal(random.nextInt(4)).defaultBlockState());
            }
            return true;
        }
        if (settings.kind().equals("meteor")) { terrain.meteor(center,true); return true; }
        if (settings.kind().equals("vegetation")) {
            for (int group=0;group<3;group++) {
                int gx=minX+random.nextInt(16),gz=minZ+random.nextInt(16);
                if (group==2) {
                    for (int probe=0;probe<16;probe++) {
                        int px=minX+random.nextInt(16),pz=minZ+random.nextInt(16),py=StarwreckTerrain.surface(level,px,pz);
                        if (!level.getBlockState(new BlockPos(px,py,pz)).is(ModStarwreck.STAR_MOSS.get())) continue;
                        boolean rim=false;
                        for (Direction dir:Direction.Plane.HORIZONTAL) {
                            int ny=StarwreckTerrain.surface(level,px+dir.getStepX()*2,pz+dir.getStepZ()*2);
                            if (ny<=py-2 && ny>=py-8) rim=true;
                        }
                        if (rim) {gx=px;gz=pz;break;}
                    }
                }
                int attempts=group==2 ? 10 : 20, remaining=group==2 ? 2+random.nextInt(3) : 6+random.nextInt(5);
                for (int n=0;n<attempts && remaining>0;n++) {
                    int px=gx+random.nextInt(7)-3,pz=gz+random.nextInt(7)-3,py=StarwreckTerrain.surface(level,px,pz);
                    BlockPos soil=new BlockPos(px,py,pz);
                    if (level.getBiome(soil).is(StarwreckWorldgen.BIOME) && level.getBlockState(soil).is(ModStarwreck.STAR_MOSS.get()) && level.isEmptyBlock(soil.above())) {
                        terrain.put(px,py+1,pz,(group==2 ? ModStarwreck.EMBERBLOOM.get() : ModStarwreck.STAR_MOSS_SPROUTS.get()).defaultBlockState()); remaining--;
                    }
                }
            }
            if (random.nextInt(4)==0) {
                int r=3+random.nextInt(3),cy=StarwreckTerrain.surface(level,x,z);
                for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
                    int sy=StarwreckTerrain.surface(level,x+dx,z+dz);
                    BlockPos p=new BlockPos(x+dx,sy,z+dz);
                    if(dx*dx+dz*dz<=r*r && sy<=cy && sy>=cy-2 && level.getBiome(p).is(StarwreckWorldgen.BIOME) && level.isEmptyBlock(p.above()) && terrain(level.getBlockState(p.below()))) terrain.put(x+dx,sy,z+dz,ModStarwreck.METEOR_ASH.get().defaultBlockState());
                }
            }
            return true;
        }
        return false;
    }
    private static boolean terrain(BlockState state) { return StarwreckTerrain.terrain(state); }
}
