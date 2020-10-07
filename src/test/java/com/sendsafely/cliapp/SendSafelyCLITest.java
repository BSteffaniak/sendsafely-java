package com.sendsafely.cliapp;

import com.sendsafely.SendSafely;
import com.sendsafely.exceptions.InvalidCredentialsException;
import com.sendsafely.exceptions.UserInformationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SendSafelyCLITest {
  SendSafelyCLI sendSafelyCLI;

  @Mock
  ConsolePromptHelper consolePromptHelper;

  @BeforeEach
  void setup() {
    this.sendSafelyCLI = Mockito.spy(new SendSafelyCLI(consolePromptHelper));
  }

  @Test
  @DisplayName("should not allow logging in with empty credentials")
  void shouldNotAllowLoggingInWithEmptyCredentials() throws IOException {
    when(consolePromptHelper.promptForPrivateString(any())).thenReturn("");

    assertFalse(sendSafelyCLI.attemptLogin());
  }

  @Test
  @DisplayName("should not allow logging in with invalid credentials")
  void shouldNotAllowLoggingInWithInvalidCredentials() throws IOException, InvalidCredentialsException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    SendSafely sendSafely = mock(SendSafely.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.verifyCredentials()).thenThrow(new InvalidCredentialsException());

    assertFalse(sendSafelyCLI.attemptLogin());

    verify(sendSafelyCLI, times(1)).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");
  }

  @Test
  @DisplayName("should not allow logging in with user information failure")
  void shouldNotAllowLoggingInWithUserInformationFailure() throws IOException, UserInformationFailedException {
    when(consolePromptHelper.promptForPrivateString("Enter api key:")).thenReturn("smelly key");
    when(consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):")).thenReturn("stinky secret");

    SendSafely sendSafely = mock(SendSafely.class);

    doReturn(sendSafely).when(sendSafelyCLI).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");

    when(sendSafely.getUserInformation()).thenThrow(new UserInformationFailedException());

    assertFalse(sendSafelyCLI.attemptLogin());

    verify(sendSafelyCLI, times(1)).getSendSafelyAPIForKeyAndSecret("smelly key", "stinky secret");
  }
}
