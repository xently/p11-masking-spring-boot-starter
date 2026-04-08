package co.ke.xently.log.mask;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
        "p11.masking.enabled=true",
        "p11.masking.mask-style=PARTIAL",
        "p11.masking.mask-character=*",
        "p11.masking.fields[0]=email",
        "p11.masking.fields[1]=phoneNumber"
})
@ExtendWith(OutputCaptureExtension.class)
class MaskingLogbackIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MaskingLogbackIntegrationTest.class);

    private static String firstLineContaining(String output, String token) {
        return output.lines()
                .filter(line -> line.contains(token))
                .findFirst()
                .orElse("");
    }

    private static void assertMasked(String output, List<String> expected, List<String> unexpected) {
        var checks = new ArrayList<Executable>();
        for (String value : expected) {
            checks.add(() -> assertThat(output, containsString(value)));
        }
        for (String value : unexpected) {
            checks.add(() -> assertThat(output, not(containsString(value))));
        }
        assertAll(checks);
    }

    private record LogCase(String name, Runnable action, String marker, List<String> expected,
                           List<String> unexpected) {
        @Override
        @NonNull
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
    class LogOutput {
        static Stream<LogCase> logCases() {
            var record = new LogDto("Title", "john.doe@example.com", "0712345678");
            var override = new OverrideDto("1234567890");

            return Stream.of(
                    new LogCase(
                            "record payload",
                            () -> log.info("mask-test {}", record),
                            "mask-test",
                            List.of("email=j*******@example.com", "phoneNumber=0*********"),
                            List.of("john.doe@example.com", "0712345678")
                    ),
                    new LogCase(
                            "annotation override",
                            () -> log.info("mask-override {}", override),
                            "mask-override",
                            List.of("phoneNumber=######7890"),
                            List.of("1234567890")
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("logCases")
        void shouldMaskLogOutput(LogCase logCase, CapturedOutput output) {
            logCase.action().run();

            var line = firstLineContaining(output.getOut(), logCase.marker());

            assertAll(
                    () -> assertThat(line, not(emptyString())),
                    () -> assertMasked(line, logCase.expected(), logCase.unexpected())
            );
        }
    }
}
