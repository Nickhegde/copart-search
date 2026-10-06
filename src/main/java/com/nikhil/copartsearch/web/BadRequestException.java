package com.nikhil.copartsearch.web;

/** Thrown for invalid client input; mapped to HTTP 400 by ApiExceptionHandler. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}