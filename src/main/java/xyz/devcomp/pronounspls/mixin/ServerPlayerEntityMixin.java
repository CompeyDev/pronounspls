package xyz.devcomp.pronounspls.mixin;

import java.util.Optional;
import java.util.UUID;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsPrideFlag;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.PronounsTranslationManager;

import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.network.message.MessageType;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @ModifyVariable(
        method = "sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private MessageType.Parameters overrideChatMessageName(MessageType.Parameters params) {
        Identifier messageTypeId = params
            .type()
            .getKey()
            .map(RegistryKey::getValue)
            .orElse(null);

        if (messageTypeId != null) {
            ServerPlayerEntity recipient = (ServerPlayerEntity) (Object) this;
            String language = recipient.getClientOptions().language();

            switch (messageTypeId.toString()) {
                case "minecraft:msg_command_outgoing", "minecraft:team_msg_command_outgoing" -> {
                    // Message includes a target; must be an outgoing whisper or similar, so we use the regular type
                    PronounsPlease.LOGGER.debug("Whisper detected: {}, {}", params.targetName(), params.type().getIdAsString());
                    return params;
                }

                case "minecraft:chat" -> {
                    RegistryEntry<MessageType> messageType = recipient
                        .getRegistryManager()
                        .getOrThrow(RegistryKeys.MESSAGE_TYPE)
                        .getEntry(PronounsPlease.PRONOUNS_MESSAGE_TYPE_ID)
                        .orElseThrow();

                    // Extract the sender's UUID from targetName carrier set by `PlayerManagerMixin`
                    UUID senderUuid = params.targetName()
                        .map(Text::getString)
                        .map(UUID::fromString)
                        .orElse(null);

                    if (senderUuid == null) return params;

                    String pronounsKey = PronounsTeamManager.INSTANCE.getPronounsKey(senderUuid).orElse(null);
                    PronounsPrideFlag prideFlag = PronounsTeamManager.INSTANCE.getPrideFlag(senderUuid).orElse(null);

                    if (pronounsKey == null)
                        return params;

                    String translated = PronounsTranslationManager.INSTANCE.translate(language, pronounsKey);
                    Text pronounText = prideFlag != null ? prideFlag.apply(translated) : Text.literal(translated).formatted(Formatting.GRAY);

                    // Regular chat messages never have a target, so we can use it to store our pronoun and use a custom type
                    return new MessageType.Parameters(messageType, params.name(), Optional.of(pronounText));
                }
            }
        }

        // No-op for incoming whispers, say command, emote command, etc.
        return params;
    }
}
