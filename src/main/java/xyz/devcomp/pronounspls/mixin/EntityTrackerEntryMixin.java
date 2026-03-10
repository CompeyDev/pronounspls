package xyz.devcomp.pronounspls.mixin;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsTeamManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {
    @Final @Shadow
    private Entity entity;

    @Inject(method = "startTracking", at = @At("TAIL"))
    private void onStartTracking(ServerPlayerEntity player, CallbackInfo ci) {
        // Tamed entities inherit their owner's team prefix on the client, so we
        // assign them to their own empty dummy team to prevent this

        if (!(entity instanceof TameableEntity tameable) || !tameable.isTamed()) return;

        PronounsTeamManager.INSTANCE.trackPet(tameable.getUuid());
        PronounsTeamManager.INSTANCE.assignPetDummyTeam(tameable.getUuidAsString(), player);
        PronounsPlease.LOGGER.debug("Tracked tamed entity for dummy team: {}", tameable.getStringifiedName());
    }

    @Inject(method = "stopTracking", at = @At("TAIL"))
    private void onStopTracking(ServerPlayerEntity player, CallbackInfo ci) {
        // Clean up the dummy team when the entity is no longer tracked

        if (!(entity instanceof TameableEntity tameable) || !tameable.isTamed()) return;

        PronounsTeamManager.INSTANCE.untrackPet(tameable.getUuid());
        PronounsTeamManager.INSTANCE.removePetDummyTeam(tameable.getUuidAsString(), player);
        PronounsPlease.LOGGER.debug("Untracked tamed entity from dummy team: {}", tameable.getStringifiedName());
    }
}
