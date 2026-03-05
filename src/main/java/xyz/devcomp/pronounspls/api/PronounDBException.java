package xyz.devcomp.pronounspls.api;

public class PronounDBException extends Exception {
    public PronounDBException(String message) { super(message); }
    public PronounDBException(String message, Throwable cause) { super(message, cause); }

    public static class PronounDBNotFoundException extends PronounDBException {
        private final String userId;
        private final PronounDBClient.Platform platform;

        public PronounDBNotFoundException(PronounDBClient.Platform platform, String userId) {
            super("No pronouns found for " + userId + " on " + platform.getId());
            this.userId = userId;
            this.platform = platform;
        }

        public String getUserId() { return userId; }
        public PronounDBClient.Platform getPlatform() { return platform; }
    }

    public static class PronounDBRateLimitException extends PronounDBException {
        private final int retryAfterSeconds;

        public PronounDBRateLimitException(int retryAfterSeconds) {
            super("Rate limited by PronounDB. Retry after " + retryAfterSeconds + " seconds.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
