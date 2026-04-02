package com.github.ajharry69.log.mask;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;

class MaskingMessageConverterTest {
    private final P11MaskingProperties properties = P11MaskingProperties.builder()
            .maskStyle(P11MaskingProperties.MaskingStyle.PARTIAL)
            .maskCharacter("*")
            .fields(List.of("email", "phoneNumber"))
            .build();
    private final MaskingService maskingService = new MaskingService(properties);
    private final MaskingMessageConverter converter = new MaskingMessageConverter();
    private final LoggerContext loggerContext = new LoggerContext();
    private final Logger logger = loggerContext.getLogger(MaskingMessageConverterTest.class);

    @BeforeEach
    void setUp() {
        MaskingMessageConverter.initialize(maskingService, properties);
    }

    private String convert(String message, Object... args) {
        var event = new LoggingEvent(
                MaskingMessageConverterTest.class.getName(),
                logger,
                Level.INFO,
                message,
                null,
                args
        );
        return converter.convert(event);
    }

    private void assertMasked(String output, List<String> expected, List<String> unexpected) {
        var checks = new ArrayList<Executable>();
        for (String value : expected) {
            checks.add(() -> assertThat(output, containsString(value)));
        }
        for (String value : unexpected) {
            checks.add(() -> assertThat(output, not(containsString(value))));
        }
        assertAll(checks);
    }

    private record LogCase(String name, String message, Object[] arguments, List<String> expected,
                           List<String> unexpected) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record LogDto(String title, @Mask String email, String phoneNumber) {
    }

    private record OverrideDto(
            @Mask(style = P11MaskingProperties.MaskingStyle.LAST4, maskCharacter = "#") String phoneNumber
    ) {
    }

    @Nested
    class StructuredMessages {
        static Stream<LogCase> logCases() {
            var record = new LogDto("Title", "john.doe@example.com", "0712345678");
            var map = Map.of("email", "john.doe@example.com", "phoneNumber", "0712345678");

            return Stream.of(
                    new LogCase(
                            "email field",
                            "email={}",
                            new Object[]{"john.doe@example.com"},
                            List.of("email=j*******@example.com"),
                            List.of("john.doe@example.com")
                    ),
                    new LogCase(
                            "map payload",
                            "payload={}",
                            new Object[]{map},
                            List.of("email=j*******@example.com", "phoneNumber=0*********"),
                            List.of("john.doe@example.com", "0712345678")
                    ),
                    new LogCase(
                            "record payload",
                            "payload={}",
                            new Object[]{record},
                            List.of("email=j*******@example.com", "phoneNumber=0*********"),
                            List.of("john.doe@example.com", "0712345678")
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("logCases")
        void shouldMaskStructuredMessages(LogCase logCase) {
            var masked = convert(logCase.message(), logCase.arguments());

            assertMasked(masked, logCase.expected(), logCase.unexpected());
        }
    }

    @Nested
    class AnnotationOverrides {
        @Test
        void shouldApplyAnnotationOverrides() {
            var dto = new OverrideDto("1234567890");

            var masked = convert("payload={}", dto);

            assertMasked(masked, List.of("phoneNumber=######7890"), List.of("1234567890"));
        }
    }
}
