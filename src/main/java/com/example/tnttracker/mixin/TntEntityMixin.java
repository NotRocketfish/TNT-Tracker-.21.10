package com.example.tnttracker.mixin;

import com.example.tnttracker.TntRespawnManager;
import com.example.tnttracker.config.ModConfig;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(TntEntity.class)
public class TntEntityMixin {

    @Unique
    private static final Map<UUID, BlockPos> originalPositions = new HashMap<>();

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void captureOriginalPos(CallbackInfo ci) {
        TntEntity self = (TntEntity)(Object)this;
        World world = self.getWorld();
        if (world.isClient) return;

        BlockPos pos = new BlockPos((int)Math.floor(self.getX()), (int)Math.floor(self.getY()), (int)Math.floor(self.getZ()));
        originalPositions.put(self.getUuid(), pos);

    }

    @Inject(method = "explode", at = @At("HEAD"))
    private void scheduleRespawn(CallbackInfo ci) {
        TntEntity self = (TntEntity)(Object)this;
        World world = self.getWorld();
        if (world.isClient) return;

        if (!ModConfig.respawnTnt) return;

        BlockPos originalPos = originalPositions.remove(self.getUuid());
        if (originalPos == null) return;

        TntRespawnManager.schedule(originalPos, world, ModConfig.respawnTntCooldownTicks);
    }
}