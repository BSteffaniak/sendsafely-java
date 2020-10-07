package com.sendsafely.cliapp;

public class FilePromptException extends RuntimeException {
  public FilePromptException(String message) {
    super(message);
  }

  public FilePromptException(String message, Throwable cause) {
    super(message + ": " + cause.getMessage());
  }
}
