package xyz.devcomp.pronounspls.mixin;

import java.util.Optional;

import xyz.devcomp.pronounspls.PronounsTeamManager;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.jetbrains.annotations.Nullable;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Redirect(
        method = "onPlayerConnect",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V"
        )
    )
    private void redirectPlayerListBroadcast(PlayerManager instance, Packet<?> packet, ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData) {
        // TODO: Offload player list refreshing to our own implementation and cancel the vanilla one.
        //       We do not use our own player list packets and rely on team suffixes for now, but we
        //       should for more customizability, see `PronounsPlease.refreshPlayerList`
        instance.sendToAll(packet);
    }


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

        String pronounKey = PronounsTeamManager.getPronounsKey(sender).orElse(null);
        if (pronounKey == null) {
            original.call(recipient, sentMessage, filterMaskEnabled, params);
            return;
        }

        // Embed pronoun key into targetName as a carrier
        MessageType.Parameters newParams = new MessageType.Parameters(
            params.type(),
            params.name(),
            Optional.of(Text.literal(pronounKey))
        );

        original.call(recipient, sentMessage, filterMaskEnabled, newParams);
    }
}
