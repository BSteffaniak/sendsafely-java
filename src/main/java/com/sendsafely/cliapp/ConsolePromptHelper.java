package com.sendsafely.cliapp;

import de.codeshelf.consoleui.elements.ConfirmChoice;
import de.codeshelf.consoleui.prompt.*;
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.PromptBuilder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConsolePromptHelper {
  public File promptForFile(String message) throws IOException {
    String location = promptForString(message);

    if (location.isEmpty()) {
      throw new FilePromptException("Please give a file name");
    }

    File file = new File(location);

    if (file.isDirectory()) {
      throw new FilePromptException("Cannot upload a directory");
    } else if (!file.isFile()) {
      throw new FilePromptException("File does not exist at '" + file.getCanonicalPath() + "'");
    }

    return file;
  }

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
