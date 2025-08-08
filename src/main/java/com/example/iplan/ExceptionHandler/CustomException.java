package com.example.iplan.ExceptionHandler;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

@Getter
public class CustomException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;
    private final String stackTraceString;

    public CustomException(String message, @Nullable String detail, HttpStatus status, @Nullable Exception error) {
        super(message);
        this.status = status;
        this.detail = detail;
        assert error != null;
        this.stackTraceString = getStackTraceAsString(error);
    }

    public static String getStackTraceAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

}

