package xyz.devcomp.pronounspls.mixin;

import java.util.Optional;
import java.util.UUID;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsPrideFlag;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.PronounsTranslationManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @ModifyVariable(
        method = "sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V\n",
        at = @At("HEAD"),
        argsOnly = true
    )
    private ChatType.Bound overrideChatMessageName(ChatType.Bound bound) {
        Identifier messageTypeId = bound
            .chatType()
            .unwrapKey()
            .map(ResourceKey::identifier)
            .orElse(null);

        if (messageTypeId != null) {
            //noinspection ConstantConditions
            ServerPlayer recipient = (ServerPlayer) (Object) this;
            String language = recipient.clientInformation().language();

            switch (messageTypeId.toString()) {
                case "minecraft:msg_command_outgoing", "minecraft:team_msg_command_outgoing" -> {
                    // Message includes a target; must be an outgoing whisper or similar, so we use the regular type
                    PronounsPlease.LOGGER.debug("Whisper detected: {}, {}", bound.targetName(), bound.chatType().getRegisteredName());
                    return bound;
                }

                case "minecraft:chat" -> {
                    @SuppressWarnings("NullableProblems")
                    Holder<ChatType> messageType = recipient
                        .registryAccess()
                        .lookupOrThrow(Registries.CHAT_TYPE)
                        .get(PronounsPlease.PRONOUNS_MESSAGE_TYPE_ID)
                        .orElseThrow();

                    // Extract the sender's UUID from targetName carrier set by `PlayerManagerMixin`
                    UUID senderUuid = bound.targetName()
                        .map(Component::getString)
                        .map(UUID::fromString)
                        .orElse(null);

                    if (senderUuid == null) return bound;

                    String pronounsKey = PronounsTeamManager.INSTANCE.getPronounsKey(senderUuid).orElse(null);
                    PronounsPrideFlag prideFlag = PronounsTeamManager.INSTANCE.getPrideFlag(senderUuid).orElse(null);

                    if (pronounsKey == null)
                        return bound;

                    String translated = PronounsTranslationManager.INSTANCE.translate(language, pronounsKey);
                    Component pronounText = prideFlag != null ? prideFlag.apply(translated) : Component.literal(translated).withStyle(ChatFormatting.GRAY);

                    // Regular chat messages never have a target, so we can use it to store our pronoun and use a custom type
                    return new ChatType.Bound(messageType, bound.name(), Optional.of(pronounText));
                }
            }
        }

        // No-op for incoming whispers, say command, emote command, etc.
        return bound;
    }
}
