package com.sendsafely.cliapp;

import com.google.common.collect.ImmutableMap;
import com.sendsafely.Package;
import com.sendsafely.Privatekey;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

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
                        .build())))
                            .thenReturn(ActionType.QUIT);

        sendSafelyCLI.start();

        verify(consolePromptHelper, times(1)).promptForAction(
            any(),
            eq(
                ImmutableMap.<ActionType, String>builder()
                    .put(ActionType.CREATE_PACKAGE, "Create package")
                    .put(ActionType.LOGOUT, "Logout")
                    .put(ActionType.QUIT, "Quit")
                    .build()));
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
          .put(ActionType.ADD_RECIPIENTS, "Add recipients")
          .put(ActionType.ADD_YOURSELF_AS_RECIPIENT, "Add yourself as a recipient")
          .put(ActionType.FINALIZE, "Finalize package")
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
    doReturn(true).when(sendSafelyCLI).uploadFile();

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

  @Test
  void keygenPersistsKeyWithoutPrintingPrivateMaterial() throws Exception {
    Path directory = Files.createTempDirectory("sendsafely-keygen");
    Path credentials = directory.resolve("credentials.json");
    Files.write(credentials,
      ("{\"apiKey\":\"api-key\",\"apiKeySecret\":\"api-secret\"," +
        "\"preserved\":\"value\"}").getBytes(StandardCharsets.UTF_8));

    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    SendSafelyCLI cli = new SendSafelyCLI(consolePromptHelper,
      new ErrorReporter(new PrintStream(errors)), credentials.toFile());
    cli.setSendSafelyAPI(sendSafely);
    cli.setAuthenticatedCredentials("api-key", "api-secret");
    cli.setCheckFile(true);

    Privatekey key = new Privatekey();
    key.setPublicKeyId("public-key-id");
    key.setArmoredKey("private-key-material");
    when(sendSafely.generateKeyPair("laptop")).thenReturn(key);

    PrintStream originalOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(output));
      assertEquals(0, cli.keygen("laptop"));
    } finally {
      System.setOut(originalOut);
    }

    String stored = new String(Files.readAllBytes(credentials), StandardCharsets.UTF_8);
    assertTrue(stored.contains("\"apiKey\":\"api-key\""));
    assertTrue(stored.contains("\"apiKeySecret\":\"api-secret\""));
    assertTrue(stored.contains("\"publicKeyId\":\"public-key-id\""));
    assertTrue(stored.contains("\"armoredKey\":\"private-key-material\""));
    assertTrue(stored.contains("\"preserved\":\"value\""));
    assertEquals("public-key-id", cli.getPublicKeyId());
    assertEquals("private-key-material", cli.getArmoredKey());
    assertTrue(output.toString().contains("public-key-id"));
    assertFalse(output.toString().contains("private-key-material"));
    assertFalse(errors.toString().contains("private-key-material"));

    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(credentials);
      assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(permissions));
    } catch (UnsupportedOperationException ignored) {
      // POSIX permissions are not available on every supported platform.
    }
  }

  @Test
  void keygenRejectsDisabledCredentialStorageBeforeGeneratingKey() throws Exception {
    Path credentials = Files.createTempDirectory("sendsafely-keygen")
      .resolve("credentials.json");
    SendSafelyCLI cli = new SendSafelyCLI(consolePromptHelper,
      new ErrorReporter(System.err), credentials.toFile());
    cli.setSendSafelyAPI(sendSafely);
    cli.setAuthenticatedCredentials("api-key", "api-secret");
    cli.setCheckFile(false);

    IOException error = assertThrows(IOException.class, () -> cli.keygen("laptop"));

    assertTrue(error.getMessage().contains("DISABLE_CREDS_FILE"));
    verify(sendSafely, never()).generateKeyPair(anyString());
    assertFalse(Files.exists(credentials));
  }

  @Test
  void keygenDoesNotReplaceMalformedCredentialFile() throws Exception {
    Path credentials = Files.createTempFile("sendsafely-keygen", ".json");
    Files.write(credentials, "not-json".getBytes(StandardCharsets.UTF_8));
    SendSafelyCLI cli = new SendSafelyCLI(consolePromptHelper,
      new ErrorReporter(System.err), credentials.toFile());
    cli.setSendSafelyAPI(sendSafely);
    cli.setAuthenticatedCredentials("api-key", "api-secret");
    cli.setCheckFile(true);

    Privatekey key = new Privatekey();
    key.setPublicKeyId("public-key-id");
    key.setArmoredKey("private-key-material");
    when(sendSafely.generateKeyPair("laptop")).thenReturn(key);

    assertThrows(IOException.class, () -> cli.keygen("laptop"));
    assertEquals("not-json",
      new String(Files.readAllBytes(credentials), StandardCharsets.UTF_8));
  }
}
