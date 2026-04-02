package com.github.ajharry69.demo.book;

import com.github.ajharry69.demo.TestcontainersConfiguration;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class BookControllerAPITest {

    @LocalServerPort
    int port;

    @Autowired
    private BookRepository bookRepository;

    private static String firstLineContaining(String output, String token) {
        return output.lines()
                .filter(line -> line.contains(token))
                .findFirst()
                .orElse("");
    }

    private static void assertMasked(String line,
                                     String maskedEmail,
                                     String maskedPhone,
                                     String rawEmail,
                                     String rawPhone) {
        var checks = new ArrayList<Runnable>();
        checks.add(() -> assertThat(line, containsString(maskedEmail)));
        checks.add(() -> assertThat(line, containsString(maskedPhone)));
        checks.add(() -> assertThat(line, not(containsString(rawEmail))));
        checks.add(() -> assertThat(line, not(containsString(rawPhone))));
        assertAll(checks.stream().map(check -> (Executable) check::run).toList());
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        RestAssured.replaceFiltersWith(new RequestLoggingFilter(), new ResponseLoggingFilter());
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("multipart/form-data", ContentType.MULTIPART));
    }

    @Test
    void shouldCreateBookAndReturnMaskedSensitiveFields(CapturedOutput output) {
        // language=JSON
        var payload = """
                {
                  "title": "Clean Architecture",
                  "author": "Robert Martin",
                  "email": "uncle.bob@example.com",
                  "phoneNumber": "0712345678"
                }""";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/books")
                .then()
                .statusCode(201)
                .body("title", equalTo("Clean Architecture"))
                .body("author", equalTo("Robert Martin"))
                .body("email", equalTo("u********@example.com"))
                .body("phoneNumber", equalTo("0*********"));

        var line = firstLineContaining(output.getOut(), "Creating book:");
        assertThat(line, not(emptyString()));
        assertMasked(line,
                "u********@example.com",
                "0*********",
                "uncle.bob@example.com",
                "0712345678"
        );
    }

    @Test
    void shouldUpdateAndDeleteBook(CapturedOutput output) {
        var book = bookRepository.save(Book.builder()
                .title("T").author("A").email("a@test.com").phoneNumber("0700000000").build());
        var id = book.getId();

        // language=JSON
        var update = """
                {
                  "title": "New",
                  "author": "Auth2",
                  "email": "new@test.com",
                  "phoneNumber": "0711111111"
                }""";

        given().contentType(ContentType.JSON)
                .body(update)
                .when().put("/api/v1/books/{id}", id)
                .then().statusCode(200)
                .body("title", equalTo("New"))
                .body("email", equalTo("n**@test.com"))
                .body("phoneNumber", equalTo("0*********"));

        given().when().delete("/api/v1/books/{id}", id).then().statusCode(204);

        given().when().get("/api/v1/books/{id}", id).then().statusCode(404);

        var line = firstLineContaining(output.getOut(), "Updating book with id:");
        assertThat(line, not(emptyString()));
        assertMasked(line,
                "n**@test.com",
                "0*********",
                "new@test.com",
                "0711111111"
        );
    }
}
