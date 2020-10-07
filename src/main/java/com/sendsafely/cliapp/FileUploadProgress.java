package com.sendsafely.cliapp;

import com.sendsafely.ProgressInterface;
import me.tongfei.progressbar.ProgressBar;

public class FileUploadProgress implements ProgressInterface {
  private final ProgressBar progressBar;

  public FileUploadProgress(ProgressBar progressBar) {
    this.progressBar = progressBar;
  }

  @Override
  public void updateProgress(String s, double progress) {
    progressBar.stepTo(Math.round(progress * 100));
  }

  @Override
  public void gotFileId(String s) {
  }
}
