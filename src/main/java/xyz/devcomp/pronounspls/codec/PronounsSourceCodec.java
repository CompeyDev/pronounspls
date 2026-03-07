package xyz.devcomp.pronounspls.codec;

import java.io.IOException;
import java.lang.ref.WeakReference;

import xyz.devcomp.pronounspls.PronounsSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PronounsSourceCodec implements PronounsCodec<PronounsSource> {
    public static final PronounsSourceCodec INSTANCE = new PronounsSourceCodec();

    @Override
    public JsonElement serialize(PronounsSource source) {
        JsonObject obj = new JsonObject();
        switch (source) {
            case PronounsSource.Custom c -> {
                obj.addProperty("type", "custom");
                obj.addProperty("pronounsKey", c.pronounsKey());
            }
            case PronounsSource.PronounDB p -> {
                obj.addProperty("type", "pronoundb");
            }
        }
        return obj;
    }

    @Override
    public PronounsSource deserialize(JsonElement json) throws IOException {
        JsonObject obj = json.getAsJsonObject();
        return switch (obj.get("type").getAsString()) {
            case "custom" -> new PronounsSource.Custom(obj.get("pronounsKey").getAsString());
            case "pronoundb" -> new PronounsSource.PronounDB(new WeakReference<>(null));
            default -> throw new IOException("Unknown PronounsSource type: " + obj.get("type").getAsString());
        };
    }
}
