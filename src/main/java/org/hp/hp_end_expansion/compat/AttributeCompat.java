package org.hp.hp_end_expansion.compat;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class AttributeCompat {
    public static void permanentModifier(AttributeInstance attribute, AttributeModifier modifier) {
        attribute.removeModifier(modifier.getId());
        attribute.addPermanentModifier(modifier);
    }

    public static void transientModifier(AttributeInstance attribute, AttributeModifier modifier) {
        attribute.removeModifier(modifier.getId());
        attribute.addTransientModifier(modifier);
    }
}
