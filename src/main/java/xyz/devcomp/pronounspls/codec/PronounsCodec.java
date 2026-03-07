package xyz.devcomp.pronounspls.codec;

import com.google.gson.JsonElement;

import java.io.IOException;

public interface PronounsCodec<T> {
    JsonElement serialize(T value);
    T deserialize(JsonElement json) throws IOException;
}
