package co.ke.xently.log.mask;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(prefix = "p11.masking")
public class P11MaskingProperties {
    @Builder.Default
    private boolean enabled = true;
    private List<String> fields;
    private static final List<String> DEFAULT_FIELDS = List.of(
            "password",
            "passcode",
            "secret",
            "token",
            "accessToken",
            "refreshToken",
            "ssn",
            "creditCard",
            "cardNumber",
            "email",
            "phone",
            "phoneNumber",
            "accountNumber",
            "pin"
    );
    @Builder.Default
    private MaskingStyle maskStyle = MaskingStyle.FULL;
    @Builder.Default
    private String maskCharacter = "*";
    @Builder.Default
    private Json json = new Json();

    public List<String> getFields() {
        if (fields != null && !fields.isEmpty()) return fields;
        return DEFAULT_FIELDS;
    }

    public boolean isFieldConfigured(String name) {
        if (name == null) return false;
        var effective = getFields();
        if (effective == null || effective.isEmpty()) return false;
        var normalized = name.toLowerCase(Locale.ROOT);
        return effective.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(normalized));
    }

    public boolean isJsonMaskingEnabled() {
        return json != null && json.isEnabled();
    }

    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Json {
        private boolean enabled = false;
    }

}