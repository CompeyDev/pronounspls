package xyz.devcomp.pronounspls;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Manages server-side translations for pronoun variants by loading lang files
 * from {@code data/pronounspls/lang/<language>.json} during datapack reload.
 *
 * <p>This is used in place of {@link net.minecraft.text.Text#translatable} since
 * translatables are handled on the client side and this mod runs purely on the
 * server.
 */
public class PronounsTranslationManager implements SynchronousResourceReloader {
    private final Map<String, Map<String, String>> translations = new HashMap<>();

    public static final PronounsTranslationManager INSTANCE = new PronounsTranslationManager();
    private static final String DEFAULT_LOCALE = "en_us";


    @Override
    public String getName() {
        return getFabricId().toString();
    }

    /**
     * Gets the unique identifier for this resource reload listener.
     */
    public static Identifier getFabricId() {
        return Identifier.of(PronounsPlease.MOD_ID, "translations");
    }

    /**
     * Clears and reloads all translations from {@code data/pronounspls/lang/}
     * on datapack reload. Each JSON file's name (without extension) is used as
     * the locale code.
     *
     * @param manager the resource manager providing access to datapack files
     */
    @Override
    public void reload(ResourceManager manager) {
        translations.clear();

        manager.findResources("lang", path -> path.getPath().endsWith(".json"))
            .forEach((id, resource) -> {
                if (!id.getNamespace().equals(PronounsPlease.MOD_ID)) return;

                String language = id.getPath()
                    .replace("lang/", "")
                    .replace(".json", "");

                try (var stream = resource.getInputStream()) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                    Map<String, String> langMap = new HashMap<>();
                    obj.entrySet().forEach(e -> langMap.put(e.getKey(), e.getValue().getAsString()));
                    translations.put(language, langMap);
                    PronounsPlease.LOGGER.info("Loaded {} pronoun translations for {} from datapack", langMap.size(), language);
                } catch (IOException e) {
                    PronounsPlease.LOGGER.error(e.toString());
                }
            });
    }
    /**
     * Translates a key into the given language.
     *
     * <p>Falls back to {@value DEFAULT_LOCALE} if the key is not found in the requested
     * language, and falls back to the raw key itself if not found in the default locale
     * either.
     *
     * @param language  the locale code (see <a href="https://minecraft.wiki/w/Language#Languages">the wiki</a>)
     * @param key       the translation key to look up
     * @return the translated string
     */
    public String translate(String language, String key) {
        PronounsPlease.LOGGER.debug("Translating pronoun key {} in {}", key, language);
        return translations
            .getOrDefault(language, Map.of())
            .getOrDefault(key, translations
                .getOrDefault(DEFAULT_LOCALE, Map.of())
                .getOrDefault(key, key));
    }
}
