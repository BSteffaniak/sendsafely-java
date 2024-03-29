package com.sendsafely.cliapp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openpgp.PGPException;
import org.fusesource.jansi.AnsiConsole;
import org.zeroturnaround.zip.ZipUtil;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.sendsafely.Package;
import com.sendsafely.Privatekey;
import com.sendsafely.Recipient;
import com.sendsafely.SendSafely;
import com.sendsafely.dto.PackageURL;
import com.sendsafely.dto.UserInformation;
import com.sendsafely.exceptions.ApproverRequiredException;
import com.sendsafely.exceptions.CreatePackageFailedException;
import com.sendsafely.exceptions.DeletePackageException;
import com.sendsafely.exceptions.DownloadFileException;
import com.sendsafely.exceptions.FileOperationFailedException;
import com.sendsafely.exceptions.FinalizePackageFailedException;
import com.sendsafely.exceptions.GetKeycodeFailedException;
import com.sendsafely.exceptions.GetPackagesException;
import com.sendsafely.exceptions.InvalidCredentialsException;
import com.sendsafely.exceptions.LimitExceededException;
import com.sendsafely.exceptions.MessageException;
import com.sendsafely.exceptions.PackageInformationFailedException;
import com.sendsafely.exceptions.PasswordRequiredException;
import com.sendsafely.exceptions.PublicKeysFailedException;
import com.sendsafely.exceptions.RecipientFailedException;
import com.sendsafely.exceptions.UploadFileException;
import com.sendsafely.exceptions.UserInformationFailedException;
import com.sendsafely.file.DefaultFileManager;
import com.sendsafely.file.FileManager;

import jline.TerminalFactory;
import me.tongfei.progressbar.ProgressBar;

/**
 * A small CLI application for interfacing with the SendSafely API.
 */
@Command(
    name = "ss",
    mixinStandardHelpOptions = true,
    version = "ss 1.0",
    description = "SendSafely Java CLI Client")
class SendSafelyCLI implements Callable<Integer> {
    private SendSafely sendSafelyAPI;
    private ConsolePromptHelper consolePromptHelper;
    private Package currentPackage;
    private UserInformation userInformation;
    private Set<String> addedRecipients;
    private boolean checkFile;
    private String publicKeyId;
    private String armoredKey;

    private Stack<Runnable> undoActions;

    private static final File credsHomeDirectory =
        new File(System.getProperty("user.home"), ".config");
    private static final File credsFile = new File(credsHomeDirectory, ".ss-creds.json");

    @Option(names = {"-mf", "--message-file"}, description = "Package secure message from a file.")
    private File messageFile;

    @Option(names = {"-m", "--message"}, description = "Package secure message.")
    private String message;

    @Option(names = {"-q", "--quiet"}, description = "Only print out necessary output.")
    private boolean quiet;

    @Option(names = {"-l", "--list"}, description = "List package history.")
    private boolean list;

    @Option(names = {"--pop"}, description = "Download the most recent package's files.")
    private boolean pop;

    @Option(names = {"-d", "--download"}, description = "Download package files.")
    private String downloadPackageId;

    @Option(names = {"--archive"}, description = "Archive a package.")
    private String archivePackageId;

    @Option(names = {"-u", "--unzip"}, description = "Unzip zip file types.")
    private boolean unzip;

    @Option(names = {"--read-last-message"}, description = "Read the last package's secure message")
    private boolean readLastMessage;

    @Option(names = {"--read-message"}, description = "Read a package's secure message")
    private String readMessagePackageId;

    @Option(names = {"--keygen"}, description = "Generate a new RSA Key pair to encrypt keycodes")
    private String keygen;

    @Option(names = {"-r", "--recipient"}, description = "Package recipient.")
    private String[] recipients = new String[0];

    @Parameters(arity = "0..*", description = "File to upload.")
    private File[] files = new File[0];

    /**
     * Start the CLI application. Exit code 1 for any uncaught CLIExceptions or IOExceptions. Exit
     * code 0 for all successful outcomes.
     */
    public static void main(String... args) {
        SendSafelyCLI cli = new SendSafelyCLI(new ConsolePromptHelper());

        try {
            cli.checkFile = !Objects.equals(System.getenv("DISABLE_CREDS_FILE"), "true");

            if (args.length > 0) {
                System.exit(new CommandLine(cli).execute(args));
            }

            cli.start();
        } catch (CLIException | IOException exception) {
            System.err.println(exception.getMessage());

            System.exit(1);
        }

        System.exit(0);
    }

    public Integer call() throws Exception {
        if (!attemptLogin())
            return 1;

        if (list)
            return listPackages();

        if (readMessagePackageId != null)
            return readMessage(parsePackageId(readMessagePackageId));

        if (readLastMessage)
            return readMessage(getLastPackage().getPackageId());

        if (pop)
            return pop();

        if (downloadPackageId != null)
            return downloadPackage(parsePackageId(downloadPackageId));

        if (archivePackageId != null)
            return archivePackage(parsePackageId(archivePackageId));

        if (keygen != null)
            return keygen(keygen);

        if (!createPackage())
            return 1;

        for (File file : files) {
            if (!uploadFile(file, true))
                return 1;
        }

        if (recipients.length > 0) {
            for (String recipient : recipients) {
                if (!addRecipients(recipient))
                    return 1;
            }
        } else {
            if (!addRecipients(userInformation.getEmail()))
                return 1;
        }

        if (messageFile != null) {
            if (!uploadMessage(messageFile))
                return 1;
        } else if (message != null) {
            if (!uploadMessage(message))
                return 1;
        }

        if (!finalizePackage())
            return 1;

        return 0;
    }

    private String parsePackageId(String value)
        throws GetPackagesException, DownloadFileException, PasswordRequiredException {
        Package[] packages = getPackages();

        if (value.startsWith("@")) {
            if (packages.length == 0)
                throw new RuntimeException(
                    "No packages to reference by index. Package list is empty.");

            int index = Integer.parseInt(value.substring(1));

            if (index < 0 || index >= packages.length) {
                throw new RuntimeException("Index " + index
                    + " is out of bounds. Valid indexes range from 0 to " + packages.length + ".");
            }
        }

        Package[] packageMatches = Arrays.stream(packages)
            .filter(p -> p.getPackageId().toLowerCase().contains(value))
            .toArray(Package[]::new);

        if (packageMatches.length == 0) {
            throw new RuntimeException("No packages match value '" + value + "'");
        }

        if (packageMatches.length == 1)
            return packageMatches[0].getPackageId();

        Package[] strictPackageMatches = Arrays.stream(packageMatches)
            .filter(p -> p.getPackageId().toLowerCase().startsWith(value))
            .toArray(Package[]::new);

        if (strictPackageMatches.length == 1)
            return strictPackageMatches[0].getPackageId();

        String packageIds = Arrays.stream(strictPackageMatches)
            .map(p -> p.getPackageId())
            .map(p -> "'" + p + "'")
            .collect(Collectors.joining(", "));

        throw new RuntimeException(
            "Ambiguous package id value '" + value + "'. Matches " + packageIds);
    }

    private Integer keygen(String keygen)
        throws NoSuchAlgorithmException, PublicKeysFailedException, PGPException, IOException {
        Privatekey key = sendSafelyAPI.generateKeyPair(keygen);

        System.out.println(key.getPublicKeyId());
        System.out.println(key.getArmoredKey());

        return 0;
    }

    private String getMessage(String packageId)
        throws GetKeycodeFailedException, MessageException, PackageInformationFailedException {
        Package pkg = sendSafelyAPI.getPackageInformation(packageId);

        if (!pkg.getPackageContainsMessage()) {
            throw new RuntimeException("Package does not contain a secure message.");
        }

        String keycode = getPackageKeycode(packageId);

        return sendSafelyAPI.getPackageMessage(sendSafelyAPI.getPackageLink(packageId, keycode));
    }

    private Integer readMessage(String packageId)
        throws GetKeycodeFailedException, MessageException, PackageInformationFailedException,
        DeletePackageException {
        System.out.print(getMessage(packageId));

        if (pop) {
            return archivePackage(packageId);
        }

        return 0;
    }

    private Integer archivePackage(String packageId) throws DeletePackageException {
        sendSafelyAPI.deletePackage(packageId);

        return 0;
    }

    private String getPackageKeycode(String packageId) throws GetKeycodeFailedException {
        if (publicKeyId == null)
            throw new RuntimeException(
                "RSA Key pair required to get the keycode for packages. Use `ss --keygen \"description\"` to create a key pair.");

        Privatekey key = new Privatekey();
        key.setPublicKeyId(publicKeyId);
        key.setArmoredKey(armoredKey);
        return sendSafelyAPI.getKeycode(packageId, key);
    }

    private Integer downloadPackage(String packageId)
        throws PackageInformationFailedException, DownloadFileException, PasswordRequiredException,
        GetKeycodeFailedException, IOException, MessageException {
        Package p = sendSafelyAPI.getPackageInformation(packageId);

        String keycode = getPackageKeycode(packageId);

        if (p.getPackageContainsMessage()) {
            String message = getMessage(packageId);

            File downloadedFile = new File("secure-message-" + packageId + ".txt");

            try (FileWriter writer = new FileWriter(downloadedFile)) {
                writer.write(message);
            }

            log("Downloaded secure message: " + downloadedFile.getCanonicalPath());
        }

        for (com.sendsafely.File f : p.getFiles()) {
            File downloadedFile = null;

            try (ProgressBar progressBar = new ASCIIProgressBar("File download", 100)) {
                FileProgressBar fileProgressBar = new FileProgressBar(progressBar);
                File file = sendSafelyAPI.downloadFile(p.getPackageId(), f.getFileId(), keycode,
                    fileProgressBar);

                if (unzip && f.getFileName().endsWith(".zip")) {
                    downloadedFile = new File(
                        f.getFileName().substring(0, f.getFileName().length() - ".zip".length()));

                    ZipUtil.unpack(file, downloadedFile);
                } else {
                    downloadedFile = new File(f.getFileName());

                    Files.move(file.toPath(), downloadedFile.toPath());
                }
            }

            log("Downloaded file: " + downloadedFile.getCanonicalPath());
        }

        return 0;
    }

    private Package getLastPackage()
        throws GetPackagesException, DownloadFileException, PasswordRequiredException {
        Package[] packages = getPackages();

        if (packages.length == 0) {
            throw new RuntimeException("No active packages");
        }

        return packages[0];
    }

    private Integer pop() throws PackageInformationFailedException, DownloadFileException,
        PasswordRequiredException, GetKeycodeFailedException, IOException, GetPackagesException,
        DeletePackageException, MessageException {
        String packageId = getLastPackage().getPackageId();
        int returnValue = 0;

        if ((returnValue = downloadPackage(packageId)) != 0) {
            return returnValue;
        }

        return archivePackage(packageId);
    }

    private Integer listPackages()
        throws GetPackagesException, DownloadFileException, PasswordRequiredException {
        Package[] packages = getPackages();

        if (packages.length == 0) {
            log("No active packages");
            return 0;
        }

        for (Package p : packages) {
            String pattern = "MM/dd/yyyy HH:mm:ss";
            DateFormat df = new SimpleDateFormat(pattern);
            String date = df.format(p.getPackageTimestamp());
            String message = p.getPackageId() + " - " + date + " - ";

            if (!p.getFiles().isEmpty()) {
                int count = p.getFiles().size();
                String fileNames = p.getFiles().stream().map(f -> f.getFileName())
                    .collect(Collectors.joining(", "));
                fileNames =
                    fileNames.length() > 100 ? fileNames.substring(0, 100) : fileNames;
                message += count + " file" + (count == 1 ? "" : "s") + " - " + fileNames;
            } else if (p.getPackageContainsMessage()) {
                message += "secure message";
            } else {
                message += "empty";
            }

            message += " - " + p.getState().toString().substring("PACKAGE_STATE_".length())
                .toLowerCase().replace("_", " ");

            System.out.println(message);
        }

        return 0;
    }

    private Package[] getPackages()
        throws GetPackagesException, DownloadFileException, PasswordRequiredException {
        return sendSafelyAPI.getActivePackages()
            .stream()
            .map((p) -> {
                try {
                    return sendSafelyAPI.getPackageInformation(p.getPackageId());
                } catch (PackageInformationFailedException e) {
                    e.printStackTrace();
                }

                return null;
            })
            .filter(p -> p != null)
            .toArray(Package[]::new);
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
     * Start the CLI program. This starts the user off with prompting login credentials, then moves
     * into the main menu where a user can create a package, upload a file, add recipients to the
     * current package, undo the previous action, logout, or quit the program.
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
                    optionsBuilder.build());

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
                    .build());

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
        String apiKey = null;
        String apiSecret = null;

        if (checkFile && credsFile.exists()) {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode node = mapper.readTree(credsFile);
            apiKey = node.get("apiKey").asText();
            apiSecret = node.get("apiKeySecret").asText();

            if (node.findValue("publicKeyId") != null) {
                publicKeyId = node.get("publicKeyId").asText(null);
                armoredKey = node.get("armoredKey").asText(null);
            }
        } else {
            apiKey = consolePromptHelper.promptForPrivateString("Enter api key:");
            apiSecret = consolePromptHelper.promptForPrivateString("Enter api secret (shhhhhh):");
        }

        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            System.err.println("Invalid credentials");

            return false;
        }

        sendSafelyAPI = getSendSafelyAPIForKeyAndSecret(apiKey, apiSecret);

        try {
            sendSafelyAPI.verifyCredentials();
            userInformation = sendSafelyAPI.getUserInformation();

            log("Successfully logged in! Welcome, " + userInformation.getFirstName()
                + "!!! Wooooo!");

            undoActions.push(() -> {
                logoutUser();

                log("Logged out!!");

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
            System.err.println(
                "No actions available to be undone, but I'm sure you knew that already. You're doing great!");
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
    public boolean createPackage() {
        try {
            currentPackage = sendSafelyAPI.createPackage();

            log("Successfully created package");

            undoActions.push(() -> {
                try {
                    deleteCurrentPackage();

                    log("Successfully deleted package");
                } catch (DeletePackageException e) {
                    System.err.println("Failed to delete packages: " + e.getError());
                }
            });

            return true;
        } catch (CreatePackageFailedException | LimitExceededException e) {
            System.err.println("Failed to create package: " + e);

            return false;
        }
    }

    /**
     * Upload a custom message to the current package
     */
    public boolean uploadMessage(String message) {
        try {
            sendSafelyAPI.encryptAndUploadMessage(currentPackage.getPackageId(),
                currentPackage.getKeyCode(), message);

            log("Successfully uploaded message");

            return true;
        } catch (MessageException e) {
            System.err.println("Failed to upload message: " + e);

            return false;
        }
    }

    /**
     * Upload a custom message to the current package
     */
    public boolean uploadMessage(File mesageFile) {
        try {
            return uploadMessage(FileUtils.readFileToString(messageFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to read file contents: " + e);

            return false;
        }
    }

    /**
     * Delete the given file from the current package.
     *
     * @param file The java.io.File version of the file to delete.
     * @param addedFile The com.sendSafely.File version of the file to delete.
     */
    public void deleteFile(File file, com.sendsafely.File addedFile)
        throws FileOperationFailedException, IOException {
        log("Deleting file '" + file.getCanonicalPath() + "'");

        sendSafelyAPI.deleteFile(currentPackage.getPackageId(), currentPackage.getRootDirectoryId(),
            addedFile.getFileId());
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
    public boolean uploadFile() throws IOException {
        try {
            File file = consolePromptHelper.promptForFile("Enter the file location");

            return uploadFile(file, false);
        } catch (FilePromptException e) {
            System.err.println(e.getMessage());

            if (consolePromptHelper.promptForConfirmation("Try a new file?")) {
                return uploadFile();
            }

            return false;
        }
    }

    /**
     * Enter a promp sequence for uploading a file to the current package.
     */
    public boolean uploadFile(File file, boolean autoZipDirectory) throws IOException {
        try {
            File tempDir = null;

            if (file.isDirectory()) {
                if (!autoZipDirectory && !consolePromptHelper.promptForConfirmation(
                    "The given file is a directory and cannot be uploaded as is. Zip it?")) {
                    return false;
                }

                String name = file.getName();

                try {
                    name = file.getCanonicalFile().getName();
                } catch (IOException e) {
                    System.err.println("Failed to get canonical file name");
                }

                tempDir = Files.createTempDirectory("ss-" + name).toFile();

                File tempFile = new File(tempDir, name + ".zip");

                if (tempFile.exists()) {
                    throw new RuntimeException(
                        "Zip file already exists at location " + tempFile.getAbsolutePath());
                }

                log("Creating zip file at " + tempFile.getAbsolutePath());

                ZipUtil.pack(file, tempFile);

                file = tempFile;
            }

            FileManager fileManager = createFileManager(file);

            final File uploadedFile = file;

            // Using try-with-resources to ensure the ProgressBar stream gets closed out after
            // successful
            // and failed file uploads
            try (ProgressBar progressBar = new ASCIIProgressBar("File Upload", 100)) {
                FileProgressBar fileProgressBar = new FileProgressBar(progressBar);

                try {
                    com.sendsafely.File addedFile =
                        sendSafelyAPI.encryptAndUploadFile(currentPackage.getPackageId(),
                            currentPackage.getKeyCode(), fileManager, fileProgressBar);

                    undoActions.push(() -> {
                        try {
                            deleteFile(uploadedFile, addedFile);

                            log("Deleted file successfully");
                        } catch (FileOperationFailedException | IOException e) {
                            System.err
                                .println("Failed to delete file from package: " + e.getMessage());
                        }
                    });

                    progressBar.stepTo(100);
                } catch (LimitExceededException | UploadFileException e) {
                    System.err.println("Failed to upload file:" + e.getMessage());
                }
            }

            log("File successfully uploaded");

            if (tempDir != null) {
                FileUtils.deleteDirectory(tempDir);

                log("Temporary zip file deleted");
            }

            return true;
        } catch (FilePromptException e) {
            System.err.println(e.getMessage());

            if (consolePromptHelper.promptForConfirmation("Try a new file?")) {
                return uploadFile();
            }

            return false;
        }
    }

    /**
     * Quit the app with exit code 0.
     */
    public void quit() {
        log("Bye ♥");

        System.exit(0);
    }

    /**
     * Finalize the current package and print out a secure link to that package.
     */
    public boolean finalizePackage() {
        try {
            PackageURL packageURL = sendSafelyAPI.finalizePackage(currentPackage.getPackageId(),
                currentPackage.getKeyCode());

            System.out.println("Secure link: " + packageURL.getSecureLink());

            undoActions.clear();
            undoActions.push(() -> {
                System.err.println("Cannot unfinalize a package (that I'm aware of)");
            });

            clearCurrentPackage();

            return true;
        } catch (LimitExceededException | FinalizePackageFailedException
            | ApproverRequiredException e) {
            System.err.println("Failed to finalize package: " + e.getMessage());

            return false;
        }
    }

    /**
     * Add a recipient to the current package.
     */
    public void addRecipients() throws IOException {
        String recipientEmail =
            consolePromptHelper.promptForString("Enter recipient email:").trim();

        addRecipients(recipientEmail);
    }

    /**
     * Add a predetermined recipient to the current package.
     *
     * @param recipientEmail The recipient to add.
     */
    public boolean addRecipients(String recipientEmail) {
        if (recipientEmail.isEmpty()) {
            System.err.println("Recipient cannot be empty");
            return false;
        }
        if (addedRecipients.contains(recipientEmail)) {
            System.err.println("Recipient '" + recipientEmail + "' already added");
            return false;
        }

        try {
            Recipient recipient =
                sendSafelyAPI.addRecipient(currentPackage.getPackageId(), recipientEmail);

            addedRecipients.add(recipientEmail);

            log("Successfully added recipient '" + recipientEmail + "'");

            undoActions.push(() -> {
                log("Removing recipient '" + recipientEmail + "'");

                try {
                    sendSafelyAPI.removeRecipient(currentPackage.getPackageId(),
                        recipient.getRecipientId());

                    addedRecipients.remove(recipientEmail);

                    log("Recipient removed successfully");
                } catch (RecipientFailedException e) {
                    System.err.println("Failed to remove recipient: " + e.getMessage());
                }
            });

            return true;
        } catch (LimitExceededException | RecipientFailedException e) {
            System.err.println("Failed to add recipient: " + e.getMessage());

            return false;
        }
    }

    private void log(String message) {
        if (quiet)
            return;

        System.out.println(message);
    }
}
