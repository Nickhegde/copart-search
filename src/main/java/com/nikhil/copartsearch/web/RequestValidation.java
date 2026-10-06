package com.nikhil.copartsearch.web;

/** Input checks shared by the API controllers. Each throws BadRequestException on failure. */
final class RequestValidation {

    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SUGGEST_LIMIT = 20;

    private RequestValidation() {
        // utility class
    }

    static void requireMaxLength(String name, String value, int max) {
        if (value.length() > max) {
            throw new BadRequestException(name + " must be at most " + max + " characters");
        }
    }

    static void requireAtLeast(String name, int value, int min) {
        if (value < min) {
            throw new BadRequestException(name + " must be >= " + min);
        }
    }

    static void requireBetween(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new BadRequestException(name + " must be between " + min + " and " + max);
        }
    }
}