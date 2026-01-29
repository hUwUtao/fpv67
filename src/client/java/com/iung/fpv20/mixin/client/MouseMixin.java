package com.iung.fpv20.mixin.client;

import com.iung.fpv20.replay.FpvReplayManager;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void fpv20$freezeMouseDuringReplay(double timeDelta, CallbackInfo ci) {
        if (FpvReplayManager.isReplaying()) {
            ci.cancel();
        }
    }
}
