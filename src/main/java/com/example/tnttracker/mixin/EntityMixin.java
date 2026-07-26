package com.example.tnttracker.mixin;

import com.example.tnttracker.TntTrackerMod;
import com.example.tnttracker.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "move", at = @At("RETURN"))
    private void recordMovementStep(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof TntEntity tnt)) return;
        if (self.getWorld().isClient()) return;

        // Use ModConfig instead of TntTrackerMod
        if (!ModConfig.showTntPositions) return;

        TntTrackerMod.recordPosition(tnt, new Vec3d(tnt.getX(), tnt.getY(), tnt.getZ()));
    }
}