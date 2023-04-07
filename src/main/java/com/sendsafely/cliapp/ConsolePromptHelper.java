package com.sendsafely.cliapp;

import de.codeshelf.consoleui.elements.ConfirmChoice;
import de.codeshelf.consoleui.prompt.*;
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.PromptBuilder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper functions for getting user input from the console.
 */
public class ConsolePromptHelper {
  /**
   * Prompt for a file location, then return the java.io.File for that location.
   *
   * @param message The message to display in the prompt
   * @return The java.io.File for the given location
   */
  public File promptForFile(String message) throws IOException {
    String location = promptForString(message);

    if (location.isEmpty()) {
      throw new FilePromptException("Please give a file name");
    }

    while (location.startsWith("\"") && location.endsWith("\"") || location.startsWith("'") && location.endsWith("'")) {
      location = location.substring(1, location.length() - 1);
    }

    File file = new File(location);

    if (!file.exists()) {
      throw new FilePromptException("File does not exist at '" + file.getCanonicalPath() + "'");
    }

    return file;
  }

  /**
   * Prompt for a String response.
   *
   * @param message The message to display in the prompt
   * @return The String response from the user
   */
  public String promptForString(String message) throws IOException {
    ConsolePrompt consolePrompt = new ConsolePrompt();
    PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();

    promptBuilder.createInputPrompt()
      .name("value")
      .message(message)
      .defaultValue("")
      .addPrompt();

    HashMap<String, ? extends PromtResultItemIF> result = consolePrompt.prompt(promptBuilder.build());

    InputResult inputResult = (InputResult) result.get("value");

    return inputResult.getInput();
  }

  /**
   * Prompt for a masked String. Useful for reading sensitive data.
   *
   * @param message The message to display in the prompt
   * @return The String response from the user
   */
  public String promptForPrivateString(String message) throws IOException {
    ConsolePrompt consolePrompt = new ConsolePrompt();
    PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();

    promptBuilder.createInputPrompt()
      .name("value")
      .message(message)
      .mask('*')
      .defaultValue("")
      .addPrompt();

    HashMap<String, ? extends PromtResultItemIF> result = consolePrompt.prompt(promptBuilder.build());

    InputResult inputResult = (InputResult) result.get("value");

    return inputResult.getInput();
  }

  /**
   * Get which ActionType is selected from the given options Map.
   *
   * @param message The message to display in the prompt
   * @param options The Map of ActionTypes -> label for the options
   * @return The ActionType response from the user
   */
  public ActionType promptForAction(String message, Map<ActionType, String> options) throws IOException {
    ConsolePrompt consolePrompt = new ConsolePrompt();
    PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();

    ListPromptBuilder listPromptBuilder = promptBuilder.createListPrompt()
      .name("action")
      .message(message);

    options.forEach((actionType, label) -> listPromptBuilder.newItem(actionType.name()).text(label).add());

    listPromptBuilder.addPrompt();

    HashMap<String, ? extends PromtResultItemIF> result = consolePrompt.prompt(promptBuilder.build());

    ListResult item = (ListResult) result.get("action");

    return ActionType.valueOf(item.getSelectedId());
  }

  /**
   * Get a boolean response from the user.
   *
   * @param message The message to display in the prompt
   * @return The yes/no boolean response from the user
   */
  public boolean promptForConfirmation(String message) throws IOException {
    ConsolePrompt consolePrompt = new ConsolePrompt();
    PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();

    promptBuilder.createConfirmPromp()
      .name("response")
      .message(message)
      .addPrompt();

    HashMap<String, ? extends PromtResultItemIF> result = consolePrompt.prompt(promptBuilder.build());

    ConfirmResult item = (ConfirmResult) result.get("response");

    return item.getConfirmed() == ConfirmChoice.ConfirmationValue.YES;
  }
}
