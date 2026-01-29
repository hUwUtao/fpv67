package com.iung.fpv20.mixin.client;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("lastX")
    void fpv20$setLastX(double value);

    @Accessor("lastY")
    void fpv20$setLastY(double value);

    @Accessor("lastZ")
    void fpv20$setLastZ(double value);

    @Accessor("lastRenderX")
    void fpv20$setLastRenderX(double value);

    @Accessor("lastRenderY")
    void fpv20$setLastRenderY(double value);

    @Accessor("lastRenderZ")
    void fpv20$setLastRenderZ(double value);

    @Accessor("lastYaw")
    void fpv20$setLastYaw(float value);

    @Accessor("lastPitch")
    void fpv20$setLastPitch(float value);
}
