package com.example.conjur.lib;

public class ConjurException extends RuntimeException {

    public ConjurException(String message) {
        super(message);
    }

    public ConjurException(String message, Throwable cause) {
        super(message, cause);
    }
}
