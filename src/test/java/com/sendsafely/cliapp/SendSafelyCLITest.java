package com.sendsafely.cliapp;

import com.google.common.collect.ImmutableMap;
import com.sendsafely.Package;
import com.sendsafely.SendSafely;
import com.sendsafely.dto.UserInformation;
import com.sendsafely.exceptions.*;
import com.sendsafely.file.FileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SendSafelyCLITest {
  SendSafelyCLI sendSafelyCLI;

  @Mock
  ConsolePromptHelper consolePromptHelper;

  @Mock
  SendSafely sendSafely;

  @BeforeEach
  void setup() {
    this.sendSafelyCLI = Mockito.spy(new SendSafelyCLI(consolePromptHelper));
  }

  @Test
  @DisplayName("attemptLogin | should not allow logging in with empty credentials")
  void attemptLogin_shouldNotAllowLoggingInWithEmptyCredentials() throws IOException {
    when(consolePromptHelper.promptForPrivateString(any())).thenReturn("");

    assertFalse(sendSafelyCLI.attemptLogin());
  }

  @Test
  @DisplayName("attemptLogin | should not allow logging in with invalid credentials")
  void attemptLogin_shouldNotAllowLoggingInWithInvalidCredentials() throws IOException, InvalidCredentialsException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.verifyCredentials()).thenThrow(new InvalidCredentialsException());

    assertFalse(sendSafelyCLI.attemptLogin());

    verify(sendSafelyCLI, times(1)).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");
  }

  @Test
  @DisplayName("attemptLogin | should not allow logging in with user information failure")
  void attemptLogin_shouldNotAllowLoggingInWithUserInformationFailure() throws IOException, UserInformationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenThrow(new UserInformationFailedException());

    assertFalse(sendSafelyCLI.attemptLogin());

    verify(sendSafelyCLI, times(1)).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");
  }

  @Test
  @DisplayName("attemptLogin | should successfully log in with valid credentials")
  void attemptLogin_shouldSuccessfullyLogInWithValidCredentials() throws IOException, UserInformationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);

    assertTrue(sendSafelyCLI.attemptLogin());
  }

  @Test
  @DisplayName("undoPreviousAction | should successfully undo log in if undo called after login")
  void undoPreviousAction_shouldSuccessfullyUndoLogInIfUndoCalledAfterLogin() throws IOException, UserInformationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");
    doNothing().when(sendSafelyCLI).loginUser();

    when(sendSafely.getUserInformation()).thenReturn(userInformation);

    sendSafelyCLI.attemptLogin();

    verify(sendSafelyCLI, times(0)).logoutUser();

    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(1)).logoutUser();
  }

  @Test
  @DisplayName("undoPreviousAction | should not call logout twice if trying to undo twice")
  void undoPreviousAction_shouldNotCallLogoutTwiceIfTryingToUndoTwice() throws IOException, UserInformationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");
    doNothing().when(sendSafelyCLI).loginUser();

    when(sendSafely.getUserInformation()).thenReturn(userInformation);

    sendSafelyCLI.attemptLogin();

    verify(sendSafelyCLI, times(0)).logoutUser();

    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(1)).logoutUser();

    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(1)).logoutUser();
  }

  @Test
  @DisplayName("start | should not show undo in menu if there are no actions to undo")
  void start_shouldNotShowUndoInMenuIfThereAreNoActionsToUndo() throws IOException {
    doNothing().when(sendSafelyCLI).loginUser();
    doNothing().when(sendSafelyCLI).quit();

    when(
      consolePromptHelper.promptForAction(
        any(),
        eq(
          ImmutableMap.<ActionType, String>builder()
            .put(ActionType.CREATE_PACKAGE, "Create package")
            .put(ActionType.LOGOUT, "Logout")
            .put(ActionType.QUIT, "Quit")
            .build()
        )
      )
    )
      .thenReturn(ActionType.QUIT);

    sendSafelyCLI.start();

    verify(consolePromptHelper, times(1)).promptForAction(
      any(),
      eq(
        ImmutableMap.<ActionType, String>builder()
          .put(ActionType.CREATE_PACKAGE, "Create package")
          .put(ActionType.LOGOUT, "Logout")
          .put(ActionType.QUIT, "Quit")
          .build()
      )
    );
  }

  @Test
  @DisplayName("start | should show undo in menu if there are actions to undo")
  void start_shouldShowUndoInMenuIfThereAreActionsToUndo() throws IOException, UserInformationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);

    doNothing().when(sendSafelyCLI).quit();

    when(
      consolePromptHelper.promptForAction(
        any(),
        eq(
          ImmutableMap.<ActionType, String>builder()
            .put(ActionType.LOGIN, "Login")
            .put(ActionType.QUIT, "Quit")
            .build()
        )
      )
    )
      .thenReturn(ActionType.LOGIN);

    when(
      consolePromptHelper.promptForAction(
        any(),
        eq(
          ImmutableMap.<ActionType, String>builder()
            .put(ActionType.CREATE_PACKAGE, "Create package")
            .put(ActionType.UNDO, "Undo")
            .put(ActionType.LOGOUT, "Logout")
            .put(ActionType.QUIT, "Quit")
            .build()
        )
      )
    )
      .thenReturn(ActionType.QUIT);

    sendSafelyCLI.start();

    verify(consolePromptHelper, times(1)).promptForAction(
      any(),
      eq(
        ImmutableMap.<ActionType, String>builder()
          .put(ActionType.CREATE_PACKAGE, "Create package")
          .put(ActionType.UNDO, "Undo")
          .put(ActionType.LOGOUT, "Logout")
          .put(ActionType.QUIT, "Quit")
          .build()
      )
    );
  }

  @Test
  @DisplayName("start | should allow uploading file if a package has been created")
  void start_shouldAllowUploadingFileIfAPackageHasBeenCreated() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.QUIT);

    sendSafelyCLI.start();

    verify(consolePromptHelper, times(1)).promptForAction(
      any(),
      eq(
        ImmutableMap.<ActionType, String>builder()
          .put(ActionType.CREATE_PACKAGE, "Create package")
          .put(ActionType.UNDO, "Undo")
          .put(ActionType.LOGOUT, "Logout")
          .put(ActionType.QUIT, "Quit")
          .build()
      )
    );

    verify(consolePromptHelper, times(1)).promptForAction(
      any(),
      eq(
        ImmutableMap.<ActionType, String>builder()
          .put(ActionType.UPLOAD_FILE, "Upload file")
          .put(ActionType.UNDO, "Undo")
          .put(ActionType.LOGOUT, "Logout")
          .put(ActionType.QUIT, "Quit")
          .build()
      )
    );
  }

  @Test
  @DisplayName("start | should be able to undo creating a package")
  void start_shouldBeAbleToUndoCreatingAPackage() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException, DeletePackageException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.QUIT);

    sendSafelyCLI.start();

    verify(sendSafelyCLI, times(0)).deleteCurrentPackage();

    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(1)).deleteCurrentPackage();
  }

  @Test
  @DisplayName("start | should be able to upload a file to a package")
  void start_shouldBeAbleToUploadAFileToAPackage() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException, DeletePackageException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();
    doNothing().when(sendSafelyCLI).uploadFile();

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.QUIT);

    sendSafelyCLI.start();

    verify(sendSafelyCLI, times(1)).uploadFile();
  }

  @Test
  @DisplayName("start | should be able to undo uploading a file to a package")
  void start_shouldBeAbleToUndoUploadingAFileToAPackage() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException, DeletePackageException, FileOperationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();
    doNothing().when(sendSafelyCLI).deleteFile(any(), any());
    doReturn(mock(FileManager.class)).when(sendSafelyCLI).createFileManager(any());

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.QUIT);

    when(consolePromptHelper.promptForFile(any()))
      .thenReturn(mock(File.class));

    verify(sendSafelyCLI, times(0)).deleteFile(any(), any());

    sendSafelyCLI.start();

    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(1)).deleteFile(any(), any());
  }

  @Test
  @DisplayName("start | should be able to upload multiple files to a package")
  void start_shouldBeAbleToUploadMultipleFilesToAPackage() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException, DeletePackageException, FileOperationFailedException, UploadFileException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();
    doReturn(mock(FileManager.class)).when(sendSafelyCLI).createFileManager(any());

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.QUIT);

    when(consolePromptHelper.promptForFile(any()))
      .thenReturn(mock(File.class));

    verify(sendSafely, times(0)).encryptAndUploadFile(any(), any(), any(), any());

    sendSafelyCLI.start();

    verify(sendSafely, times(2)).encryptAndUploadFile(any(), any(), any(), any());
  }

  @Test
  @DisplayName("start | should be able to undo second uploaded file to a package")
  void start_shouldBeAbleToUndoSecondUploadedFileToAPackage() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException, DeletePackageException, FileOperationFailedException, UploadFileException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();
    doNothing().when(sendSafelyCLI).deleteFile(any(), any());
    doReturn(mock(FileManager.class)).when(sendSafelyCLI).createFileManager(any());

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.QUIT);

    when(consolePromptHelper.promptForFile(any()))
      .thenReturn(mock(File.class));

    verify(sendSafelyCLI, times(0)).deleteFile(any(), any());

    sendSafelyCLI.start();

    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(1)).deleteFile(any(), any());
  }

  @Test
  @DisplayName("start | should be able to undo both uploaded files to a package")
  void start_shouldBeAbleToUndoBothUploadedFilesToAPackage() throws IOException, UserInformationFailedException, CreatePackageFailedException, LimitExceededException, DeletePackageException, FileOperationFailedException, UploadFileException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    UserInformation userInformation = mock(UserInformation.class);
    Package pkgInfo = mock(Package.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenReturn(userInformation);
    when(sendSafely.createPackage()).thenReturn(pkgInfo);

    doNothing().when(sendSafelyCLI).quit();
    doNothing().when(sendSafelyCLI).deleteFile(any(), any());
    doReturn(mock(FileManager.class)).when(sendSafelyCLI).createFileManager(any());

    when(consolePromptHelper.promptForAction(any(), any()))
      .thenReturn(ActionType.LOGIN)
      .thenReturn(ActionType.CREATE_PACKAGE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.UPLOAD_FILE)
      .thenReturn(ActionType.QUIT);

    when(consolePromptHelper.promptForFile(any()))
      .thenReturn(mock(File.class));

    verify(sendSafelyCLI, times(0)).deleteFile(any(), any());

    sendSafelyCLI.start();

    sendSafelyCLI.undoPreviousAction();
    sendSafelyCLI.undoPreviousAction();

    verify(sendSafelyCLI, times(2)).deleteFile(any(), any());
  }
}
