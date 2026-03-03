package xyz.devcomp.pronounsplus.mixin;

import java.util.Optional;

import xyz.devcomp.pronounsplus.PronounsPlus;

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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void overrideTabListName(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Text name = Text.literal("[they/them] ")
                .formatted(Formatting.GRAY)
                .append(player.getName());

        cir.setReturnValue(name);
    }

    @ModifyVariable(method = "sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V", at = @At("HEAD"), argsOnly = true)
    private MessageType.Parameters overrideChatMessageName(MessageType.Parameters params) {
        Identifier messageTypeId = params
                .type()
                .getKey()
                .map(RegistryKey::getValue)
                .orElse(null);

        if (messageTypeId != null) {
            // TODO: Config option for where to place pronouns, command to set pronouns color per-player

            switch (messageTypeId.toString()) {
                case "minecraft:msg_command_outgoing", "minecraft:team_msg_command_outgoing" -> {
                    // Message includes a target; must be an outgoing whisper or similar, so we use the regular type
                    PronounsPlus.LOGGER.debug("Whisper detected: {}, {}", params.targetName(), params.type().getIdAsString());
                    Text nameWithPronouns = MutableText.of(params.name().getContent())
                            .append(Text.literal(" (they/them)")
                            .formatted(Formatting.ITALIC));

                    return new MessageType.Parameters(params.type(), nameWithPronouns, params.targetName());
                }

                case "minecraft:chat" -> {
                    // Regular chat messages never have a target, so we can use it to store our pronoun and use a custom type
                    ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
                    RegistryEntry<MessageType> messageType = player
                            .getRegistryManager()
                            .getOrThrow(RegistryKeys.MESSAGE_TYPE)
                            .getEntry(PronounsPlus.PRONOUNS_MESSAGE_TYPE_ID)
                            .orElseThrow();

                    return new MessageType.Parameters(messageType, params.name(), Optional.of(Text.literal("they/them").formatted(Formatting.DARK_PURPLE)));
                }
            }
        }

        // No-op for incoming whispers, say command, emote command, etc.
        return params;
    }
}
