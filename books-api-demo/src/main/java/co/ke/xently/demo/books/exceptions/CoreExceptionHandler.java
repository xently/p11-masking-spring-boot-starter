package co.ke.xently.demo.books.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.time.Instant;

@Slf4j
@ControllerAdvice
public class CoreExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception exception, HttpServletRequest request) {
        log.error(exception.getMessage(), exception);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setDetail(exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errorCode", "SERVER_ERROR");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    @ResponseBody
    @ExceptionHandler(CoreHttpResponseException.class)
    public ResponseEntity<ProblemDetail> handle(CoreHttpResponseException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(exception.getStatusCode());
        problem.setDetail(exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errorCode", exception.getErrorCode());

        return ResponseEntity.status(exception.getStatusCode())
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }
}