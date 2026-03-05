package xyz.devcomp.pronounspls;

import java.lang.ref.WeakReference;
import java.util.Optional;

import xyz.devcomp.pronounspls.api.Pronouns;

public sealed interface PronounsSource permits PronounsSource.PronounDB, PronounsSource.Custom {
    record Custom(String pronounsKey) implements PronounsSource {}

    record PronounDB(WeakReference<Pronouns> ref) implements PronounsSource {
        public Optional<String> resolve() {
            return Optional.ofNullable(ref.get())
                .flatMap(p -> p.asTranslationKeys().stream().findFirst());
        }
    }
}
