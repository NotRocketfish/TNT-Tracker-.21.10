package com.example.tnttracker.mixin;

import com.example.tnttracker.TntRespawnManager;
import com.example.tnttracker.config.ModConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntEntity.class)
public class TntEntityMixin {
    @Unique
    private BlockPos tnttracker$originalPosition;

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void captureConstructorPosition(World world, double x, double y, double z, LivingEntity igniter,
                                            CallbackInfo ci) {
        captureOriginalPosition();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void captureFirstTickPosition(CallbackInfo ci) {
        captureOriginalPosition();
    }

    @Inject(method = "explode", at = @At("HEAD"))
    private void scheduleRespawn(CallbackInfo ci) {
        TntEntity self = (TntEntity)(Object)this;
        World world = self.getWorld();
        if (world.isClient || !ModConfig.respawnTnt || tnttracker$originalPosition == null) {
            return;
        }

        TntRespawnManager.schedule(tnttracker$originalPosition, world, ModConfig.respawnTntCooldownTicks);
        tnttracker$originalPosition = null;
    }

    @Unique
    private void captureOriginalPosition() {
        if (tnttracker$originalPosition != null) {
            return;
        }

        TntEntity self = (TntEntity)(Object)this;
        World world = self.getWorld();
        if (world.isClient) {
            return;
        }

        tnttracker$originalPosition = new BlockPos(
                (int)Math.floor(self.getX()),
                (int)Math.floor(self.getY()),
                (int)Math.floor(self.getZ())
        );
    }
}
