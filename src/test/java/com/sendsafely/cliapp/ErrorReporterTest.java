package com.sendsafely.cliapp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class ErrorReporterTest {
    @Test
    void reportsCauseChain() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ErrorReporter reporter = new ErrorReporter(new PrintStream(output));

        reporter.report("Authentication failed",
            new IllegalStateException("request failed", new java.net.ConnectException("refused")));

        String rendered = output.toString();
        assertTrue(rendered.contains("Authentication failed."));
        assertTrue(rendered.contains("IllegalStateException: request failed"));
        assertTrue(rendered.contains("Caused by ConnectException: refused"));
    }

    @Test
    void redactsRegisteredSecretsAndSensitiveAssignments() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ErrorReporter reporter = new ErrorReporter(new PrintStream(output));
        reporter.addSecret("secret-value");

        reporter.report("Request failed", new RuntimeException(
            "apiKeySecret=secret-value ss-request-signature: signature-value"));

        String rendered = output.toString();
        assertTrue(rendered.contains("[REDACTED]"));
        assertFalse(rendered.contains("secret-value"));
        assertFalse(rendered.contains("signature-value"));
    }

    @Test
    void debugIncludesSanitizedStackTrace() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ErrorReporter reporter = new ErrorReporter(new PrintStream(output));
        reporter.addSecret("very-secret");
        reporter.setDebug(true);

        reporter.report("Request failed", new RuntimeException("very-secret"));

        String rendered = output.toString();
        assertTrue(rendered.contains("at com.sendsafely.cliapp.ErrorReporterTest"));
        assertFalse(rendered.contains("very-secret"));
    }

    @Test
    void redactsPackageKeycodesAndPrivateKeys() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ErrorReporter reporter = new ErrorReporter(new PrintStream(output));

        reporter.report("Request failed", new RuntimeException(
            "https://example.test/package#keyCode=abc123\n"
                + "-----BEGIN PRIVATE KEY-----\nmaterial\n-----END PRIVATE KEY-----"));

        String rendered = output.toString();
        assertFalse(rendered.contains("abc123"));
        assertFalse(rendered.contains("material"));
    }
}
