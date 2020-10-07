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