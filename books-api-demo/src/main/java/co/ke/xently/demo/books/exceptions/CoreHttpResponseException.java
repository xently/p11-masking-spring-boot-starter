package co.ke.xently.demo.books.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class CoreHttpResponseException extends ResponseStatusException {
    private final String errorCode;

    public CoreHttpResponseException(HttpStatus httpStatus, String errorCode) {
        super(httpStatus, errorCode);
        this.errorCode = errorCode;
    }
}
