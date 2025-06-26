package com.example.iplan.auth.ExceptionHandler;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

@Getter
public class CustomAuthenticationException extends AuthenticationException {

    private final HttpStatus status;
    public CustomAuthenticationException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}

