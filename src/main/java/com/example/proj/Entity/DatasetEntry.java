package com.example.proj.Entity;

import java.time.LocalDateTime;

public class DatasetEntry {
    private LocalDateTime creationDateTime;
    private double runningDuration;

    public DatasetEntry(LocalDateTime creationDateTime, double runningDuration) {
        this.creationDateTime = creationDateTime;
        this.runningDuration = runningDuration;
    }

    public LocalDateTime getCreationDateTime() {
        return creationDateTime;
    }

    public double getRunningDuration() {
        return runningDuration;
    }
}
