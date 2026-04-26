package co.ke.xently.log.mask;

import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;

@AllArgsConstructor
public class MaskingService {
    private final P11MaskingProperties properties;

    public String mask(String input) {
        return mask(input, null, null);
    }

    public String mask(String input, P11MaskingProperties.MaskingStyle styleOverride, String maskCharacterOverride) {
        if (!properties.isEnabled() || !StringUtils.hasText(input)) return input;

        var ch = resolveMaskCharacter(maskCharacterOverride);
        return switch (resolveStyle(styleOverride)) {
            case FULL, DEFAULT -> ch.repeat(8);
            case LAST4 -> {
                int unmaskedCharacters = 4;
                if (input.length() <= unmaskedCharacters) yield ch.repeat(input.length());
                yield ch.repeat(input.length() - unmaskedCharacters) + input.substring(input.length() - unmaskedCharacters);
            }
            case PARTIAL -> {
                if (input.contains("@")) { // Email
                    int atIndex = input.indexOf("@");
                    if (atIndex <= 1) yield input; // Too short to mask
                    yield input.charAt(0) + ch.repeat(atIndex - 1) + input.substring(atIndex);
                }
                // Default partial (keep first char)
                if (input.length() <= 1) yield input;
                yield input.charAt(0) + ch.repeat(input.length() - 1);
            }
        };
    }

    private P11MaskingProperties.MaskingStyle resolveStyle(P11MaskingProperties.MaskingStyle styleOverride) {
        var override = styleOverride == null || styleOverride == P11MaskingProperties.MaskingStyle.DEFAULT;
        var resolved = override ? properties.getMaskStyle() : styleOverride;
        return resolved == P11MaskingProperties.MaskingStyle.DEFAULT
                ? P11MaskingProperties.MaskingStyle.FULL
                : resolved;
    }

    private String resolveMaskCharacter(String maskCharacterOverride) {
        if (StringUtils.hasText(maskCharacterOverride)) return maskCharacterOverride;
        return StringUtils.hasText(properties.getMaskCharacter()) ? properties.getMaskCharacter() : "*";
    }
}
