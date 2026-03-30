package xyz.devcomp.pronounspls.mixin;

import java.util.Optional;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.jetbrains.annotations.Nullable;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @WrapOperation(
        method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
        at = @At(
            value = "INVOKE",
            target = "net/minecraft/server/level/ServerPlayer.sendChatMessage (Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"
        )
    )
    private void redirectSendChatMessage(
        ServerPlayer recipient,
        OutgoingChatMessage sentMessage,
        boolean filterMaskEnabled,
        ChatType.Bound bound,
        Operation<Void> original,
        @Local(argsOnly = true) @Nullable ServerPlayer sender
    ) {
        if (sender == null) {
            original.call(recipient, sentMessage, filterMaskEnabled, bound);
            return;
        }

        // Embed sender UUID into targetName as a carrier
        ChatType.Bound newParams = new ChatType.Bound(
            bound.chatType(),
            bound.name(),
            Optional.of(Component.literal(sender.getStringUUID()))
        );

        original.call(recipient, sentMessage, filterMaskEnabled, newParams);
    }
}
