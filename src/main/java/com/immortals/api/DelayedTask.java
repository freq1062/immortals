package com.immortals.api;

public class DelayedTask {
    public long executeInTicks;
    private final Runnable action;

    public DelayedTask(long executeInTicks, Runnable action) {
        this.executeInTicks = executeInTicks;
        this.action = action;
    }

    public boolean shouldRun(long currentTick) {
        return executeInTicks <= 0;
    }

    public void run() {
        action.run();
    }
}