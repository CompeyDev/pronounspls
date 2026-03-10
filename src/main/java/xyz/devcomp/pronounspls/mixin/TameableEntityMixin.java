package xyz.devcomp.pronounspls.mixin;

import xyz.devcomp.pronounspls.PronounsPlease;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.devcomp.pronounspls.PronounsTeamManager;

@Mixin(TameableEntity.class)
public class TameableEntityMixin {
    @Inject(method = "setOwner*", at = @At("TAIL"))
    private void onSetOwner(CallbackInfo ci) {
        // When a newly tamed entity is renamed, we need to emit packets relocating it into
        // its own team in order to prevent it from inheriting its owner's pronouns prefix

        TameableEntity tameable = (TameableEntity)(Object) this;
        if (!(tameable.getEntityWorld() instanceof ServerWorld serverWorld)) return;
        if (!tameable.isTamed()) return;

        String entityUuid = tameable.getUuidAsString();
        PronounsTeamManager.INSTANCE.trackPet(tameable.getUuid());

        serverWorld.getPlayers().forEach(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                PronounsTeamManager.INSTANCE.assignPetDummyTeam(entityUuid, serverPlayer);
                PronounsPlease.LOGGER.debug("Assigned dummy team for tamed entity: {} to {}", tameable.getStringifiedName(), serverPlayer.getStringifiedName());
            }
        });
    }
}
