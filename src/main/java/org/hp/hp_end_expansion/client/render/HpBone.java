package org.hp.hp_end_expansion.client.render;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import org.joml.Matrix4f;

public final class HpBone {
    final GeoBone bone;
    Boolean hidden, childrenHidden;
    Float x;
    boolean tracking;
    final Matrix4f localMatrix=new Matrix4f();
    HpBone(GeoBone bone) { this.bone=bone; }
    public void setHidden(boolean value) { hidden=value; }
    public void setChildrenHidden(boolean value) { childrenHidden=value; }
    public void setPosX(float value) { x=value; }
    public void setTrackingMatrices(boolean value) { tracking=value; }
    public Matrix4f getLocalSpaceMatrix() { return localMatrix; }
    void apply(BoneSnapshot snapshot) {
        if(hidden!=null) snapshot.skipRender(hidden);
        if(childrenHidden!=null) snapshot.skipChildrenRender(childrenHidden);
        if(x!=null) snapshot.setTranslateX(x);
    }
}
