package org.hp.hp_end_expansion.entity.tidelight;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.*;
import software.bernie.geckolib.animation.*;

public final class TidebreakerShrimpEntity extends Monster implements GeoEntity {
    public static final int NONE=0, COMBO=1, DASH=2, BUBBLE=3, GUARD=4;
    private static final int[] LENGTH={0,24,30,28,20}, COOLDOWN={0,70,140,180,200};
    private static final EntityDataAccessor<Integer> SKILL=data(EntityDataSerializers.INT), START=data(EntityDataSerializers.INT), SEQUENCE=data(EntityDataSerializers.INT), BUBBLE_STATE=data(EntityDataSerializers.INT), BURST_TIME=data(EntityDataSerializers.INT), GUARD_TIME=data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW=data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> ORIGIN=data(EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> POINT=data(EntityDataSerializers.VECTOR3), PREVIOUS=data(EntityDataSerializers.VECTOR3), LEFT=data(EntityDataSerializers.VECTOR3), RIGHT=data(EntityDataSerializers.VECTOR3);
    private static <T> EntityDataAccessor<T> data(EntityDataSerializer<T> serializer) { return SynchedEntityData.defineId(TidebreakerShrimpEntity.class,serializer); }
    private static RawAnimation play(String name) { return RawAnimation.begin().thenPlay("animation.tidebreaker_shrimp."+name); }
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.tidebreaker_shrimp.idle"), WALK=RawAnimation.begin().thenLoop("animation.tidebreaker_shrimp.walk"), DEATH=RawAnimation.begin().thenPlayAndHold("animation.tidebreaker_shrimp.death");
    private static final RawAnimation HURT=play("hurt");
    private static final RawAnimation[] ACTIONS={IDLE,play("punch_combo"),play("dash_punch"),play("bubble_punch"),play("guard")};
    private final AnimatableInstanceCache cache=new SingletonAnimatableInstanceCache(this);
    private final int[] cooldowns=new int[5];
    private final Set<Integer> hitIds=new HashSet<>();
    private int pause=20, seenSequence=-1, lostSight, lastHurtTick=-100, recentHits;
    private Vec3 bubbleDirection=Vec3.ZERO, previousFist;
    private double bubbleTravel;

    public TidebreakerShrimpEntity(EntityType<? extends TidebreakerShrimpEntity> type,Level level) { super(type,level); xpReward=8; }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.ARMOR,4).add(Attributes.MOVEMENT_SPEED,.26).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.FOLLOW_RANGE,12).add(Attributes.KNOCKBACK_RESISTANCE,.35);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(SKILL,0);b.define(START,0);b.define(SEQUENCE,0);b.define(YAW,0F);b.define(ORIGIN,BlockPos.ZERO);
        b.define(BUBBLE_STATE,0);b.define(BURST_TIME,0);b.define(GUARD_TIME,-1000);
        b.define(POINT,new Vector3f());b.define(PREVIOUS,new Vector3f());b.define(LEFT,new Vector3f());b.define(RIGHT,new Vector3f());
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.8) {
            @Override public boolean canUse() { return skill()==NONE&&getTarget()==null&&super.canUse(); }
            @Override public boolean canContinueToUse() { return skill()==NONE&&getTarget()==null&&super.canContinueToUse(); }
        });
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
    }
    public int skill() { return entityData.get(SKILL); }
    public float skillAge(float partial) { return Math.max(0,(int)level().getGameTime()-entityData.get(START)+partial); }
    public float aimYaw() { return entityData.get(YAW); }
    public Vec3 forward() { double a=Math.toRadians(aimYaw());return new Vec3(-Math.sin(a),0,Math.cos(a)); }
    public Vec3 fist(int side,float age) { return TidebreakerMotion.fist(skill(),side,age,aimYaw()); }
    private Vec3 origin() { return Vec3.atLowerCornerOf(entityData.get(ORIGIN)); }
    private Vec3 read(EntityDataAccessor<Vector3f> key) { Vector3f v=entityData.get(key);return origin().add(v.x,v.y,v.z); }
    private void write(EntityDataAccessor<Vector3f> key,Vec3 point) { Vec3 v=point.subtract(origin());entityData.set(key,new Vector3f((float)v.x,(float)v.y,(float)v.z)); }
    public Vec3 impact(int side) { return read(side==0?LEFT:RIGHT); }
    public int bubbleState() { return entityData.get(BUBBLE_STATE); }
    public Vec3 bubblePoint(float pt) { return bubbleState()==1?read(PREVIOUS).lerp(read(POINT),pt):read(POINT); }
    public float burstAge(float pt) { return (int)level().getGameTime()-entityData.get(BURST_TIME)+pt; }
    public float guardHitAge(float pt) { return (int)level().getGameTime()-entityData.get(GUARD_TIME)+pt; }
    @Override public void tick() {
        super.tick();
        if(skill()!=NONE&&!isDeadOrDying()) { setYRot(aimYaw());yBodyRot=aimYaw();yHeadRot=aimYaw(); }
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if(isDeadOrDying())return;
        for(int i=1;i<5;i++)if(cooldowns[i]>0)cooldowns[i]--;
        if(pause>0)pause--;
        tickBubble();
        LivingEntity target=getTarget();
        if(target!=null&&(!canHit(target)||distanceToSqr(target)>256||(!hasLineOfSight(target)&&++lostSight>100))) {setTarget(null);target=null;}
        if(target!=null&&hasLineOfSight(target))lostSight=0;
        if(skill()!=NONE) {
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            int age=(int)skillAge(0),s=skill();
            boolean tracking=s==COMBO?(age<7||age>=10&&age<13):s==DASH?age<10:s==BUBBLE&&age<14;
            if(target!=null&&tracking)aim(target);
            if(s==COMBO&&(age==9||age==15))punch(age==9?0:1,age==9?4:5,age==15);
            if(s==DASH&&age>=12&&age<=17)dash();
            if(s==BUBBLE&&age==14)launchBubble(target);
            if(age>=LENGTH[s]) { cooldowns[s]=COOLDOWN[s];entityData.set(SKILL,NONE);pause=16; }
            return;
        }
        if(target==null)return;
        double distance=distanceTo(target);
        if(pause==0&&hasLineOfSight(target)) {
            int next=distance<2.8&&cooldowns[COMBO]==0?COMBO:distance>2.8&&distance<6.5&&cooldowns[DASH]==0?DASH:distance<7&&cooldowns[BUBBLE]==0?BUBBLE:NONE;
            if(next!=NONE){start(next,target);return;}
        }
        getNavigation().moveTo(target,1.1);
    }
    private void aim(LivingEntity target) {
        Vec3 d=target.position().subtract(position());float wanted=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
        entityData.set(YAW,Mth.approachDegrees(aimYaw(),wanted,12));
    }
    private void start(int action,LivingEntity target) {
        entityData.set(ORIGIN,blockPosition());entityData.set(YAW,getYRot());entityData.set(SKILL,action);entityData.set(START,(int)level().getGameTime());entityData.set(SEQUENCE,entityData.get(SEQUENCE)+1);
        entityData.set(BUBBLE_STATE,0);hitIds.clear();previousFist=null;getNavigation().stop();if(target!=null)aim(target);
    }
    private boolean canHit(LivingEntity e) {return e!=this&&e.isAlive()&&!isAlliedTo(e)&&!(e instanceof TidebreakerShrimpEntity)&&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    private Vec3 clip(Vec3 a,Vec3 b) { return level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getLocation(); }
    private void strike(Vec3 a,Vec3 b,float damage,boolean second) {
        for(LivingEntity e:level().getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(.35),this::canHit)) {
            if(e.getBoundingBox().inflate(.35).clip(a,b).isEmpty()&&!e.getBoundingBox().inflate(.35).contains(a))continue;
            if(clip(a,e.getBoundingBox().getCenter()).distanceToSqr(e.getBoundingBox().getCenter())>.01)continue;
            if(skill()==DASH&&hitIds.contains(e.getId()))continue;
            if(second&&hitIds.contains(e.getId())&&e.getLastHurtByMob()==this)e.invulnerableTime=0;
            if(e.hurt(damageSources().mobAttack(this),damage)){hitIds.add(e.getId());e.setDeltaMovement(e.getDeltaMovement().add(forward().scale(.2)).add(0,.06,0));e.hurtMarked=true;}
        }
    }
    private void punch(int side,float damage,boolean second) {
        Vec3 a=reachableFist(side),b=clip(a,a.add(forward().scale(1.15)));
        write(side==0?LEFT:RIGHT,b);strike(a,b,damage,second);playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.9F,side==0?1.35F:1.05F);
    }
    private void dash() {
        Vec3 move=forward().scale(.48),next=position().add(move);
        boolean supported=true;
        for(double side:new double[]{-.45,0,.45}) {
            Vec3 p=next.add(forward().scale(.65)).add(forward().z*side,.25,-forward().x*side);
            if(level().clip(new ClipContext(p,p.add(0,-1.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS)supported=false;
        }
        if(supported&&level().noCollision(this,getBoundingBox().move(move)))setDeltaMovement(move.x,getDeltaMovement().y,move.z);
        Vec3 a=reachableFist(1),b=clip(a,a.add(forward().scale(.8)));
        strike(previousFist==null?a:previousFist,b,7,false);previousFist=a;write(RIGHT,b);
        if((int)skillAge(0)==12)playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK,1F,.7F);
    }
    private void launchBubble(LivingEntity target) {
        Vec3 a=reachableFist(1);
        bubbleDirection=(target==null?forward():target.getBoundingBox().getCenter().subtract(a).normalize());bubbleTravel=0;
        write(POINT,a);write(PREVIOUS,a);entityData.set(BUBBLE_STATE,1);playSound(SoundEvents.GENERIC_SPLASH,.8F,1.6F);
    }
    private Vec3 reachableFist(int side) {
        Vec3 body=position().add(0,.5,0),wanted=position().add(fist(side,skillAge(0))),actual=clip(body,wanted);
        return actual.distanceToSqr(wanted)>.0001?actual.subtract(wanted.subtract(body).normalize().scale(.02)):wanted;
    }
    private void tickBubble() {
        if(bubbleState()==2){if(burstAge(0)>7)entityData.set(BUBBLE_STATE,0);return;}
        if(bubbleState()!=1)return;
        Vec3 a=read(POINT),end=a.add(bubbleDirection.scale(Math.min(.9,6-bubbleTravel))),b=clip(a,end);
        boolean collision=b.distanceToSqr(end)>.0001;
        for(LivingEntity e:level().getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(.22),this::canHit)) {
            AABB box=e.getBoundingBox().inflate(.22);Vec3 hit=box.contains(a)?a:box.clip(a,b).orElse(null);
            if(hit!=null&&hit.distanceToSqr(a)<=b.distanceToSqr(a)){b=hit;collision=true;}
        }
        write(PREVIOUS,a);write(POINT,b);bubbleTravel+=a.distanceTo(b);
        if(collision||bubbleTravel>=5.999) {
            entityData.set(BUBBLE_STATE,2);entityData.set(BURST_TIME,(int)level().getGameTime());
            Vec3 center=b.subtract(bubbleDirection.scale(.03));
            for(LivingEntity e:level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(1.1),this::canHit)) {
                AABB box=e.getBoundingBox();Vec3 closest=new Vec3(Mth.clamp(center.x,box.minX,box.maxX),Mth.clamp(center.y,box.minY,box.maxY),Mth.clamp(center.z,box.minZ,box.maxZ));
                if(center.distanceToSqr(closest)<=1.21&&clip(center,e.getBoundingBox().getCenter()).distanceToSqr(e.getBoundingBox().getCenter())<.01)e.hurt(damageSources().mobAttack(this),6);
            }
            playSound(SoundEvents.GENERIC_EXPLODE.value(),.6F,1.8F);
        }
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(!level().isClientSide&&skill()==GUARD&&skillAge(0)>=3&&skillAge(0)<=14&&source.getSourcePosition()!=null) {
            Vec3 d=source.getSourcePosition().subtract(position());d=new Vec3(d.x,0,d.z).normalize();
            if(d.dot(forward())>Math.cos(Math.toRadians(50))){amount*=.6F;entityData.set(GUARD_TIME,(int)level().getGameTime());}
        }
        boolean accepted=super.hurt(source,amount);
        if(accepted&&!level().isClientSide&&!isDeadOrDying()&&source.getEntity() instanceof LivingEntity attacker&&canHit(attacker)) {
            recentHits=tickCount-lastHurtTick<40?recentHits+1:1;lastHurtTick=tickCount;
            if(recentHits>=2&&skill()==NONE&&cooldowns[GUARD]==0){start(GUARD,attacker);recentHits=0;}
        }
        return accepted;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SyncedController(this,state->{
            if(isDeadOrDying())return state.setAndContinue(DEATH);
            if(seenSequence!=entityData.get(SEQUENCE)){seenSequence=entityData.get(SEQUENCE);state.getController().forceAnimationReset();}
            return state.setAndContinue(skill()==NONE?(hurtTime>0?HURT:state.isMoving()?WALK:IDLE):ACTIONS[skill()]);
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override protected void tickDeath() {
        deathTime++;
        if(deathTime>=30&&!level().isClientSide&&!isRemoved()) { level().broadcastEntityEvent(this,(byte)60);remove(RemovalReason.KILLED); }
    }
    private static final class SyncedController extends AnimationController<TidebreakerShrimpEntity> {
        SyncedController(TidebreakerShrimpEntity e,AnimationStateHandler<TidebreakerShrimpEntity> handler){super(e,"action",0,handler);}
        @Override protected double adjustTick(double tick){
            if(animatable.skill()!=NONE&&!animatable.isDeadOrDying()&&!shouldResetTick&&getAnimationState()==State.RUNNING)return animatable.skillAge((float)(tick-Math.floor(tick)));
            return super.adjustTick(tick);
        }
    }
}
