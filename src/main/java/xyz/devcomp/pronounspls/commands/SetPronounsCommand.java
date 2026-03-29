package xyz.devcomp.pronounspls.commands;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsCommandManager;
import xyz.devcomp.pronounspls.PronounsSource;
import xyz.devcomp.pronounspls.api.PronounDBClient;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.PronounsTranslationManager;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

// TODO: translations for command feedback

enum PronounKey implements StringRepresentable {
    HE("pronounspls.pronouns.he"),
    SHE("pronounspls.pronouns.she"),
    THEY("pronounspls.pronouns.they"),
    IT("pronounspls.pronouns.it"),
    ANY("pronounspls.pronouns.any"),
    ASK("pronounspls.pronouns.ask"),
    AVOID("pronounspls.pronouns.avoid"),
    OTHER("pronounspls.pronouns.other"),
    PRONOUNDB(null);

    private final String key;
    public static final Codec<PronounKey> CODEC = StringRepresentable.fromEnum(PronounKey::values);

    PronounKey(String key) { this.key = key; }

    @Override
    public @NotNull String getSerializedName() { return name().toLowerCase(); }

    public String getTranslationKey() { return key; }
}

class PronounKeyArgumentType {
    private static final DynamicCommandExceptionType INVALID_PRONOUN = new DynamicCommandExceptionType(
        value -> SetPronounsCommand.error(
            "Unknown pronoun ",
            Component.literal(value.toString()).withStyle(ChatFormatting.AQUA),
            null
        )
    );

    private static final List<String> PRONOUN_KEYS = Arrays.stream(PronounKey.values())
        .map(PronounKey::getSerializedName)
        .toList();

    public static RequiredArgumentBuilder<CommandSourceStack, String> pronounArgument(String name) {
        return argument(name, StringArgumentType.word())
            .suggests((ctx, builder) -> {
                PRONOUN_KEYS.forEach(builder::suggest);
                return builder.buildFuture();
            });
    }

    public static PronounKey getPronounKey(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        String value = StringArgumentType.getString(ctx, name);
        return Arrays.stream(PronounKey.values())
            .filter(k -> k.getSerializedName().equals(value))
            .findFirst()
            .orElseThrow(() -> INVALID_PRONOUN.create(value));
    }
}

@PronounsCommandManager.CommandInfo(usage = "/pronounspls set pronoun <pronoun>", description = "Set your pronouns")
public class SetPronounsCommand implements PronounsCommandManager.PronounsCommand {
    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("set")
            .then(PronounKeyArgumentType.pronounArgument("pronoun")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayerOrException();
                    MinecraftServer server = source.getServer();
                    PronounKey key = PronounKeyArgumentType.getPronounKey(ctx, "pronoun");

                    if (key.getTranslationKey() == null) {
                        // PronounDB has no translation key
                        setFromPronounDB(player, server, source);
                    } else {
                        // Custom pronoun translation keys
                        setCustom(player, key, server, source);
                    }

                    return 1;
                })
            )
        );
    }

    private void setFromPronounDB(ServerPlayer player, MinecraftServer server, CommandSourceStack source) {
        if (PronounsPlease.pronoundb == null) {
            error("PronounDB is unavailable in offline mode", null, source);
            return;
        }

        PronounsPlease.pronoundb.lookupAsync(PronounDBClient.Platform.MINECRAFT, player.getStringUUID())
            .thenAccept(pronouns -> pronouns.ifPresent(p ->
                server.execute(() -> {
                    String translatedPronouns = PronounsTranslationManager
                        .INSTANCE
                        .translate(player, p.asTranslationKeys().getFirst());

                    PronounsTeamManager.INSTANCE.setPronouns(player, new PronounsSource.PronounDB(new WeakReference<>(p)), server);
                    PronounsTeamManager.INSTANCE.syncToPlayer(player, server);
                    source.sendSuccess(
                        () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                            Component.literal("Pronouns set to ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(translatedPronouns).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC))
                                .append(Component.literal(" from PrononunDB"))
                        ),
                        false
                    );
                })
            ))
            .exceptionally(e -> {
                Component why = Component.literal(e.getCause().getMessage()).withStyle(ChatFormatting.BOLD);
                server.execute(() -> error("Oh no! An error occurred while setting your pronouns: ", why, source));
                return null;
            });
    }

    private void setCustom(ServerPlayer player, PronounKey key, MinecraftServer server, CommandSourceStack source) {
        PronounsTeamManager.INSTANCE.setPronouns(player, new PronounsSource.Custom(key.getTranslationKey()), server);
        PronounsTeamManager.INSTANCE.syncToPlayer(player, server);

        source.sendSuccess(
            () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                Component.literal("Pronouns set to ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(key.getSerializedName()).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC))
            ),
            false
        );
    }

    protected static Component error(String message, @Nullable Component special, @Nullable CommandSourceStack source) {
        MutableComponent formattedMessage = Component.literal(message).withStyle(ChatFormatting.GRAY);
        if (special != null) formattedMessage.append(special);

        Component formatted = PronounsCommandManager.ERROR_PREFIX.copy().append(formattedMessage);
        if (source != null) {
            source.sendFailure(formatted);
        }

        return formatted;
    }
}
