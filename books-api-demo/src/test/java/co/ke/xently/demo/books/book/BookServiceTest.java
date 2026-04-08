package co.ke.xently.demo.books.book;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import co.ke.xently.demo.books.book.exceptions.BookNotFoundException;
import co.ke.xently.log.mask.MaskingLogbackInitializer;
import co.ke.xently.log.mask.MaskingService;
import co.ke.xently.log.mask.P11MaskingProperties;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class BookServiceTest {
    private static final String RAW_EMAIL = "john.doe@example.com";
    private static final String RAW_PHONE = "0712345678";
    private static final String MASKED_EMAIL = "j*******@example.com";
    private static final String MASKED_PHONE = "0*********";
    private final BookRepository bookRepository = Mockito.mock(BookRepository.class);
    private final BookService service = new BookService(bookRepository);

    private static void applyLogMasking() {
        var props = P11MaskingProperties.builder()
                .enabled(true)
                .maskStyle(P11MaskingProperties.MaskingStyle.PARTIAL)
                .maskCharacter("*")
                .fields(List.of("email", "phoneNumber"))
                .build();
        var maskingService = new MaskingService(props);

        var context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.reset();

        var encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -- %m%n");
        encoder.start();

        var appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.start();

        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        root.addAppender(appender);

        new MaskingLogbackInitializer(maskingService, props).initialize();
    }

    private static String firstLineContaining(String output, String token) {
        return output.lines()
                .filter(line -> line.contains(token))
                .findFirst()
                .orElse("");
    }

    private static void assertMasked(String output, List<String> expected, List<String> unexpected) {
        var checks = new java.util.ArrayList<Executable>();
        for (String value : expected) {
            checks.add(() -> assertThat(output, containsString(value)));
        }
        for (String value : unexpected) {
            checks.add(() -> assertThat(output, not(containsString(value))));
        }
        assertAll(checks);
    }

    @BeforeEach
    void setUp() {
        applyLogMasking();
    }

    @Test
    void shouldGetAllBooks() {
        var book1 = Book.builder().id(1L).title("T1").author("A1").email("e1@test.com").phoneNumber("0712345678").build();
        var book2 = Book.builder().id(2L).title("T2").author("A2").email("e2@test.com").phoneNumber("0712345679").build();
        when(bookRepository.findAll()).thenReturn(List.of(book1, book2));

        List<BookDto> list = service.getAllBooks();

        assertAll(
                () -> assertThat(list.size(), is(2)),
                () -> assertThat(list, contains(
                        new BookDto("T1", "A1", "e1@test.com", "0712345678"),
                        new BookDto("T2", "A2", "e2@test.com", "0712345679")
                ))
        );
    }

    @ParameterizedTest(name = "shouldCreateBookFromDto: {0},{1}")
    @CsvSource({
            "Title, Author",
            "Another, Someone"
    })
    void shouldCreateBookFromDto(String title, String author) {
        var input = new BookDto(title, author, "john.doe@test.com", "0712345678");
        var saved = Book.builder().id(10L).title(title).author(author).email(input.email()).phoneNumber(input.phoneNumber()).build();
        when(bookRepository.save(any(Book.class))).thenReturn(saved);

        BookDto result = service.createBook(input);

        var captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        Book toSave = captor.getValue();

        assertAll(
                () -> assertThat(toSave.getId(), nullValue()),
                () -> assertThat(result, equalTo(new BookDto(title, author, input.email(), input.phoneNumber())))
        );
    }

    @Test
    void shouldGetBookByIdOrThrow() {
        var stored = Book.builder().id(2L).title("T").author("A").email("e@test.com").phoneNumber("0712345678").build();
        when(bookRepository.findById(2L)).thenReturn(Optional.of(stored));

        BookDto found = service.getBookById(2L);

        assertThat(found, equalTo(new BookDto("T", "A", "e@test.com", "0712345678")));
    }

    @Test
    void shouldThrowWhenBookNotFoundById() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BookNotFoundException.class, () -> service.getBookById(99L));
    }

    @Test
    void shouldUpdateExistingBook() {
        var existing = Book.builder().id(5L).title("Old").author("Auth").email("old@test.com").phoneNumber("0700000000").build();
        when(bookRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        var update = new BookDto("New", "Auth2", "new@test.com", "0711111111");
        BookDto updated = service.updateBook(5L, update);

        assertAll(
                () -> assertThat(updated, equalTo(update)),
                () -> assertThat(existing.getTitle(), equalTo("New")),
                () -> assertThat(existing.getAuthor(), equalTo("Auth2")),
                () -> assertThat(existing.getEmail(), equalTo("new@test.com")),
                () -> assertThat(existing.getPhoneNumber(), equalTo("0711111111"))
        );
    }

    @Test
    void shouldDeleteBookById() {
        service.deleteBook(7L);
        verify(bookRepository).deleteById(7L);
    }

    private record LogCase(
            String name,
            Consumer<BookRepository> stubbing,
            Consumer<BookService> action,
            String marker,
            List<String> expected,
            List<String> unexpected
    ) {
        @Override
        public @NonNull String toString() {
            return name;
        }
    }

    @Nested
    class LogMasking {
        static Stream<LogCase> logCases() {
            return Stream.of(
                    new LogCase(
                            "createBook logs",
                            repo -> {
                                var input = new BookDto("Title", "Author", RAW_EMAIL, RAW_PHONE);
                                var saved = Book.builder()
                                        .id(10L)
                                        .title(input.title())
                                        .author(input.author())
                                        .email(input.email())
                                        .phoneNumber(input.phoneNumber())
                                        .build();
                                when(repo.save(any(Book.class))).thenReturn(saved);
                            },
                            svc -> svc.createBook(new BookDto("Title", "Author", RAW_EMAIL, RAW_PHONE)),
                            "Creating book:",
                            List.of(MASKED_EMAIL, MASKED_PHONE),
                            List.of(RAW_EMAIL, RAW_PHONE)
                    ),
                    new LogCase(
                            "updateBook logs",
                            repo -> {
                                var existing = Book.builder()
                                        .id(5L)
                                        .title("Old")
                                        .author("Auth")
                                        .email(RAW_EMAIL)
                                        .phoneNumber(RAW_PHONE)
                                        .build();
                                when(repo.findById(5L)).thenReturn(Optional.of(existing));
                                when(repo.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
                            },
                            svc -> svc.updateBook(5L, new BookDto("New", "Auth2", RAW_EMAIL, RAW_PHONE)),
                            "Updating book with id:",
                            List.of(MASKED_EMAIL, MASKED_PHONE),
                            List.of(RAW_EMAIL, RAW_PHONE)
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("logCases")
        void shouldMaskLoggedOutput(LogCase logCase, CapturedOutput output) {
            Mockito.reset(bookRepository);
            logCase.stubbing().accept(bookRepository);
            logCase.action().accept(service);

            var line = firstLineContaining(output.getOut(), logCase.marker());

            assertAll(
                    () -> assertThat(line, not(emptyString())),
                    () -> assertMasked(line, logCase.expected(), logCase.unexpected())
            );
        }
    }
}
