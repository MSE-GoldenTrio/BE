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

    public CustomException(String message, @Nullable String detail, HttpStatus status, @Nullable Throwable cause) {
        super(message);
        this.status = status;
        this.detail = detail;
        this.stackTraceString = cause != null? getStackTraceAsString(cause) : null;
    }

    public static String getStackTraceAsString(Throwable e) {
        if(e == null) return null; // NPE 방지
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

}

