package com.example.iplan.ExceptionHandler;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

@Getter
public class CustomException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;

    public CustomException(String message, @Nullable String detail, HttpStatus status) {
        super(message);
        this.status = status;
        this.detail = detail;
    }
}

