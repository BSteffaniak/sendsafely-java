package com.sendsafely.cliapp;

/**
 * Exception for when the CLI encounters an error that the user cannot recover themselves from.
 */
public class CLIException extends RuntimeException {
    public CLIException(String message) {
        super(message);
    }

    public CLIException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage());
    }
}
