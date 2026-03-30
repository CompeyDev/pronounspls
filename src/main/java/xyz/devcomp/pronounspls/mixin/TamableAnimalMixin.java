package xyz.devcomp.pronounspls.mixin;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsTeamManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public class TamableAnimalMixin {
    @Inject(method = "setOwner*", at = @At("TAIL"))
    private void onSetOwner(CallbackInfo ci) {
        // When a newly tamed entity is renamed, we need to emit packets relocating it into
        // its own team in order to prevent it from inheriting its owner's pronouns prefix

        //noinspection ConstantConditions
        TamableAnimal tameable = (TamableAnimal) (Object) this;

        //noinspection resource
        if (!(tameable.level() instanceof Level serverLevel)) return;
        if (!tameable.isTame()) return;

        String entityUuid = tameable.getStringUUID();
        PronounsTeamManager.INSTANCE.trackPet(tameable.getUUID());

        serverLevel.players().forEach(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                PronounsTeamManager.INSTANCE.assignPetDummyTeam(entityUuid, serverPlayer);
                PronounsPlease.LOGGER.debug("Assigned dummy team for tamed entity: {} to {}", tameable.getPlainTextName(), serverPlayer.getPlainTextName());
            }
        });
    }
}
