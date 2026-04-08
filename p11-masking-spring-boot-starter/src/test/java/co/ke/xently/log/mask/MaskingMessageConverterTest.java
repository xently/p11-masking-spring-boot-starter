package co.ke.xently.log.mask;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;

class MaskingMessageConverterTest {
    private static final String RAW_EMAIL = "john.doe@example.com";
    private static final String RAW_PHONE = "0712345678";
    private static final String RAW_CARD = "4111 1111 1111 1111";
    private static final String RAW_TOKEN = "secret-token";
    private static final String RAW_PLAIN = "plainvalue";
    private static final String RAW_SHORT_PHONE = "12345";
    private static final String MASKED_EMAIL = "j*******@example.com";
    private static final String MASKED_PHONE = "0*********";

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

    private String masked(String raw) {
        return maskingService.mask(raw, null, null);
    }

    private String masked(String raw, P11MaskingProperties.MaskingStyle style, String maskCharacter) {
        return maskingService.mask(raw, style, maskCharacter);
    }

    private static void setConverterState(MaskingService service, P11MaskingProperties props) {
        try {
            Field serviceField = MaskingMessageConverter.class.getDeclaredField("maskingService");
            serviceField.setAccessible(true);
            serviceField.set(null, service);
            Field propsField = MaskingMessageConverter.class.getDeclaredField("properties");
            propsField.setAccessible(true);
            propsField.set(null, props);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to reset converter state", ex);
        }
    }

    private String invokeMaskXmlValue(String value, Object override) {
        try {
            Method method = MaskingMessageConverter.class.getDeclaredMethod("maskXmlValue", String.class, maskOverrideClass());
            method.setAccessible(true);
            return (String) method.invoke(converter, value, override);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke maskXmlValue", ex);
        }
    }

    private String invokeDeriveFieldName(String methodName) {
        try {
            Method method = MaskingMessageConverter.class.getDeclaredMethod("deriveFieldName", String.class);
            method.setAccessible(true);
            return (String) method.invoke(converter, methodName);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke deriveFieldName", ex);
        }
    }

    private Object newMaskOverride(P11MaskingProperties.MaskingStyle style, String maskCharacter) {
        try {
            Constructor<?> constructor = maskOverrideClass()
                    .getDeclaredConstructor(P11MaskingProperties.MaskingStyle.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(style, maskCharacter);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create MaskOverride", ex);
        }
    }

    private Class<?> maskOverrideClass() {
        try {
            return Class.forName("co.ke.xently.log.mask.MaskingMessageConverter$MaskOverride");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("MaskOverride type not found", ex);
        }
    }

    private record LogCase(String name, String message, Object[] arguments, List<String> expected,
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

    private static final class MethodMasked {
        private final String token;

        private MethodMasked(String token) {
            this.token = token;
        }

        @Mask(style = P11MaskingProperties.MaskingStyle.FULL, maskCharacter = "#")
        public String getToken() {
            return token;
        }

        @Override
        public String toString() {
            return "MethodMasked[token=" + token + "]";
        }
    }

    private record Child(@Mask String email) {
    }

    private static final class Parent {
        private final Child child;

        private Parent(Child child) {
            this.child = child;
        }

        @Override
        public String toString() {
            return "Parent[child=" + child + "]";
        }
    }

    private static final class CyclicNode {
        @Mask
        private final String email;
        private CyclicNode parent;

        private CyclicNode(String email) {
            this.email = email;
        }

        private void linkTo(CyclicNode parent) {
            this.parent = parent;
        }

        @Override
        public String toString() {
            return "CyclicNode[email=" + email + "]";
        }
    }

    private static final class ThrowingGetter {
        @Mask
        public String getEmail() {
            throw new IllegalStateException("boom");
        }

        @Override
        public String toString() {
            return "ThrowingGetter[email=" + RAW_EMAIL + "]";
        }
    }

    private record FieldCase(String name, String message, String rawValue, String expectedPrefix, String expectedSuffix) {
        @Override
        @NonNull
        public String toString() {
            return name;
        }
    }

    private record PatternCase(String name, String rawValue) {
        @Override
        @NonNull
        public String toString() {
            return name;
        }
    }

    private record CollectionCase(String name, String message, Object[] arguments,
                                  List<String> expected, List<String> unexpected) {
        @Override
        @NonNull
        public String toString() {
            return name;
        }
    }

    private record SoapCase(String name, String message, List<String> expected, List<String> unexpected) {
        @Override
        @NonNull
        public String toString() {
            return name;
        }
    }

    @Nested
    class GuardClauses {
        @Test
        void shouldReturnMessageWhenDisabled() {
            var disabledProps = P11MaskingProperties.builder()
                    .enabled(false)
                    .fields(List.of("email"))
                    .build();
            var disabledService = new MaskingService(disabledProps);
            MaskingMessageConverter.initialize(disabledService, disabledProps);

            var message = "email=" + RAW_EMAIL;
            var masked = convert(message, RAW_EMAIL);

            assertAll(
                    () -> assertThat(masked, equalTo(message)),
                    () -> assertThat(masked, containsString(RAW_EMAIL))
            );
        }

        @Test
        void shouldReturnMessageWhenBlank() {
            var message = "   ";
            var masked = convert(message, RAW_EMAIL);

            assertThat(masked, equalTo(message));
        }

        @Test
        void shouldReturnMessageWhenUninitialized() {
            setConverterState(null, null);
            var message = "email=" + RAW_EMAIL;

            var masked = convert(message, RAW_EMAIL);

            assertThat(masked, equalTo(message));
        }
    }

    @Nested
    class StructuredMessages {
        static Stream<LogCase> logCases() {
            var record = new LogDto("Title", RAW_EMAIL, RAW_PHONE);
            var map = Map.of("email", RAW_EMAIL, "phoneNumber", RAW_PHONE);

            return Stream.of(
                    new LogCase(
                            "email field",
                            "email={}",
                            new Object[]{RAW_EMAIL},
                            List.of("email=j*******@example.com"),
                            List.of(RAW_EMAIL)
                    ),
                    new LogCase(
                            "map payload",
                            "payload={}",
                            new Object[]{map},
                            List.of("email=j*******@example.com", "phoneNumber=0*********"),
                            List.of(RAW_EMAIL, RAW_PHONE)
                    ),
                    new LogCase(
                            "record payload",
                            "payload={}",
                            new Object[]{record},
                            List.of("email=j*******@example.com", "phoneNumber=0*********"),
                            List.of(RAW_EMAIL, RAW_PHONE)
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

        @Test
        void shouldOverrideFieldNameMaskingWhenAnnotationPresent() {
            var override = new OverrideDto("1234567890");
            var message = "phoneNumber=5555555555";

            var maskedValue = masked("5555555555", P11MaskingProperties.MaskingStyle.LAST4, "#");
            var defaultMasked = masked("5555555555");

            var masked = convert(message, override);

            assertAll(
                    () -> assertThat(masked, containsString("phoneNumber=" + maskedValue)),
                    () -> assertThat(masked, not(containsString("phoneNumber=" + defaultMasked))),
                    () -> assertThat(masked, not(containsString("5555555555")))
            );
        }
    }

    @Nested
    class FieldNameMasking {
        static Stream<FieldCase> fieldCases() {
            return Stream.of(
                    new FieldCase(
                            "equals without quotes",
                            "email=" + RAW_PLAIN,
                            RAW_PLAIN,
                            "email=",
                            ""
                    ),
                    new FieldCase(
                            "colon with double quotes",
                            "phoneNumber: \"" + RAW_SHORT_PHONE + "\"",
                            RAW_SHORT_PHONE,
                            "phoneNumber: \"",
                            "\""
                    ),
                    new FieldCase(
                            "case-insensitive with single quotes",
                            "EMAIL='" + RAW_PLAIN + "'",
                            RAW_PLAIN,
                            "EMAIL='",
                            "'"
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("fieldCases")
        void shouldMaskFieldOccurrences(FieldCase fieldCase) {
            var output = convert(fieldCase.message());
            var expected = fieldCase.expectedPrefix() + masked(fieldCase.rawValue()) + fieldCase.expectedSuffix();

            assertAll(
                    () -> assertThat(output, containsString(expected)),
                    () -> assertThat(output, not(containsString(fieldCase.rawValue())))
            );
        }

        @Test
        void shouldIgnoreUnconfiguredFieldNames() {
            var message = "ssn=123456";

            var output = convert(message);

            assertThat(output, equalTo(message));
        }
    }

    @Nested
    class PatternMasking {
        static Stream<PatternCase> patternCases() {
            return Stream.of(
                    new PatternCase("email pattern", RAW_EMAIL),
                    new PatternCase("phone pattern", RAW_PHONE)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("patternCases")
        void shouldMaskPatternMatches(PatternCase patternCase) {
            var message = "value=" + patternCase.rawValue();
            var expected = masked(patternCase.rawValue());

            var output = convert(message);

            assertAll(
                    () -> assertThat(output, containsString(expected)),
                    () -> assertThat(output, not(containsString(patternCase.rawValue())))
            );
        }

        @Test
        void shouldMaskCardPatterns() {
            var message = "value=" + RAW_CARD;

            var output = convert(message);

            assertAll(
                    () -> assertThat(output, containsString("4****")),
                    () -> assertThat(output, not(containsString(RAW_CARD)))
            );
        }
    }

    @Nested
    class XmlValueMasking {
        @Test
        void shouldReturnNullWhenValueIsNull() {
            var output = invokeMaskXmlValue(null, null);

            assertThat(output, nullValue());
        }

        @Test
        void shouldReturnBlankValueAsIs() {
            var output = invokeMaskXmlValue("   ", null);

            assertThat(output, equalTo("   "));
        }

        @Test
        void shouldReturnXmlFragmentsUnchanged() {
            var output = invokeMaskXmlValue("value<inner>", null);

            assertThat(output, equalTo("value<inner>"));
        }

        @Test
        void shouldMaskCdataValues() {
            var output = invokeMaskXmlValue("<![CDATA[" + RAW_PLAIN + "]]>", null);

            assertThat(output, equalTo("<![CDATA[" + masked(RAW_PLAIN) + "]]>"));
        }

        @Test
        void shouldMaskUsingOverride() {
            var override = newMaskOverride(P11MaskingProperties.MaskingStyle.LAST4, "#");

            var output = invokeMaskXmlValue("1234567890", override);

            assertThat(output, equalTo("######7890"));
        }
    }

    @Nested
    class DerivedFieldNames {
        @Test
        void shouldDeriveFromGettersAndBooleanGetters() {
            assertAll(
                    () -> assertThat(invokeDeriveFieldName("getEmail"), equalTo("email")),
                    () -> assertThat(invokeDeriveFieldName("isActive"), equalTo("active")),
                    () -> assertThat(invokeDeriveFieldName("getURL"), equalTo("uRL"))
            );
        }

        @Test
        void shouldReturnOriginalNameWhenNotGetter() {
            assertAll(
                    () -> assertThat(invokeDeriveFieldName("get"), equalTo("get")),
                    () -> assertThat(invokeDeriveFieldName("is"), equalTo("is")),
                    () -> assertThat(invokeDeriveFieldName("fetchValue"), equalTo("fetchValue"))
            );
        }
    }

    @Nested
    class SoapPayloadMasking {
        static Stream<SoapCase> soapCases() {
            return Stream.of(
                    new SoapCase(
                            "simple tags",
                            "<Envelope><email>" + RAW_EMAIL + "</email><phoneNumber>" + RAW_PHONE + "</phoneNumber></Envelope>",
                            List.of("<email>" + MASKED_EMAIL + "</email>", "<phoneNumber>" + MASKED_PHONE + "</phoneNumber>"),
                            List.of(RAW_EMAIL, RAW_PHONE)
                    ),
                    new SoapCase(
                            "namespaced tags",
                            "<soapenv:Envelope><ns:email>" + RAW_EMAIL + "</ns:email></soapenv:Envelope>",
                            List.of("<ns:email>" + MASKED_EMAIL + "</ns:email>"),
                            List.of(RAW_EMAIL)
                    ),
                    new SoapCase(
                            "cdata tags",
                            "<Envelope><email><![CDATA[" + RAW_EMAIL + "]]></email></Envelope>",
                            List.of("<email><![CDATA[" + MASKED_EMAIL + "]]></email>"),
                            List.of(RAW_EMAIL)
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("soapCases")
        void shouldMaskSoapPayloads(SoapCase soapCase) {
            var output = convert(soapCase.message());

            assertMasked(output, soapCase.expected(), soapCase.unexpected());
        }
    }

    @Nested
    class CollectionsAndArrays {
        static Stream<CollectionCase> collectionCases() {
            var record = new LogDto("Title", RAW_EMAIL, RAW_PHONE);
            var expected = List.of(
                    "email=" + "j*******@example.com",
                    "phoneNumber=0*********"
            );
            var unexpected = List.of(RAW_EMAIL, RAW_PHONE);

            return Stream.of(
                    new CollectionCase(
                            "list payload",
                            "payload={}",
                            new Object[]{List.of(record)},
                            expected,
                            unexpected
                    ),
                    new CollectionCase(
                            "array payload",
                            "payload={}",
                            new Object[]{new LogDto[]{record}},
                            expected,
                            unexpected
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("collectionCases")
        void shouldMaskCollectionsAndArrays(CollectionCase collectionCase) {
            var output = convert(collectionCase.message(), collectionCase.arguments());

            assertMasked(output, collectionCase.expected(), collectionCase.unexpected());
        }
    }

    @Nested
    class MethodAnnotations {
        @Test
        void shouldMaskValuesFromAnnotatedGetter() {
            var holder = new MethodMasked(RAW_TOKEN);

            var output = convert("payload={}", holder);
            var expected = masked(RAW_TOKEN, P11MaskingProperties.MaskingStyle.FULL, "#");

            assertMasked(output, List.of("token=" + expected), List.of(RAW_TOKEN));
        }
    }

    @Nested
    class NestedObjectsAndCycles {
        @Test
        void shouldMaskNestedObjects() {
            var parent = new Parent(new Child(RAW_EMAIL));

            var output = convert("payload={}", parent);

            assertMasked(output, List.of("email=" + masked(RAW_EMAIL)), List.of(RAW_EMAIL));
        }

        @Test
        void shouldHandleCyclicGraphs() {
            var node = new CyclicNode(RAW_EMAIL);
            node.linkTo(node);

            var output = convert("payload={}", node);

            assertMasked(output, List.of("email=" + masked(RAW_EMAIL)), List.of(RAW_EMAIL));
        }

        @Test
        void shouldIgnoreFailingAccessors() {
            var output = convert("payload={}", new ThrowingGetter());

            assertMasked(output, List.of(masked(RAW_EMAIL)), List.of(RAW_EMAIL));
        }
    }
}
