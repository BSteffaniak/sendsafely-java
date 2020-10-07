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

class SendSafelyCLI {
  private SendSafely sendSafelyAPI;
  private ConsolePromptHelper consolePromptHelper;
  private Package currentPackage;
  private UserInformation userInformation;
  private Set<String> addedRecipients;

  private Stack<Runnable> undoActions;

  public static void main(String... args) {
    SendSafelyCLI cli = new SendSafelyCLI(new ConsolePromptHelper());

    try {
      cli.call();
    } catch (CLIException | IOException exception) {
      System.err.println(exception.getMessage());

      System.exit(1);
    }

    System.exit(0);
  }

  public static void restoreTerminalFactory() {
    try {
      TerminalFactory.get().restore();
    } catch (Exception e) {
      throw new CLIException("Failed to restore terminal factory", e);
    }
  }

  public SendSafelyCLI(ConsolePromptHelper consolePromptHelper) {
    this.consolePromptHelper = consolePromptHelper;
  }

  public void call() throws CLIException, IOException {
    undoActions = new Stack<>();
    addedRecipients = new HashSet<>();

    AnsiConsole.systemInstall();

    loginUser();

    try {
      while (true) {
        ImmutableMap.Builder<ActionType, String> optionsBuilder = ImmutableMap.builder();

        if (currentPackage != null) {
          optionsBuilder.put(ActionType.UPLOAD_FILE, "Upload file");
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

  public void clearCurrentPackage() {
    currentPackage = null;
    addedRecipients.clear();
  }

  public void logoutUser() {
    undoActions.clear();
    clearCurrentPackage();
    sendSafelyAPI = null;
    userInformation = null;
  }

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

  public SendSafely getSendSafelyAPIForKeyAndSecret(String apiKey, String apiSecret) {
    return new SendSafely("https://app.sendsafely.com", apiKey, apiSecret);
  }

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

  public void undoPreviousAction() {
    if (undoActions.empty()) {
      System.err.println("No actions available to be undone, but I'm sure you knew that already. You're doing great!");
    } else {
      Runnable action = undoActions.pop();

      action.run();
    }
  }

  public void createPackage() {
    try {
      currentPackage = sendSafelyAPI.createPackage();

      System.out.println("Successfully created package");

      undoActions.push(() -> {
        try {
          sendSafelyAPI.deletePackage(currentPackage.getPackageId());

          System.out.println("Successfully deleted package");
        } catch (DeletePackageException e) {
          System.err.println("Failed to delete packages: " + e.getError());
        }

        currentPackage = null;
      });
    } catch (CreatePackageFailedException | LimitExceededException e) {
      System.err.println("Failed to create package: " + e);
    }
  }

  public void uploadFile() throws IOException {
    try {
      final FileManager fileManager;
      final File file = consolePromptHelper.promptForFile("Enter the file location");

      try {
        fileManager = new DefaultFileManager(file);
      } catch (IOException e) {
        throw new CLIException("Failed to create file manager", e);
      }

      try (ProgressBar progressBar = new ASCIIProgressBar("File Upload", 100)) {
        FileUploadProgress fileUploadProgress = new FileUploadProgress(progressBar);

        try {
          com.sendsafely.File addedFile = sendSafelyAPI.encryptAndUploadFile(currentPackage.getPackageId(), currentPackage.getKeyCode(), fileManager, fileUploadProgress);

          undoActions.push(() -> {
            try {
              System.out.println("Deleting file '" + file.getCanonicalPath() + "'");

              sendSafelyAPI.deleteFile(currentPackage.getPackageId(), currentPackage.getRootDirectoryId(), addedFile.getFileId());

              System.out.println("Deleted file successfully");
            } catch (FileOperationFailedException | IOException e) {
              throw new CLIException("Failed to delete file from package", e);
            }
          });

          progressBar.stepTo(100);
        } catch (LimitExceededException | UploadFileException e) {
          System.err.println("Failed to upload file:" + e.getMessage());
        }
      }

      System.out.println("File successfully uploaded");

      while (true) {
        ActionType action = consolePromptHelper.promptForAction(
          "What would you like to do?",
          ImmutableMap.<ActionType, String>builder()
            .put(ActionType.UPLOAD_FILE, "Upload another file")
            .put(ActionType.ADD_RECIPIENTS, "Add recipients")
            .put(ActionType.ADD_YOURSELF_AS_RECIPIENT, "Add yourself as a recipient")
            .put(ActionType.FINALIZE, "Finalize file")
            .put(ActionType.UNDO, "Undo")
            .put(ActionType.LOGOUT, "Logout")
            .put(ActionType.QUIT, "Rage quit (lose all unfinalized changes)")
            .build()
        );

        switch (action) {
          case UPLOAD_FILE:
            uploadFile();
          case FINALIZE:
            if (finalizePackage()) {
              clearCurrentPackage();
              return;
            }
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
            return;
          case QUIT:
            quit();
            return;
          default:
            throw new CLIException("Invalid action: " + action);
        }
      }
    } catch (FilePromptException e) {
      System.err.println(e.getMessage());

      if (consolePromptHelper.promptForConfirmation("Try a new file?")) {
        uploadFile();
      } else {
        quit();
      }
    }
  }

  public void quit() {
    System.out.println("Bye ♥");

    System.exit(0);
  }

  public boolean finalizePackage() {
    try {
      PackageURL packageURL = sendSafelyAPI.finalizePackage(currentPackage.getPackageId(), currentPackage.getKeyCode());

      System.out.println("Secure link: " + packageURL.getSecureLink());

      undoActions.clear();
      undoActions.push(() -> {
        System.err.println("Cannot unfinalize a package (that I'm aware of)");
      });

      return true;
    } catch (LimitExceededException | FinalizePackageFailedException | ApproverRequiredException e) {
      System.err.println("Failed to finalize package: " + e.getMessage());

      return false;
    }
  }

  public void addRecipients() throws IOException {
    String recipientEmail = consolePromptHelper.promptForString("Enter recipient email:").trim();

    addRecipients(recipientEmail);
  }

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