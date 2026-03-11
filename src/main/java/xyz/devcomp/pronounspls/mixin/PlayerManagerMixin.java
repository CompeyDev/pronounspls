package xyz.devcomp.pronounspls.mixin;

import java.util.Optional;

import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.jetbrains.annotations.Nullable;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @WrapOperation(
        method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
        at = @At(
            value = "INVOKE",
            target = "net/minecraft/server/network/ServerPlayerEntity.sendChatMessage (Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V"
        )
    )
    private void redirectSendChatMessage(
        ServerPlayerEntity recipient,
        SentMessage sentMessage,
        boolean filterMaskEnabled,
        MessageType.Parameters params,
        Operation<Void> original,
        @Local(argsOnly = true) @Nullable ServerPlayerEntity sender
    ) {
        if (sender == null) {
            original.call(recipient, sentMessage, filterMaskEnabled, params);
            return;
        }

        // Embed sender UUID into targetName as a carrier
        MessageType.Parameters newParams = new MessageType.Parameters(
            params.type(),
            params.name(),
            Optional.of(Text.literal(sender.getUuidAsString()))
        );

        original.call(recipient, sentMessage, filterMaskEnabled, newParams);
    }
}
