package xyz.devcomp.pronounspls.mixin;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsTeamManager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {
    @Final @Shadow
    private Entity entity;

    @Inject(method = "addPairing(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("TAIL"))
    private void onStartTracking(ServerPlayer player, CallbackInfo ci) {
        // Tamed entities inherit their owner's team prefix on the client, so we
        // assign them to their own empty dummy team to prevent this

        if (!(entity instanceof TamableAnimal tamable) || !tamable.isTame()) return;

        PronounsTeamManager.INSTANCE.trackPet(tamable.getUUID());
        PronounsTeamManager.INSTANCE.assignPetDummyTeam(tamable.getStringUUID(), player);
        PronounsPlease.LOGGER.debug("Tracked tamed entity for dummy team: {}", tamable.getPlainTextName());
    }

    @Inject(method = "removePairing(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("TAIL"))
    private void onStopTracking(ServerPlayer player, CallbackInfo ci) {
        // Clean up the dummy team when the entity is no longer tracked

        if (!(entity instanceof TamableAnimal tameable) || !tameable.isTame()) return;

        PronounsTeamManager.INSTANCE.untrackPet(tameable.getUUID());
        PronounsTeamManager.INSTANCE.removePetDummyTeam(tameable.getStringUUID(), player);
        PronounsPlease.LOGGER.debug("Untracked tamed entity from dummy team: {}", tameable.getPlainTextName());
    }
}
