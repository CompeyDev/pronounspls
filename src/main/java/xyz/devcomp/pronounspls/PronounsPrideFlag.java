package xyz.devcomp.pronounspls;

import java.util.Optional;

import xyz.devcomp.pronounspls.api.Decoration;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public enum PronounsPrideFlag {
    LESBIAN     (0xD62900, 0xFF9B55, 0xFFFFFF, 0xD461A6, 0xA50062),
    BISEXUAL    (0xD60270, 0xD60270, 0x9B4F96, 0x0038A8, 0x0038A8),
    PANSEXUAL   (0xFF218C, 0xFFD800, 0x21B1FF),
    RAINBOW     (0xFF0000, 0xFF9B00, 0xFFFF00, 0x00B300, 0x0000FF, 0x7F00FF),
    TRANS       (0x55CDFC, 0xF7A8B8, 0xFFFFFF, 0xF7A8B8, 0x55CDFC);

    private final int[] colors;

    PronounsPrideFlag(int... colors) {
        this.colors = colors;
    }

    public static Optional<PronounsPrideFlag> fromDecoration(Decoration decoration) {
        if (!decoration.isPride())
            return Optional.empty();

        return Optional.ofNullable(switch (decoration.name()) {
            case "pride"             -> RAINBOW;
            case "pride_bisexual"    -> BISEXUAL;
            case "pride_lesbian"     -> LESBIAN;
            case "pride_pansexual"   -> PANSEXUAL;
            case "pride_transgender" -> TRANS;
            default                  -> null;
        });
    }

    public Text apply(String text) {
        MutableText result = Text.empty();

        for (int i = 0; i < text.length(); i++) {
            int color = colors[i % colors.length];
            result.append(
                Text.literal(String.valueOf(text.charAt(i)))
                    .styled(style -> style.withColor(color))
            );
        }

        return result;
    }
}
