package com.example.jms;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class MdbControl {

    private final AtomicBoolean paused = new AtomicBoolean(false);

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
        synchronized (paused) {
            paused.notifyAll();
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void waitIfPaused() throws InterruptedException {
        synchronized (paused) {
            while (paused.get()) {
                paused.wait(1000);
            }
        }
    }
}
