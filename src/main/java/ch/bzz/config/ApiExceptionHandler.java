package ch.bzz.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Void> handleMissingHeader(MissingRequestHeaderException exception) {
        HttpStatus status = "Authorization".equalsIgnoreCase(exception.getHeaderName())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).build();
    }
}
