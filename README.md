# SendSafely Java CLI

## Getting Started

#### Requirements:
* Java 1.8
* Maven
* An interactive console/terminal (some IDE consoles do not handle interactivity well)

#### Building executable Jar file:
`mvn install`

#### Running the CLI program from the executable Jar file:
From the root of the project: `java -jar target/sendsafely-java-1.0-SNAPSHOT-jar-with-dependencies.jar`

#### Running tests:
From command line: `mvn test`

It is also possible to run tests from most IDE's directly from the file.

---

## CLI Usage

#### Possible interactions:

* `LOGIN` Log in with valid SendSafely api key and api secret. Necessary for further operations in the CLI app.
* `LOGOUT` Log out once the user has been logged in.
* `CREATE_PACKAGE` Create a new package for adding files and recipients to.
* `UPLOAD_FILE` Upload a file to the package currently being worked with.
* `ADD_RECIPIENTS` Add recipients to the package currently being worked with.
* `ADD_YOURSELF_AS_RECIPIENT` Add the current user as recipient to the package currently being worked with.
* `FINALIZE` Finalize the package and get a secure link for the package.
* `UNDO` Undo the most previously enacted action.
* `QUIT` Quit the CLI application. Don't go!!!

## Key generation

Run `ss --keygen "description"` to generate the RSA key pair used to decrypt package keycodes.
The CLI stores the public key ID and armored private key alongside the API credentials in
`~/.config/.ss-creds.json`; it no longer prints private-key material. The credentials file contains
sensitive secrets and is restricted to the current user where the platform supports file
permissions. Key generation is unavailable when `DISABLE_CREDS_FILE=true` because the generated
private key could not be persisted.

## Error reporting

Runtime failures are written to stderr with their sanitized underlying exception and cause chain.
Use `--debug` to include a sanitized stack trace. `--quiet` suppresses routine status output, but
never suppresses errors. API credentials, request signatures, package keycodes, and private key
material are redacted from both normal and debug error output.
