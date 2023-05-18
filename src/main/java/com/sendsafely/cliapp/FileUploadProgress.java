package com.sendsafely.cliapp;

import com.sendsafely.ProgressInterface;
import me.tongfei.progressbar.ProgressBar;

/**
 * ProgressInterface implementation that uses a me.tongfei.progressbar.ProgressBar to display file
 * upload progress.
 */
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
    public void gotFileId(String s) {}
}
