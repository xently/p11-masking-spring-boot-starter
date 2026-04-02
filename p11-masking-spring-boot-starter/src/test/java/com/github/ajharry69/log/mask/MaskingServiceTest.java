package com.github.ajharry69.log.mask;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MaskingServiceTest {

    static Stream<String> shouldHandleNullAndEmptySafely() {
        return Stream.of(null, "");
    }

    @ParameterizedTest
    @MethodSource
    void shouldHandleNullAndEmptySafely(String input) {
        var props = P11MaskingProperties.builder()
                .maskStyle(P11MaskingProperties.MaskingStyle.PARTIAL)
                .maskCharacter("*")
                .build();
        var service = new MaskingService(props);

        var actual = service.mask(input);

        assertThat(actual, equalTo(input));
    }

    @Nested
    class PartialMasking {
        @ParameterizedTest(name = "shouldPartiallyMaskEmail: {0} -> {1}")
        @CsvSource({
                "john.doe@example.com, j*******@example.com",
                "a@b.com, a@b.com", // too short to mask username part safely
                "0712345678, 0*********"
        })
        void shouldPartiallyMaskInputs(String input, String expected) {
            var props = P11MaskingProperties.builder()
                    .maskStyle(P11MaskingProperties.MaskingStyle.PARTIAL)
                    .maskCharacter("*")
                    .build();
            var service = new MaskingService(props);

            assertThat(service.mask(input), equalTo(expected));
        }
    }

    @Nested
    class FullMasking {
        @ParameterizedTest(name = "shouldFullyMask: {0}")
        @CsvSource({
                "short, ********",
                "mediumLength, ********",
                "thisIsAVeryLongStringThatShouldStillBeMaskedToEightCharacters, ********"
        })
        void shouldFullyMaskWithFixedLength(String input, String expected) {
            var props = P11MaskingProperties.builder()
                    .maskStyle(P11MaskingProperties.MaskingStyle.FULL)
                    .maskCharacter("*")
                    .build();
            var service = new MaskingService(props);

            var actual = service.mask(input);

            assertThat(actual, equalTo(expected));
        }
    }

    @Nested
    class Last4Masking {
        @ParameterizedTest(name = "shouldMaskLast4: {0} -> {1}")
        @CsvSource({
                "1234567890, ******7890",
                "4111111111111111, ************1111",
                "123, ***", // < 4
                "1234, ****", // == 4
                "12345, *2345" // > 4
        })
        void shouldMaskKeepingLast4Visible(String input, String expected) {
            var props = P11MaskingProperties.builder()
                    .maskStyle(P11MaskingProperties.MaskingStyle.LAST4)
                    .maskCharacter("*")
                    .build();
            var service = new MaskingService(props);

            var actual = service.mask(input);

            assertThat(actual, equalTo(expected));
        }
    }

    @Nested
    class Overrides {
        @ParameterizedTest(name = "shouldOverrideStyleAndChar: {0} -> {1}")
        @CsvSource({
                "1234567890, ######7890",
                "1234, ####"
        })
        void shouldOverrideStyleAndMaskCharacter(String input, String expected) {
            var props = P11MaskingProperties.builder()
                    .maskStyle(P11MaskingProperties.MaskingStyle.PARTIAL)
                    .maskCharacter("*")
                    .build();
            var service = new MaskingService(props);

            var actual = service.mask(input, P11MaskingProperties.MaskingStyle.LAST4, "#");

            assertThat(actual, equalTo(expected));
        }
    }
}
