package xyz.devcomp.pronounspls;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.resources.Identifier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.NotNull;

/**
 * Manages server-side translations for pronoun variants by loading lang files
 * from {@code data/pronounspls/lang/<language>.json} during datapack reload.
 *
 * <p>This is used in place of {@link net.minecraft.network.chat.Component#translatable} since
 * translatables are handled on the client side and this mod runs purely on the
 * server.
 */
public class PronounsTranslationManager implements ResourceManagerReloadListener {
    private final Map<String, Map<String, String>> translations = new HashMap<>();
    private final Map<String, String> fallbacks = new HashMap<>();

    public static final PronounsTranslationManager INSTANCE = new PronounsTranslationManager();
    private static final String DEFAULT_LOCALE = "en_us";
    private static final Identifier FALLBACKS_ID = Identifier.fromNamespaceAndPath(PronounsPlease.MOD_ID, "lang_fallbacks.json");

    @Override
    public @NotNull String getName() {
        return getFabricId().toString();
    }

    /**
     * Gets the unique identifier for this resource reload listener.
     */
    public static Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(PronounsPlease.MOD_ID, "translations");
    }

    /**
     * Clears and reloads all translations from {@code data/pronounspls/lang/}
     * and fallback rules from {@code data/pronounspls/lang_fallbacks.json}
     * on datapack reload.
     *
     * @param manager the resource manager providing access to datapack files
     */
    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        translations.clear();
        fallbacks.clear();

        // Load translations
        Instant start = Instant.now();
        manager.listResources("lang", path -> path.getPath().endsWith(".json"))
            .forEach((id, resource) -> {
                if (!id.getNamespace().equals(PronounsPlease.MOD_ID)) return;

                String language = id.getPath()
                    .replace("lang/", "")
                    .replace(".json", "");

                try (var stream = resource.open()) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                    Map<String, String> langMap = new HashMap<>();
                    obj.entrySet().forEach(e -> langMap.put(e.getKey(), e.getValue().getAsString()));
                    translations.put(language, langMap);
                    PronounsPlease.LOGGER.debug("Loaded {} pronoun translations for {} from datapack", langMap.size(), language);
                } catch (IOException e) {
                    PronounsPlease.LOGGER.error(e.toString());
                }
            });

        PronounsPlease.LOGGER.info("Loaded {} translations in {}ms", translations.size(), ChronoUnit.MILLIS.between(start, Instant.now()));

        // Load fallbacks
        manager.getResource(FALLBACKS_ID).ifPresent(resource -> {
            try (var stream = resource.open()) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                obj.entrySet().forEach(e -> {
                    // Invert the fallbacks to have a constant O(1) read complexity
                    String target = e.getKey();
                    e.getValue().getAsJsonArray().forEach(locale ->
                        fallbacks.put(locale.getAsString(), target)
                    );
                });
                PronounsPlease.LOGGER.info("Loaded {} language fallback rules from datapack", fallbacks.size());
            } catch (IOException e) {
                PronounsPlease.LOGGER.error(e.toString());
            }
        });
    }

    /**
     * Translates a key into the given language, walking the fallback chain
     * defined in {@code lang_fallbacks.json} before falling back to
     * {@value DEFAULT_LOCALE}, and finally the raw key itself.
     *
     * @param language the locale code (see <a href="https://minecraft.wiki/w/Language#Languages">the wiki</a>)
     * @param key      the translation key to look up
     * @return the translated string
     */
    public String translate(String language, String key) {
        PronounsPlease.LOGGER.debug("Translating pronoun key {} in {}", key, language);

        String current = language;
        while (current != null) {
            // Try to translate into exact locale, or default to any of its fallback locales
            String result = translations.getOrDefault(current, Map.of()).get(key);
            if (result != null) return result;
            String next = fallbacks.get(current);
            current = next != null && !next.equals(language) ? next : null;
        }

        // If we still have no translation, finally fallback to en_us
        return translations
            .getOrDefault(DEFAULT_LOCALE, Map.of())
            .getOrDefault(key, key);
    }

    /**
     * Translates a key into the player's preferred language.
     *
     * @param player the player whose locale it should be translated to
     * @param key    the translation key to look up
     * @return the translated string
     */
    public String translate(ServerPlayer player, String key) {
        return this.translate(player.clientInformation().language(), key);
    }
}
