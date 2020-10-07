package com.sendsafely.cliapp;

/**
 * An enumeration of all the possible ActionTypes a user can take in the CLI
 */
public enum ActionType {
  LOGIN,
  LOGOUT,
  CREATE_PACKAGE,
  UPLOAD_FILE,
  ADD_RECIPIENTS,
  ADD_YOURSELF_AS_RECIPIENT,
  FINALIZE,
  UNDO,
  QUIT
}
