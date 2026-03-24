package dev.raymondweng;

public class Summarizer implements Runnable {
    public final String channelID;

    public Summarizer(String channelID) {
        this.channelID = channelID;
    }

    @Override
    public void run() {
        //TODO decide whether to summarize or not
        //TODO summarize
    }
}
