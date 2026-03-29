package dev.raymondweng;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class Logger {
    public static final int MONITOR_CHANNEL = 1;
    public static final int SUMMARY_CHANNEL = 2;
    public static final int EXCEPTION_CHANNEL = 3;

    public static void log(int channel, String Message) {
        String channelID = "";
        switch (channel) {
            case MONITOR_CHANNEL:
                channelID = Main.dotenv.get("DISCORD_MONITOR_CHANNEL_ID");
                break;
            case SUMMARY_CHANNEL:
                channelID = Main.dotenv.get("DISCORD_SUMMARY_CHANNEL_ID");
                break;
            case EXCEPTION_CHANNEL:
                channelID = Main.dotenv.get("DISCORD_EXCEPTION_CHANNEL_ID");
                break;
        }
        if(channelID == null) return;
        if (!channelID.equals("-1")) {
            TextChannel c = Main.jda.getTextChannelById(channelID);
            if(c == null) return;
            c.sendMessage(Message).queue();
        }
    }
}
