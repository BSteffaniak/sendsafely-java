package com.sendsafely.cliapp;

import com.sendsafely.SendSafely;
import com.sendsafely.dto.UserInformation;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
  name = "SendSafelyCLI",
  mixinStandardHelpOptions = true,
  version = "1.0",
  description = "Simple command-line application for uploading files with the SendSafely API"
)
class SendSafelyCLI implements Callable<Integer> {
  @Parameters(
    index = "0",
    description = "The action to execute (upload-file, undo)"
  )
  private String action;

  @Parameters(
    index = "1..*",
    description = "aaaa"
  )
  private String[] args;

  // this example implements Callable, so parsing, error handling and handling user
  // requests for usage help or version help can be done with one line of code.
  public static void main(String... args) {
    int exitCode = new CommandLine(new SendSafelyCLI()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception { // your business logic goes here...
    SendSafely sendSafelyAPI = new SendSafely("https://demo.sendsafely.com", "", "");

    UserInformation userInformation = sendSafelyAPI.getUserInformation();

    System.out.println(userInformation.getEmail() + " " + userInformation.getFirstName() + " " + userInformation.getLastName());

    return 0;
  }
}