package com.sendsafely.cliapp;

/**
 * An exception thrown when the location for a File is invalid.
 */
public class FilePromptException extends RuntimeException {
  public FilePromptException(String message) {
    super(message);
  }
}
