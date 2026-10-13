package org.hp.hp_end_expansion.client.render;

import com.geckolib.cache.model.BakedGeoModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class HpBakedModel {
    final Map<String, HpBone> bones=new LinkedHashMap<>();
    HpBakedModel(BakedGeoModel model) { model.boneLookup().get().forEach((name,bone)->bones.put(name,new HpBone(bone))); }
    public Optional<HpBone> getBone(String name) { return Optional.ofNullable(bones.get(name)); }
    public HpBone bone(String name) { return bones.get(name); }
}
