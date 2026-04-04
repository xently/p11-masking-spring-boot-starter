package co.ke.xently.demo.books.book.exceptions;

import co.ke.xently.demo.books.exceptions.CoreHttpResponseException;
import org.springframework.http.HttpStatus;

public class BookNotFoundException extends CoreHttpResponseException {
    public BookNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Book not found");
    }
}
