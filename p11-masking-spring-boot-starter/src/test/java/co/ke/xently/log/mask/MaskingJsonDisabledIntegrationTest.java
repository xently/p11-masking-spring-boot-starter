package co.ke.xently.log.mask;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
        "p11.masking.enabled=true",
        "p11.masking.fields[0]=email",
        "p11.masking.fields[1]=phoneNumber",
        "p11.masking.mask-style=PARTIAL",
        "p11.masking.mask-character=*"
})
class MaskingJsonDisabledIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldNotMaskConfiguredFieldsWhenJsonMaskingDisabled() {
        var dto = new TestDto("Title", "Author", "test@test.com", "1234567890");

        var json = objectMapper.writeValueAsString(dto);

        assertAll(
                () -> assertThat(json, containsString("""
                        "email":"test@test.com\"""")),
                () -> assertThat(json, containsString("""
                        "phoneNumber":"1234567890\"""")),
                () -> assertThat(json, not(containsString("t***@test.com"))),
                () -> assertThat(json, not(containsString("0*********")))
        );
    }

    @Test
    void shouldNotMaskAnnotatedFieldsWhenJsonMaskingDisabled() {
        var dto = new AnnotatedDto("Title", "1234567890");

        var json = objectMapper.writeValueAsString(dto);

        assertAll(
                () -> assertThat(json, containsString("""
                        "ssn":"1234567890\"""")),
                () -> assertThat(json, not(containsString("""
                        "ssn":"1*********\"""")))
        );
    }

    private record TestDto(String title, String author, String email, String phoneNumber) {
    }

    private record AnnotatedDto(String title, @Mask String ssn) {
    }
}
