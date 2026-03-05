package xyz.devcomp.pronounspls.api;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import com.google.gson.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import xyz.devcomp.pronounspls.PronounsPlease;

/**
 * Client for the PronounDB API (V2).
 *
 * <pre>{@code
 * PronounDBClient client = PronounDBClient.builder()
 *     .logger(mySlf4jLogger)
 *     .withCache(Duration.ofMinutes(10), 500)
 *     .build();
 *
 * Optional<Pronouns> pronouns = client.lookup(Platform.DISCORD, "123456789");
 * }</pre>
 */
public class PronounDBClient {
    private static final String API_BASE_URL = "https://pronoundb.org/api/v2";
    private static final int MAX_BULK_IDS = 50;

    private final Gson gson;
    private final HttpClient httpClient;
    @Nullable private final Logger logger;

    private final Map<String, CacheEntry<Pronouns>> cache; // sha256(platform, id) -> CacheEntry<Pronouns>
    private final Duration cacheTtl;
    private final int cacheMaxSize;
    private final boolean cachingEnabled;

    private PronounDBClient(Builder builder) {
        this.logger = builder.logger; // may be null
        this.gson = new GsonBuilder().create();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        this.cacheTtl = builder.cacheTtl;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.cachingEnabled = builder.cachingEnabled;

        if (cachingEnabled) {
            // LRU-like eviction
            this.cache = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<Pronouns>> eldest) {
                    return size() > cacheMaxSize;
                }
            };
        } else {
            this.cache = null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Logger logger        = null;
        private boolean cachingEnabled = false;
        private Duration cacheTtl    = Duration.ofMinutes(5);
        private int cacheMaxSize     = 1000;

        /**
         * Sets the SL4J logging facade.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Whether to cache or not, for a given TTL and max entry count.
         */
        public Builder withCache(Duration ttl, int maxSize) {
            this.cachingEnabled = true;
            this.cacheTtl       = Objects.requireNonNull(ttl);
            this.cacheMaxSize   = maxSize;
            return this;
        }

        public PronounDBClient build() {
            return new PronounDBClient(this);
        }
    }

    public enum Platform {
        MINECRAFT("minecraft"),
        DISCORD("discord"),
        GITHUB("github"),
        TWITTER("twitter"),
        TWITCH("twitch");

        private final String id;

        Platform(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }


    //
    // Synchronous API
    //

    /**
     * Look up pronouns for a single user.
     *
     * @param platform  the platform to query the user from
     * @param userId    the user ID on the platform
     * @return user's pronouns or empty if pronouns are unset
     */
    public Optional<Pronouns> lookup(Platform platform, String userId) throws PronounDBException {
        String cacheKey = hash(platform, userId);

        if (cachingEnabled) {
            CacheEntry<Pronouns> entry = cache.get(cacheKey);
            if (entry != null && entry.isNotExpired()) {
                log("Cache hit for {} on {}", userId, platform.getId());
                return Optional.of(entry.value());
            }
        }

        String url = API_BASE_URL + "/lookup?platform=" + platform.getId() + "&ids=" + userId;
        log("Looking up {} from {} on PronounDB", userId, platform.getId());

        JsonObject body = getJson(url);
        Optional<Pronouns> pronouns = Optional.ofNullable(parseSingleResponse(body));

        log("Raw response body for {} on {}: {}", body, userId, platform);
        log(
            "Parsed into pronouns {} for {} on {} from PronounDB",
            pronouns.map(Pronouns::toHumanReadable).orElse("<null>"),
            userId, platform
        );


        if (cachingEnabled && pronouns.isPresent()) {
            cache.put(cacheKey, new CacheEntry<>(pronouns.get(), Instant.now().plus(cacheTtl)));
        }

        return pronouns;
    }

    /**
     * Bulk-lookup pronouns for multiple users on the same platform.
     *
     * <p>Automatically batches requests if more than {@value MAX_BULK_IDS} IDs are given.
     *
     * @param platform  the platform to query the user from
     * @param userIds   the list of user IDs for the users on the platform
     * @return a map of userId -> Pronouns for users who have pronouns set (users with no
     *         pronouns registered are absent from the map)
     */
    public Map<String, Pronouns> lookupBulk(Platform platform, List<String> userIds)
        throws PronounDBException {

        if (userIds.isEmpty()) return Collections.emptyMap();

        Map<String, Pronouns> result = new HashMap<>();
        List<String> toFetch = new ArrayList<>();

        // Pull what we can from cache first
        if (cachingEnabled) {
            for (String id : userIds) {
                CacheEntry<Pronouns> entry = cache.get(hash(platform, id));
                if (entry != null && entry.isNotExpired()) {
                    result.put(id, entry.value());
                } else {
                    toFetch.add(id);
                }
            }
            log("{} cache hits, {} to fetch", result.size(), toFetch.size());
        } else {
            toFetch.addAll(userIds);
        }

        // Uncached -- batch them
        for (int i = 0; i < toFetch.size(); i += MAX_BULK_IDS) {
            List<String> batch = toFetch.subList(i, Math.min(i + MAX_BULK_IDS, toFetch.size()));
            String ids = String.join(",", batch);
            String url = API_BASE_URL + "/lookup/bulk?platform=" + platform.getId() + "&ids=" + ids;
            log("Bulk fetching {} ids of {} on PronounDB", batch.size(), platform.getId());

            JsonObject body = getJson(url);
            Map<String, Pronouns> batchResult = parseBulkResponse(body);

            if (cachingEnabled) {
                Instant expiry = Instant.now().plus(cacheTtl);
                batchResult.forEach((id, p) ->
                    cache.put(hash(platform, id), new CacheEntry<>(p, expiry))
                );
            }

            result.putAll(batchResult);
        }

        return Collections.unmodifiableMap(result);
    }

    //
    // Async API (we want to avoid blocking the server thread during joins / leaves)
    //

    public CompletableFuture<Optional<Pronouns>> lookupAsync(Platform platform, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lookup(platform, userId);
            } catch (PronounDBException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Map<String, Pronouns>> lookupBulkAsync(Platform platform, List<String> userIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lookupBulk(platform, userIds);
            } catch (PronounDBException e) {
                throw new CompletionException(e);
            }
        });
    }

    //
    // Caching!
    //

    /** Evict a specific user from the cache. */
    public void invalidate(Platform platform, String userId) {
        if (cachingEnabled) cache.remove(hash(platform, userId));
    }

    /** Clear the entire cache. */
    public void invalidateAll() {
        if (cachingEnabled) cache.clear();
    }

    /** Returns the number of entries currently in the cache. */
    public int cacheSize() {
        return cachingEnabled ? cache.size() : 0;
    }

    //
    // Private Internals
    //

    private JsonObject getJson(String url) throws PronounDBException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", PronounsPlease.MOD_ID + "/1.0") // TODO: get version?
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            throw new PronounDBException("Network error reaching PronounDB", e);
        }

        int status = response.statusCode();
        log("HTTP {} for {}", status, url);

        switch (status) {
            case 200 -> { /* no-op */ }
            case 404 -> throw new PronounDBException("Resource not found (404): " + url);
            case 429 -> {
                int retryAfter = response.headers()
                    .firstValue("Retry-After")
                    .map(Integer::parseInt)
                    .orElse(60);
                throw new PronounDBException.PronounDBRateLimitException(retryAfter);
            }
            default -> throw new PronounDBException("Unexpected HTTP status " + status);
        }

        try {
            return gson.fromJson(response.body(), JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new PronounDBException("Failed to parse JSON response", e);
        }
    }

    private Pronouns parseSingleResponse(JsonObject body) {
        // NOTE: single lookup wraps the result in an object keyed by user ID, same as bulk
        JsonObject userObj = body.entrySet().stream()
            .findFirst()
            .map(e -> e.getValue().getAsJsonObject())
            .orElse(null);

        if (userObj == null) return null;

        JsonObject sets = userObj.getAsJsonObject("sets");
        if (sets == null) return null;

        JsonArray arr = sets.getAsJsonArray("en");
        if (arr == null || arr.isEmpty()) return null;

        List<String> pronounSets = new ArrayList<>();
        arr.forEach(e -> pronounSets.add(e.getAsString()));
        return new Pronouns(Collections.unmodifiableList(pronounSets));
    }


    private Map<String, Pronouns> parseBulkResponse(JsonObject body) {
        Map<String, Pronouns> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
            String userId = entry.getKey();
            JsonObject userObj = entry.getValue().getAsJsonObject();
            Pronouns pronouns = parseSingleResponse(userObj);
            if (pronouns != null) {
                result.put(userId, pronouns);
            }
        }
        return result;
    }

    private static String hash(Platform platform, String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = platform.getId() + ":" + userId;
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { /* unreachable */ }

        return null;
    }

    private void log(String msg, Object... args) {
        if (logger != null) logger.debug(msg, args);
    }
}
