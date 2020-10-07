package com.sendsafely.cliapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SendSafelyCLITest {
  SendSafelyCLI sendSafelyCLI;

  @Mock
  ConsolePromptHelper consolePromptHelper;

  @BeforeEach
  void setup() {
    sendSafelyCLI = new SendSafelyCLI(consolePromptHelper);
  }

  @Test
  @DisplayName("should not allow logging in with empty credentials")
  void shouldNotAllowLoggingInWithEmptyCredentials() throws IOException {
    when(consolePromptHelper.promptForPrivateString(any())).thenReturn("");

    assertFalse(sendSafelyCLI.attemptLogin());
  }
}
