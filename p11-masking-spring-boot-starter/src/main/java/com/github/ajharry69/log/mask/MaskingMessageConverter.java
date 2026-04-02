package com.github.ajharry69.log.mask;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.lang.reflect.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskingMessageConverter extends ClassicConverter {
    private static final int MAX_DEPTH = 2;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b\\+?\\d{1,3}?[-.\\s]?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b"
    );
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );

    private static volatile MaskingService maskingService;
    private static volatile P11MaskingProperties properties;

    static void initialize(MaskingService service, P11MaskingProperties props) {
        maskingService = service;
        properties = props;
    }

    @Override
    public String convert(ILoggingEvent event) {
        var message = event.getFormattedMessage();
        if (maskingService == null || properties == null) return message;
        if (!properties.isEnabled() || message == null || message.isBlank()) return message;

        var context = new MaskingContext();
        var args = event.getArgumentArray();
        if (args != null) {
            for (Object arg : args) {
                inspectArgument(arg, context, 0);
            }
        }

        var masked = applyValueReplacements(message, context.replacements);
        masked = applyFieldNameMasking(masked, context.fieldOverrides);
        masked = applyPatternMasking(masked);
        return masked;
    }

    private void inspectArgument(Object arg, MaskingContext context, int depth) {
        if (arg == null || depth > MAX_DEPTH) return;
        if (arg instanceof Throwable) return;
        if (isSimple(arg)) return;
        if (context.visited.put(arg, Boolean.TRUE) != null) return;

        if (arg instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                handleField(entry.getKey(), entry.getValue(), null, context, depth);
            }
            return;
        }
        if (arg instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                inspectArgument(item, context, depth + 1);
            }
            return;
        }
        if (arg.getClass().isArray()) {
            int length = Array.getLength(arg);
            for (int i = 0; i < length; i++) {
                inspectArgument(Array.get(arg, i), context, depth + 1);
            }
            return;
        }
        if (arg.getClass().isRecord()) {
            handleRecord(arg, context, depth);
            return;
        }
        handleFields(arg, context, depth);
        handleMethods(arg, context, depth);
    }

    private void handleRecord(Object arg, MaskingContext context, int depth) {
        var components = arg.getClass().getRecordComponents();
        if (components == null) return;
        for (RecordComponent component : components) {
            try {
                var accessor = component.getAccessor();
                var value = accessor.invoke(arg);
                var annotation = component.getAnnotation(Mask.class);
                handleField(component.getName(), value, annotation, context, depth);
            } catch (Exception ignored) {
                // Best-effort masking for logs.
            }
        }
    }

    private void handleFields(Object arg, MaskingContext context, int depth) {
        Class<?> type = arg.getClass();
        while (type != null && type != Object.class) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (LinkageError ex) {
                type = type.getSuperclass();
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    if (!field.canAccess(arg)) {
                        field.setAccessible(true);
                    }
                    var value = field.get(arg);
                    var annotation = field.getAnnotation(Mask.class);
                    handleField(field.getName(), value, annotation, context, depth);
                } catch (Throwable ignored) {
                    // Best-effort masking for logs.
                }
            }
            type = type.getSuperclass();
        }
    }

    private void handleMethods(Object arg, MaskingContext context, int depth) {
        Class<?> type = arg.getClass();
        while (type != null && type != Object.class) {
            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (LinkageError ex) {
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                if (method.getParameterCount() != 0 || method.getReturnType() == void.class) continue;
                var annotation = method.getAnnotation(Mask.class);
                if (annotation == null) continue;
                try {
                    if (!method.canAccess(arg)) {
                        method.setAccessible(true);
                    }
                    var value = method.invoke(arg);
                    var fieldName = deriveFieldName(method.getName());
                    handleField(fieldName, value, annotation, context, depth);
                } catch (Throwable ignored) {
                    // Best-effort masking for logs.
                }
            }
            type = type.getSuperclass();
        }
    }

    private void handleField(Object name, Object value, Mask annotation, MaskingContext context, int depth) {
        if (!(name instanceof String fieldName) || value == null) return;
        var hasAnnotation = annotation != null;
        var isConfigured = properties.isFieldConfigured(fieldName);

        if (hasAnnotation) {
            var override = new MaskOverride(annotation.style(), annotation.maskCharacter());
            context.fieldOverrides.putIfAbsent(normalize(fieldName), override);
        }

        if (hasAnnotation || isConfigured) {
            var raw = String.valueOf(value);
            if (!raw.isBlank()) {
                var masked = maskingService.mask(
                        raw,
                        hasAnnotation ? annotation.style() : null,
                        hasAnnotation ? annotation.maskCharacter() : null
                );
                addReplacement(context.replacements, raw, masked);
            }
        }

        if (!isSimple(value)) {
            inspectArgument(value, context, depth + 1);
        }
    }

    private String applyValueReplacements(String message, LinkedHashMap<String, String> replacements) {
        var masked = message;
        for (var entry : replacements.entrySet()) {
            masked = masked.replace(entry.getKey(), entry.getValue());
        }
        return masked;
    }

    private String applyFieldNameMasking(String message, Map<String, MaskOverride> overrides) {
        var fields = properties.getFields();
        if (fields == null || fields.isEmpty()) return message;

        var masked = message;
        for (String field : fields) {
            if (field == null || field.isBlank()) continue;
            var override = overrides.get(normalize(field));
            masked = maskFieldOccurrences(masked, field, override);
        }
        return masked;
    }

    private String maskFieldOccurrences(String message, String field, MaskOverride override) {
        var pattern = Pattern.compile(
                "(?i)(\\b" + Pattern.quote(field) + "\\b\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,}\\]\\s]+)"
        );
        var matcher = pattern.matcher(message);
        var buffer = new StringBuilder();
        while (matcher.find()) {
            var prefix = matcher.group(1);
            var rawValue = matcher.group(2);
            var quote = "";
            var value = rawValue;
            if (rawValue.length() >= 2) {
                var first = rawValue.charAt(0);
                var last = rawValue.charAt(rawValue.length() - 1);
                if ((first == '"' || first == '\'') && last == first) {
                    quote = String.valueOf(first);
                    value = rawValue.substring(1, rawValue.length() - 1);
                }
            }

            var maskedValue = maskingService.mask(
                    value,
                    override != null ? override.style : null,
                    override != null ? override.maskCharacter : null
            );
            var replacement = prefix + quote + maskedValue + quote;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String applyPatternMasking(String message) {
        var masked = message;
        masked = maskMatches(EMAIL_PATTERN, masked);
        masked = maskMatches(PHONE_PATTERN, masked);
        masked = maskMatches(CARD_PATTERN, masked);
        return masked;
    }

    private String maskMatches(Pattern pattern, String message) {
        var matcher = pattern.matcher(message);
        var buffer = new StringBuilder();
        while (matcher.find()) {
            var value = matcher.group();
            var masked = maskingService.mask(value, null, null);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void addReplacement(LinkedHashMap<String, String> replacements, String raw, String masked) {
        if (raw == null || raw.isBlank() || masked == null || raw.equals(masked)) return;
        replacements.putIfAbsent(raw, masked);
    }

    private boolean isSimple(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Date
                || value instanceof Temporal;
    }

    private String normalize(String field) {
        return field.toLowerCase(Locale.ROOT);
    }

    private String deriveFieldName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        return methodName;
    }

    private String decapitalize(String name) {
        if (name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static final class MaskingContext {
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private final LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
        private final Map<String, MaskOverride> fieldOverrides = new LinkedHashMap<>();
    }

    private record MaskOverride(P11MaskingProperties.MaskingStyle style, String maskCharacter) {
    }
}
