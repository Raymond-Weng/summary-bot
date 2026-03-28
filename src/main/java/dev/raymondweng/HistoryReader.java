package dev.raymondweng;

public class HistoryReader implements Runnable {
    public final String channelID;

    public HistoryReader(String channelID) {
        this.channelID = channelID;
    }

    @Override
    public void run() {
        //TODO read history
    }
}
