package org.hp.hp_end_expansion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.hp.hp_end_expansion.entity.starwreck.MeteorTortoiseEntity;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

public final class StarwreckPiece extends StructurePiece {
    private final BlockPos center;
    private final int ground,radius,depth;
    private final boolean platform;
    private final long seed;
    private boolean spawned;
    public StarwreckPiece(BlockPos center,int ground,int radius,int depth,boolean platform,long seed) {
        super(StarwreckWorldgen.TERRAIN_PIECE.get(),0,new BoundingBox(center.getX()-radius,ground-depth-18,center.getZ()-radius,center.getX()+radius,center.getY()+8,center.getZ()+radius));
        this.center=center;this.ground=ground;this.radius=radius;this.depth=depth;this.platform=platform;this.seed=seed;
    }
    public StarwreckPiece(CompoundTag tag) {
        super(StarwreckWorldgen.TERRAIN_PIECE.get(),tag);
        center=new BlockPos(tag.getInt("CX"),tag.getInt("CY"),tag.getInt("CZ"));
        ground=tag.getInt("Ground");radius=tag.getInt("Radius");depth=tag.getInt("Depth");platform=tag.getBoolean("Platform");seed=tag.getLong("Seed");spawned=tag.getBoolean("Spawned");
    }
    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putInt("CX",center.getX());tag.putInt("CY",center.getY());tag.putInt("CZ",center.getZ());tag.putInt("Ground",ground);
        tag.putInt("Radius",radius);tag.putInt("Depth",depth);tag.putBoolean("Platform",platform);tag.putLong("Seed",seed);tag.putBoolean("Spawned",spawned);
    }
    @Override
    public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot) {
        StarwreckTerrain terrain=new StarwreckTerrain(level,clip,seed);
        int cx=center.getX(),cy=center.getY(),cz=center.getZ();
        if(platform) {
            for(int dx=-7;dx<=7;dx++) for(int dz=-7;dz<=7;dz++) {
                int q=dx*dx+dz*dz;
                if(q>56) continue;
                terrain.put(cx+dx,cy-1,cz+dz,ModStarwreck.STARWRECK_BRICKS.get().defaultBlockState());
                terrain.put(cx+dx,cy,cz+dz,q>42 ? ModStarwreck.STARWRECK_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM) : ModStarwreck.STARWRECK_BRICKS.get().defaultBlockState());
                if(Math.max(Math.abs(dx),Math.abs(dz))==1) terrain.put(cx+dx,cy,cz+dz,ModStarwreck.EMBER_STARWRECK_STONE.get().defaultBlockState());
            }
            terrain.put(cx,cy,cz,ModStarwreck.STAR_CRYSTAL_BLOCK.get().defaultBlockState());
            for(int dx:new int[]{-4,4}) for(int dz:new int[]{-4,4}) {
                for(int y=cy+1;y<=cy+4;y++) terrain.put(cx+dx,y,cz+dz,ModStarwreck.STARWRECK_BRICKS.get().defaultBlockState());
                terrain.put(cx+dx,cy+5,cz+dz,ModStarwreck.STARWRECK_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE,SlabType.DOUBLE));
            }
            for(int y=ground;y<cy;y++) terrain.put(cx, y, cz, ModStarwreck.STARWRECK_BRICKS.get().defaultBlockState());
            for(int step=0;step<=cy-ground;step++) {
                int index=step%16,dx,dz;
                if(index<4){dx=-2+index;dz=-2;}else if(index<8){dx=2;dz=-2+index-4;}else if(index<12){dx=2-(index-8);dz=2;}else{dx=-2;dz=2-(index-12);}
                terrain.put(cx+dx,ground+step,cz+dz,ModStarwreck.STARWRECK_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            }
            return;
        }
        terrain.crater(center,radius,depth,0.4,true);
        terrain.nest(center.offset(-9,-depth-6,0),3);
        terrain.nest(center.offset(9,-depth-6,0),3);
        if((seed & 1)==0) terrain.nest(center.offset(0,-depth-6,10),3);
        for(int n=0;n<6;n++) {
            double angle=n*Math.PI/3;
            terrain.meteor(center.offset((int)(Math.cos(angle)*(radius-3)),1,(int)(Math.sin(angle)*(radius-3))),true);
        }
        BlockPos spawn=center.offset(0,-depth+3,-9);
        if(!spawned && clip.isInside(spawn)) {
            // 陨壳龟趴在巨型残骸旁的坑底：从预定点向下找到第一块实心地面
            BlockPos floor=spawn;
            for(int i=0;i<12 && floor.getY()>level.getMinBuildHeight() && level.isEmptyBlock(floor.below());i++) floor=floor.below();
            for(int i=0;i<6 && !level.isEmptyBlock(floor);i++) floor=floor.above();
            MeteorTortoiseEntity tortoise=StarwreckEntities.METEOR_TORTOISE.get().create(level.getLevel());
            if(tortoise!=null) {
                tortoise.moveTo(floor.getX()+0.5,floor.getY(),floor.getZ()+0.5,level.getRandom().nextFloat()*360,0);
                tortoise.finalizeSpawn(level,level.getCurrentDifficultyAt(floor),MobSpawnType.STRUCTURE,null);
                tortoise.setPersistenceRequired();level.addFreshEntity(tortoise);spawned=true;
            }
        }
    }
}
