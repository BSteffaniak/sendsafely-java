package com.sendsafely.cliapp;

import com.google.common.collect.ImmutableMap;
import com.sendsafely.Package;
import com.sendsafely.Recipient;
import com.sendsafely.SendSafely;
import com.sendsafely.dto.PackageURL;
import com.sendsafely.dto.UserInformation;
import com.sendsafely.exceptions.*;
import com.sendsafely.file.DefaultFileManager;
import com.sendsafely.file.FileManager;
import jline.TerminalFactory;
import me.tongfei.progressbar.ProgressBar;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * A small CLI application for interfacing with the SendSafely API.
 */
class SendSafelyCLI {
  private SendSafely sendSafelyAPI;
  private ConsolePromptHelper consolePromptHelper;
  private Package currentPackage;
  private UserInformation userInformation;
  private Set<String> addedRecipients;

  private Stack<Runnable> undoActions;

  /**
   * Start the CLI application. Exit code 1 for any uncaught CLIExceptions or IOExceptions.
   * Exit code 0 for all successful outcomes.
   */
  public static void main(String... args) {
    SendSafelyCLI cli = new SendSafelyCLI(new ConsolePromptHelper());

    try {
      cli.start();
    } catch (CLIException | IOException exception) {
      System.err.println(exception.getMessage());

      System.exit(1);
    }

    System.exit(0);
  }

  /**
   * Restore the jline.TerminalFactory back to its default state.
   */
  public static void restoreTerminalFactory() {
    try {
      TerminalFactory.get().restore();
    } catch (Exception e) {
      throw new CLIException("Failed to restore terminal factory", e);
    }
  }

  /**
   * Create a new SendSafelyCLI in a fresh state.
   *
   * @param consolePromptHelper An object with prompt helper functions.
   */
  public SendSafelyCLI(ConsolePromptHelper consolePromptHelper) {
    this.consolePromptHelper = consolePromptHelper;

    undoActions = new Stack<>();
    addedRecipients = new HashSet<>();
  }

  /**
   * Start the CLI program. This starts the user off with prompting login credentials, then
   * moves into the main menu where a user can create a package, upload a file, add recipients
   * to the current package, undo the previous action, logout, or quit the program.
   */
  public void start() throws CLIException, IOException {
    AnsiConsole.systemInstall();

    loginUser();

    try {
      while (true) {
        ImmutableMap.Builder<ActionType, String> optionsBuilder = ImmutableMap.builder();

        if (currentPackage != null) {
          optionsBuilder
            .put(ActionType.UPLOAD_FILE, "Upload file")
            .put(ActionType.ADD_RECIPIENTS, "Add recipients")
            .put(ActionType.ADD_YOURSELF_AS_RECIPIENT, "Add yourself as a recipient")
            .put(ActionType.FINALIZE, "Finalize package");
        } else {
          optionsBuilder.put(ActionType.CREATE_PACKAGE, "Create package");
        }

        if (!undoActions.isEmpty()) {
          optionsBuilder.put(ActionType.UNDO, "Undo");
        }

        optionsBuilder
          .put(ActionType.LOGOUT, "Logout")
          .put(ActionType.QUIT, "Quit");

        ActionType action = consolePromptHelper.promptForAction(
          "What would you like to do?",
          optionsBuilder.build()
        );

        switch (action) {
          case CREATE_PACKAGE:
            createPackage();
            break;
          case UPLOAD_FILE:
            uploadFile();
            break;
          case FINALIZE:
            finalizePackage();
            break;
          case ADD_RECIPIENTS:
            addRecipients();
            break;
          case ADD_YOURSELF_AS_RECIPIENT:
            addRecipients(userInformation.getEmail());
            break;
          case UNDO:
            undoPreviousAction();
            break;
          case LOGOUT:
            logoutUser();
            loginUser();
            break;
          case QUIT:
            quit();
            return;
          default:
            throw new CLIException("Invalid action: " + action);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      restoreTerminalFactory();
    }
  }

  /**
   * Clear the state around the current package.
   */
  public void clearCurrentPackage() {
    currentPackage = null;
    addedRecipients.clear();
  }

  /**
   * Logout the currently logged in user and clear the sendSafelyAPI properties.
   */
  public void logoutUser() {
    undoActions.clear();
    clearCurrentPackage();
    sendSafelyAPI = null;
    userInformation = null;
  }

  /**
   * Promp the user with a menu where they can login or quit the program.
   */
  public void loginUser() throws IOException {
    while (true) {
      ActionType action = consolePromptHelper.promptForAction(
        "What would you like to do?",
        ImmutableMap.<ActionType, String>builder()
          .put(ActionType.LOGIN, "Login")
          .put(ActionType.QUIT, "Quit")
          .build()
      );

      switch (action) {
        case LOGIN:
          if (attemptLogin()) {
            return;
          }
          break;
        case QUIT:
          quit();
          return;
        default:
          throw new CLIException("Invalid action: " + action);
      }
    }
  }

  /**
   * Get a new SendSafely API instance with the given apiKey and apiSecret.
   *
   * @param apiKey The SendSafely api key for a user
   * @param apiSecret The SendSafely api secret for a user
   * @return The SendSafely API instance connected with the credentials
   */
  public SendSafely getSendSafelyAPIForKeyAndSecret(String apiKey, String apiSecret) {
    return new SendSafely("https://app.sendsafely.com", apiKey, apiSecret);
  }

  /**
   * Prompt for the user's api key and api secret, then try to log them into the API.
   *
   * @return Returns true if the user successfully logged in. False otherwise.
   */
  public boolean attemptLogin() throws IOException {
    String apiKey = consolePromptHelper.promptForPrivateString("Enter api key:");
    String apiSecret = consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):");

    if (apiKey.isEmpty() || apiSecret.isEmpty()) {
      System.err.println("Invalid credentials");

      return false;
    }

    sendSafelyAPI = getSendSafelyAPIForKeyAndSecret(apiKey, apiSecret);

    try {
      sendSafelyAPI.verifyCredentials();
      userInformation = sendSafelyAPI.getUserInformation();

      System.out.println("Successfully logged in! Welcome, " + userInformation.getFirstName() + "!!! Wooooo!");

      undoActions.push(() -> {
        logoutUser();

        System.out.println("Logged out!!");

        try {
          loginUser();
        } catch (IOException e) {
          System.err.println("Failed to login user: " + e.getMessage());
        }
      });

      return true;
    } catch (InvalidCredentialsException | UserInformationFailedException e) {
      System.err.println("Invalid credentials");

      return false;
    }
  }

  /**
   * Undo the most previously enacted action.
   */
  public void undoPreviousAction() {
    if (undoActions.empty()) {
      System.err.println("No actions available to be undone, but I'm sure you knew that already. You're doing great!");
    } else {
      Runnable action = undoActions.pop();

      action.run();
    }
  }

  /**
   * Delete the current package.
   */
  public void deleteCurrentPackage() throws DeletePackageException {
    sendSafelyAPI.deletePackage(currentPackage.getPackageId());

    currentPackage = null;
  }

  /**
   * Create a new SendSafely package and set it as the current package
   */
  public void createPackage() {
    try {
      currentPackage = sendSafelyAPI.createPackage();

      System.out.println("Successfully created package");

      undoActions.push(() -> {
        try {
          deleteCurrentPackage();

          System.out.println("Successfully deleted package");
        } catch (DeletePackageException e) {
          System.err.println("Failed to delete packages: " + e.getError());
        }
      });
    } catch (CreatePackageFailedException | LimitExceededException e) {
      System.err.println("Failed to create package: " + e);
    }
  }

  /**
   * Delete the given file from the current package.
   *
   * @param file The java.io.File version of the file to delete.
   * @param addedFile The com.sendSafely.File version of the file to delete.
   */
  public void deleteFile(File file, com.sendsafely.File addedFile) throws FileOperationFailedException, IOException {
    System.out.println("Deleting file '" + file.getCanonicalPath() + "'");

    sendSafelyAPI.deleteFile(currentPackage.getPackageId(), currentPackage.getRootDirectoryId(), addedFile.getFileId());
  }

  /**
   * Create a SendSafely FileManager for the given File.
   *
   * @param file The File to create a FileManager for.
   * @return A new FileManager for the File.
   */
  public FileManager createFileManager(File file) {
    try {
      return new DefaultFileManager(file);
    } catch (IOException e) {
      throw new FilePromptException("Failed to create file manager: " + e);
    }
  }

  /**
   * Enter a promp sequence for uploading a file to the current package.
   */
  public void uploadFile() throws IOException {
    try {
      final File file = consolePromptHelper.promptForFile("Enter the file location");

      FileManager fileManager = createFileManager(file);

      // Using try-with-resources to ensure the ProgressBar stream gets closed out after successful
      // and failed file uploads
      try (ProgressBar progressBar = new ASCIIProgressBar("File Upload", 100)) {
        FileUploadProgress fileUploadProgress = new FileUploadProgress(progressBar);

        try {
          com.sendsafely.File addedFile = sendSafelyAPI.encryptAndUploadFile(currentPackage.getPackageId(), currentPackage.getKeyCode(), fileManager, fileUploadProgress);

          undoActions.push(() -> {
            try {
              deleteFile(file, addedFile);

              System.out.println("Deleted file successfully");
            } catch (FileOperationFailedException | IOException e) {
              System.err.println("Failed to delete file from package: " + e.getMessage());
            }
          });

          progressBar.stepTo(100);
        } catch (LimitExceededException | UploadFileException e) {
          System.err.println("Failed to upload file:" + e.getMessage());
        }
      }

      System.out.println("File successfully uploaded");
    } catch (FilePromptException e) {
      System.err.println(e.getMessage());

      if (consolePromptHelper.promptForConfirmation("Try a new file?")) {
        uploadFile();
      }
    }
  }

  /**
   * Quit the app with exit code 0.
   */
  public void quit() {
    System.out.println("Bye ♥");

    System.exit(0);
  }

  /**
   * Finalize the current package and print out a secure link to that package.
   */
  public void finalizePackage() {
    try {
      PackageURL packageURL = sendSafelyAPI.finalizePackage(currentPackage.getPackageId(), currentPackage.getKeyCode());

      System.out.println("Secure link: " + packageURL.getSecureLink());

      undoActions.clear();
      undoActions.push(() -> {
        System.err.println("Cannot unfinalize a package (that I'm aware of)");
      });

      clearCurrentPackage();
    } catch (LimitExceededException | FinalizePackageFailedException | ApproverRequiredException e) {
      System.err.println("Failed to finalize package: " + e.getMessage());
    }
  }

  /**
   * Add a recipient to the current package.
   */
  public void addRecipients() throws IOException {
    String recipientEmail = consolePromptHelper.promptForString("Enter recipient email:").trim();

    addRecipients(recipientEmail);
  }

  /**
   * Add a predetermined recipient to the current package.
   *
   * @param recipientEmail The recipient to add.
   */
  public void addRecipients(String recipientEmail) {
    if (recipientEmail.isEmpty()) {
      System.err.println("Recipient cannot be empty");
    } else if (addedRecipients.contains(recipientEmail)) {
      System.err.println("Recipient '" + recipientEmail + "' already added");
    } else {
      try {
        Recipient recipient = sendSafelyAPI.addRecipient(currentPackage.getPackageId(), recipientEmail);

        addedRecipients.add(recipientEmail);

        System.out.println("Successfully added recipient '" + recipientEmail + "'");

        undoActions.push(() -> {
          System.out.println("Removing recipient '" + recipientEmail + "'");

          try {
            sendSafelyAPI.removeRecipient(currentPackage.getPackageId(), recipient.getRecipientId());

            addedRecipients.remove(recipientEmail);

            System.out.println("Recipient removed successfully");
          } catch (RecipientFailedException e) {
            System.err.println("Failed to remove recipient: " + e.getMessage());
          }
        });
      } catch (LimitExceededException | RecipientFailedException e) {
        System.err.println("Failed to add recipient: " + e.getMessage());
      }
    }
  }
}