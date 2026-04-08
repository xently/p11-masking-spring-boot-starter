package co.ke.xently.log.mask;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
        "p11.masking.enabled=true",
        "p11.masking.fields[0]=email",
        "p11.masking.fields[1]=phoneNumber",
        "p11.masking.mask-style=PARTIAL",
        "p11.masking.mask-character=*",
        "p11.masking.json.enabled=true"
})
class MaskingIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldMaskSensitiveFieldsInJson() {
        var dto = new TestDto("Title", "Author", "test@test.com", "1234567890");

        var json = objectMapper.writeValueAsString(dto);

        assertAll(
                () -> assertThat(json, containsString("""
                        "email":"t***@test.com\"""")),
                () -> assertThat(json, containsString("""
                        "title":"Title\""""))
        );
    }

    @Test
    void shouldMaskAnnotatedFieldsEvenWhenNotConfigured() {
        var dto = new AnnotatedDto("Title", "1234567890");

        var json = objectMapper.writeValueAsString(dto);

        assertAll(
                () -> assertThat(json, containsString("\"ssn\":\"1*********\"")),
                () -> assertThat(json, containsString("\"title\":\"Title\""))
        );
    }

    @Test
    void shouldPreferAnnotationOverridesGlobalConfig() {
        var dto = new AnnotatedOverrideDto("Title", "1234567890");

        var json = objectMapper.writeValueAsString(dto);

        assertAll(
                () -> assertThat(json, containsString("""
                        "phoneNumber":"######7890\"""")),
                () -> assertThat(json, containsString("""
                        "title":"Title\""""))
        );
    }

    private record TestDto(String title, String author, String email, String phoneNumber) {
    }

    private record AnnotatedDto(String title, @Mask String ssn) {
    }

    private record AnnotatedOverrideDto(
            String title,
            @Mask(style = P11MaskingProperties.MaskingStyle.LAST4, maskCharacter = "#") String phoneNumber
    ) {
    }
}
