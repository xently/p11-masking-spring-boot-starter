package co.ke.xently.demo.books.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoreExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new CoreExceptionHandler())
                .build();
    }

    @RestController
    static class TestController {
        @GetMapping("/test-exception")
        public void throwException() {
            throw new RuntimeException("Test exception");
        }

        @GetMapping("/test-core-exception")
        public void throwCoreException() {
            throw new CoreHttpResponseException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND");
        }

        @GetMapping("/test-validation-exception")
        public void throwValidationException() throws MethodArgumentNotValidException {
            var bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
            bindingResult.addError(new FieldError("testObject", "title", "must not be blank"));

            var method = new Object() {
            }.getClass().getEnclosingMethod();
            var parameter = new MethodParameter(method, -1);

            throw new MethodArgumentNotValidException(parameter, bindingResult);
        }
    }

    @Nested
    class GeneralExceptionHandling {
        @Test
        void shouldHandleGenericException() throws Exception {
            mockMvc.perform(get("/test-exception"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.detail", is("Test exception")))
                    .andExpect(jsonPath("$.errorCode", is("SERVER_ERROR")))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.instance", is("/test-exception")));
        }
    }

    @Nested
    class CoreExceptionHandling {
        @Test
        void shouldHandleCoreException() throws Exception {
            mockMvc.perform(get("/test-core-exception"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.detail", containsString("BOOK_NOT_FOUND")))
                    .andExpect(jsonPath("$.errorCode", is("BOOK_NOT_FOUND")))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.instance", is("/test-core-exception")));
        }
    }
}
