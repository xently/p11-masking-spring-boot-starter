# P11 Masking Spring Boot Starter + Books API Demo

A spring boot starter and demo showing sensitive data masking in logs/JSON.

- Spring Boot: 4.x (Jackson 3 `tools.jackson.*`)
- Java: 21 (LTS)
- Tests: JUnit 5 with Hamcrest
- Integration: Testcontainers (Oracle Free) + Rest-Assured

## Modules

- `p11-masking-spring-boot-starter`: Auto-configured Jackson module masking configured string fields.
- `books-api-demo`: Minimal CRUD API demonstrating the starter.

## Quick start

Requirements:
- Java 21
- Maven 3.9+
- Docker (for Testcontainers)

Run all tests (includes Testcontainers-based API tests):

```bash
mvn -q -DskipITs=false test
```

Run the demo API using Testcontainers (Oracle Free):

```bash
mvn -pl books-api-demo -am spring-boot:test-run -DskipTests
```

The APIs can be tested through - http://localhost:8080/swagger-ui.html

## Config (demo)

```yaml
p11:
  masking:
    enabled: true
    mask-style: PARTIAL  # FULL | PARTIAL | LAST4
    mask-character: "*"
    fields:
      - email
      - phoneNumber
```

## Log masking (no code changes)

The starter installs a Logback `%msg` converter that masks sensitive values in log output.
It works for structured messages (e.g., `email=...`, JSON snippets, Lombok `toString`) and
common patterns like emails, phone numbers, and card numbers. Default field names are used
if `p11.masking.fields` is not provided, and any configured fields override the defaults.

Default fields:
`password`, `passcode`, `secret`, `token`, `accessToken`, `refreshToken`, `ssn`,
`creditCard`, `cardNumber`, `email`, `phone`, `phoneNumber`, `accountNumber`, `pin`.

## Annotation-based masking

Use `@Mask` to force masking on a specific field even if it is not listed in `p11.masking.fields`.
Annotation settings override the global properties.

```java
import co.ke.xently.log.mask.Mask;
import co.ke.xently.log.mask.P11MaskingProperties;

public record UserDto(
        String name,
        @Mask String ssn,
        @Mask(style = P11MaskingProperties.MaskingStyle.LAST4, maskCharacter = "#") String cardNumber
) {}
```

## Highlights

- Testcontainers: Oracle FreeDB container wired via `@ServiceConnection` for repeatable integration tests.
- Spring Boot 4 + Jackson 3: Contextual `String` serializer applies masking to configured or annotated fields.
- Logback: `%msg` converter masks sensitive values in log output without manual `ObjectMapper` calls.
- Java 21: Records for DTOs and modern toolchain.
