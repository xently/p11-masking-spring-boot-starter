package com.github.ajharry69.log.mask;

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
    @Builder.Default
    private List<String> defaultFields = List.of(
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

    public List<String> getFields() {
        if (fields != null && !fields.isEmpty()) return fields;
        return defaultFields;
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

    public enum MaskingStyle {DEFAULT, FULL, PARTIAL, LAST4}
}