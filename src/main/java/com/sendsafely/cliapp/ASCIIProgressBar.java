package com.sendsafely.cliapp;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class ASCIIProgressBar extends ProgressBar {
  public ASCIIProgressBar(String task, long initialMax) {
    super(task, initialMax, 200, System.out, ProgressBarStyle.ASCII, "", 1L, false, null, ChronoUnit.SECONDS, 0L, Duration.ZERO);
  }
}
