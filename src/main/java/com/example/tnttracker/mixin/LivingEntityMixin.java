package com.example.tnttracker.mixin;

import com.example.tnttracker.config.ModConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void cancelExplosionDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // Check if the damage is from an explosion using the registry key
        if (ModConfig.safeMode && source.getTypeRegistryEntry().matchesKey(DamageTypes.EXPLOSION)) {
            cir.setReturnValue(false);
        }
    }
}