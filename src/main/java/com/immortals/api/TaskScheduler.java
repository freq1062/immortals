package com.immortals.api;

import java.util.ArrayList;
import java.util.List;

public class TaskScheduler {
    private final List<DelayedTask> tasks = new ArrayList<>();

    public void schedule(Runnable action, int delayTicks) {
        long executeInTicks = delayTicks;
        tasks.add(new DelayedTask(executeInTicks, action));
    }

    public void tick(long currentTick) {
        List<DelayedTask> toRemove = new ArrayList<>();
        List<DelayedTask> tasksCopy = new ArrayList<>(tasks);
        for (DelayedTask dt : tasksCopy) {
            dt.executeInTicks--;
            if (dt.shouldRun(currentTick)) {
                try {
                    dt.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                toRemove.add(dt);
            }
        }
        tasks.removeAll(toRemove);
    }
}
