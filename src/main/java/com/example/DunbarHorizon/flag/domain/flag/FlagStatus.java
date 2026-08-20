package com.example.DunbarHorizon.flag.domain.flag;

public enum FlagStatus {
    RECRUITING,
    WAITING,
    IN_ACTIVITY,
    ENDED;

    public boolean isRecruiting() { return this == RECRUITING; }
    public boolean isEnded() { return this == ENDED; }

    public boolean isBeforeActivity() {
        return this == RECRUITING || this == WAITING;
    }
}