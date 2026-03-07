package xyz.devcomp.pronounspls;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class PronounsPersistable {
    private final String filename;

    protected PronounsPersistable(String filename) {
        this.filename = filename;
    }

    public abstract JsonElement save();
    public abstract void load(JsonElement json) throws IOException;

    public void saveToDisk(MinecraftServer server) {
        Path path = server.getSavePath(WorldSavePath.ROOT)
            .resolve(PronounsPlease.MOD_ID)
            .resolve(filename);

        PronounsPlease.LOGGER.info("Flushing pronouns to {}", path);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new Gson().toJson(save()));
        } catch (IOException e) {
            PronounsPlease.LOGGER.error("Failed to save {}", filename, e);
        }
    }

    public void loadFromDisk(MinecraftServer server) {
        Path path = server.getSavePath(WorldSavePath.ROOT)
            .resolve(PronounsPlease.MOD_ID)
            .resolve(filename);

        if (!Files.exists(path)) return;

        try {
            load(JsonParser.parseString(Files.readString(path)));
        } catch (IOException e) {
            PronounsPlease.LOGGER.error("Failed to load {}", filename, e);
        }
    }
}
