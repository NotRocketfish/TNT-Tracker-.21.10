package com.example.tnttracker.mixin;

import com.example.tnttracker.ShotManager;
import com.example.tnttracker.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Final
    @Shadow private ObjectArrayList<BlockPos> affectedBlocks;
    @Final
    @Shadow private World world;
    @Shadow public abstract Entity getEntity();

    @Inject(method = "affectWorld", at = @At("HEAD"), cancellable = true)
    private void countDestroyedBlocks(boolean particles, CallbackInfo ci) {
        // 1. Safe Mode: Explosion komplett unterdrücken (keine Blockzerstörung, Partikel, Sound)
        if (ModConfig.safeMode) {
            ci.cancel();
            return;
        }

        // 2. Nur serverseitig weitermachen
        if (this.world.isClient) return;

        // 3. Nur TNT-Explosionen zählen
        Entity source = getEntity();
        if (!(source instanceof TntEntity)) return;

        // 4. Zerstörte Blöcke zählen (nur nicht-Luft-Blöcke)
        int destroyed = 0;
        for (BlockPos pos : affectedBlocks) {
            if (!this.world.getBlockState(pos).isAir()) {
                destroyed++;
            }
        }

        if (destroyed > 0) {
            ShotManager.addDestroyedBlocks(destroyed);
        }
    }
}