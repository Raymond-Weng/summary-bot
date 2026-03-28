package dev.raymondweng;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

public class RemoveHistory implements Runnable {
    public final String channelID;

    public RemoveHistory(String channelID) {
        this.channelID = channelID;
    }

    @Override
    public void run() {
        ReentrantLock lock = Summarizer.channelLocks.computeIfAbsent(channelID, k -> new ReentrantLock());
        lock.lock();
        try {
            File file = new File("./db/" + channelID + ".db");
            if (file.exists()) {
                file.delete();
            }
        } finally {
            lock.unlock();
            Summarizer.channelLocks.remove(channelID);
        }
    }
}
