package com.sendsafely.cliapp;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Reports actionable errors while preventing known secrets from reaching the terminal. */
public class ErrorReporter {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
        "(?i)(\\\"?(?:apiKey|apiKeySecret|apiSecret|ss-api-key|ss-request-signature)\\\"?\\s*[:=]\\s*\\\"?)([^\\\"\\s,}]+)");
    private static final Pattern ARMORED_PRIVATE_KEY = Pattern.compile(
        "(?s)-----BEGIN (?:PGP |RSA )?PRIVATE KEY(?: BLOCK)?-----.*?-----END (?:PGP |RSA )?PRIVATE KEY(?: BLOCK)?-----");
    private static final Pattern PACKAGE_KEYCODE = Pattern.compile(
        "(?i)([#?&](?:keycode|key)=)[^&\\s]+");

    private final PrintStream err;
    private final Set<String> secrets = new LinkedHashSet<>();
    private boolean debug;

    public ErrorReporter(PrintStream err) {
        this.err = err;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void addSecret(String secret) {
        if (secret != null && !secret.isEmpty()) {
            secrets.add(secret);
        }
    }

    public void report(String context, Throwable error) {
        err.println(sanitize(context) + (context.endsWith(".") ? "" : "."));

        Set<Throwable> visited = new HashSet<>();
        Set<String> rendered = new HashSet<>();
        Throwable current = error;
        boolean causedBy = false;
        while (current != null && visited.add(current)) {
            String detail = detail(current);
            String line = current.getClass().getSimpleName()
                + (detail.isEmpty() ? "" : ": " + detail);
            if (rendered.add(line)) {
                err.println((causedBy ? "Caused by " : "") + sanitize(line));
            }
            causedBy = true;
            current = current.getCause();
        }

        if (debug && error != null) {
            StringWriter output = new StringWriter();
            error.printStackTrace(new PrintWriter(output));
            err.print(sanitize(output.toString()));
        }
    }

    private String detail(Throwable error) {
        String message = error.getMessage();
        String libraryError = getLibraryError(error);
        if ((message == null || message.trim().isEmpty()) && libraryError != null) {
            message = libraryError;
        } else if (libraryError != null && !libraryError.equals(message)
            && !"unknown".equalsIgnoreCase(libraryError)) {
            message += " (" + libraryError + ")";
        }
        return message == null ? "" : message.trim();
    }

    private String getLibraryError(Throwable error) {
        try {
            Method method = error.getClass().getMethod("getError");
            if (method.getReturnType() == String.class) {
                return (String) method.invoke(error);
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Most exceptions do not expose SendSafely's getError() extension.
        }
        return null;
    }

    String sanitize(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = ARMORED_PRIVATE_KEY.matcher(value).replaceAll(REDACTED);
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = PACKAGE_KEYCODE.matcher(sanitized).replaceAll("$1" + REDACTED);
        for (String secret : secrets) {
            sanitized = sanitized.replace(secret, REDACTED);
        }
        return sanitized;
    }
}
