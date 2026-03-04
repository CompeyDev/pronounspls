package xyz.devcomp.pronounspls.mixin;

import java.util.Optional;

import xyz.devcomp.pronounspls.PronounsPlease;
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
            // TODO: Config option for where to place pronouns, command to set pronouns color per-player
            ServerPlayerEntity recipient = (ServerPlayerEntity) (Object) this;
            String language = recipient.getClientOptions().language();

            // Extract pronoun key from targetName carrier set by `PlayerManagerMixin`
            String pronounKey = params.targetName().map(Text::getString).orElse(null);
            if (pronounKey == null) return params;

            String translated = PronounsTranslationManager.INSTANCE.translate(language, pronounKey);
            Text pronounText = Text.literal(translated).formatted(Formatting.DARK_PURPLE);

            switch (messageTypeId.toString()) {
                case "minecraft:msg_command_outgoing", "minecraft:team_msg_command_outgoing" -> {
                    // Message includes a target; must be an outgoing whisper or similar, so we use the regular type
                    PronounsPlease.LOGGER.debug("Whisper detected: {}, {}", params.targetName(), params.type().getIdAsString());
                    Text nameWithPronouns = MutableText.of(params.name().getContent())
                        .append(pronounText)
                        .formatted(Formatting.ITALIC);

                    return new MessageType.Parameters(params.type(), nameWithPronouns, Optional.empty());
                }

                case "minecraft:chat" -> {
                    RegistryEntry<MessageType> messageType = recipient
                        .getRegistryManager()
                        .getOrThrow(RegistryKeys.MESSAGE_TYPE)
                        .getEntry(PronounsPlease.PRONOUNS_MESSAGE_TYPE_ID)
                        .orElseThrow();

                    // Regular chat messages never have a target, so we can use it to store our pronoun and use a custom type
                    return new MessageType.Parameters(messageType, params.name(), Optional.of(pronounText));
                }
            }
        }

        // No-op for incoming whispers, say command, emote command, etc.
        return params;
    }
}
