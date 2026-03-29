package dev.raymondweng;

import net.dv8tion.jda.api.entities.Message;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class HistoryReader implements Runnable {
    public final String channelID;

    public HistoryReader(String channelID) {
        this.channelID = channelID;
    }

    @Override
    public void run() {
        ReentrantLock reentrantLock = Summarizer.channelLocks.computeIfAbsent(channelID, k -> new ReentrantLock());
        reentrantLock.lock();
        int cnt = 0;
        try {
            List<Message> messages = Main.jda
                    .getTextChannelById(channelID)
                    .getHistory()
                    .retrievePast(100)
                    .complete();
            for(var message:messages){
                StringBuilder stringBuilder = new StringBuilder();
                for (Message.Attachment attachment : message.getAttachments()) {
                    stringBuilder.append(attachment.getFileName()).append(" ");
                }
                Main.messageController.message(
                        message.getChannelId(),
                        message.getId(),
                        message.getAuthor().getGlobalName(),
                        message.getContentDisplay(),
                        !message.getAttachments().isEmpty(),
                        stringBuilder.toString(),
                        message.getReferencedMessage() != null,
                        message.getReferencedMessage() != null ? message.getReferencedMessage().getAuthor().getGlobalName() : "",
                        message.getReferencedMessage() != null ? message.getReferencedMessage().getContentDisplay() : ""
                );
                cnt++;
            }
        } catch (SQLException e) {
            Logger.log(Logger.EXCEPTION_CHANNEL, "SQLException in history reader while reading message：" + Main.jda.getTextChannelById(channelID).getGuild().getName() + " - " + Main.jda.getTextChannelById(channelID).getName() + " (" + channelID + ")" + e.getMessage());
        } finally {
            reentrantLock.unlock();
        }
        if(cnt >= 10){
            Thread.startVirtualThread(new Summarizer(channelID));
        }
    }
}
