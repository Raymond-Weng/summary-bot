package dev.raymondweng;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageController implements EventListener {
    List<String> ADMINS = List.of(
            "1066517249906704524" // Raymond Weng
    );

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        try {
            if (genericEvent instanceof MessageReceivedEvent messageReceivedEvent) {
                if (isChannelMonitored(messageReceivedEvent.getChannel().getId())) {
                    StringBuilder stringBuilder = new StringBuilder();
                    for (Message.Attachment attachment : messageReceivedEvent.getMessage().getAttachments()) {
                        stringBuilder.append(attachment.getFileName()).append(" ");
                    }
                    message(messageReceivedEvent.getChannel().getId(),
                            messageReceivedEvent.getMessageId(),
                            messageReceivedEvent.getAuthor().getGlobalName(),
                            messageReceivedEvent.getMessage().getContentDisplay(),
                            stringBuilder.toString(),
                            messageReceivedEvent.getMessage().getReferencedMessage() != null,
                            messageReceivedEvent.getMessage().getReferencedMessage() != null ? messageReceivedEvent.getMessage().getReferencedMessage().getAuthor().getGlobalName() : "",
                            messageReceivedEvent.getMessage().getReferencedMessage() != null ? messageReceivedEvent.getMessage().getReferencedMessage().getContentDisplay() : ""
                    );
                    Thread.startVirtualThread(new Summarizer(messageReceivedEvent.getChannel().getId()));
                }
            }
            if (genericEvent instanceof SlashCommandInteractionEvent slashCommandInteractionEvent) {
                slashCommandInteractionEvent.deferReply().setEphemeral(true).queue();
                switch (slashCommandInteractionEvent.getName()) {
                    case "summary":
                        if (isChannelMonitored(slashCommandInteractionEvent.getChannelId())) {
                            //TODO return result
                            Logger.log(Logger.SUMMARY_CHANNEL, "總結請求：" + slashCommandInteractionEvent.getGuild().getName() + " - " + slashCommandInteractionEvent.getChannel().getName() + " (" + slashCommandInteractionEvent.getChannelId() + ")");
                        } else {
                            replySlashCommand(slashCommandInteractionEvent, "這個頻道沒有被我們紀錄。" +
                                    "為了節約效能，我們並不會紀錄每個頻道，如果需要紀錄，請使用`/monitor`指令" +
                                    "（目前僅開發者可用，如有需要請聯絡[Raymond Weng](https://raymondweng.dev/)）。");
                        }
                        break;
                    case "monitor":
                        //TODO deal with option
                        if (isChannelMonitored(slashCommandInteractionEvent.getChannelId())) {
                            replySlashCommand(slashCommandInteractionEvent, "我們已經在記錄這個頻道的內容了！");
                        } else {
                            if (ADMINS.contains(slashCommandInteractionEvent.getUser().getId())) {
                                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
                                    try (Statement statement = connection.createStatement()) {
                                        statement.execute("PRAGMA busy_timeout=5000");
                                    }
                                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO monitored_channels (channel_id) VALUES (?)")) {
                                        ps.setString(1, slashCommandInteractionEvent.getChannelId());
                                        ps.executeUpdate();
                                    }
                                }
                                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/" + slashCommandInteractionEvent.getChannelId() + ".db")) {
                                    try (Statement statement = connection.createStatement()) {
                                        statement.execute("PRAGMA busy_timeout=5000");
                                    }
                                    connection
                                            .createStatement()
                                            .executeUpdate("CREATE TABLE IF NOT EXISTS messages (" +
                                                    "message_id TEXT NOT NULL," +
                                                    "author_id TEXT NOT NULL," +
                                                    "content TEXT NOT NULL," +
                                                    "has_attachment INTEGER NOT NULL DEFAULT 0," +
                                                    "attachment_desc TEXT," +
                                                    "do_reply INTEGER NOT NULL DEFAULT 0," +
                                                    "reply_to_author TEXT," +
                                                    "reply_to_content TEXT," +
                                                    "processed INTEGER NOT NULL DEFAULT 0" +
                                                    ")");
                                }
                                Logger.log(Logger.MONITOR_CHANNEL, slashCommandInteractionEvent.getGuild().getName() + " - " + slashCommandInteractionEvent.getChannel().getName() + " (" + slashCommandInteractionEvent.getChannelId() + ")");
                                updateMonitoringCnt();
                                replySlashCommand(slashCommandInteractionEvent, "已經成功紀錄這個頻道，請使用`/summary`來取得統整。我們會閱讀一些先前的訊息，這可能需要一點時間。");
                                Thread.startVirtualThread(new HistoryReader(slashCommandInteractionEvent.getChannelId()));
                            } else {
                                replySlashCommand(slashCommandInteractionEvent, "這個指令目前僅開發者可用，如有需要請聯絡[Raymond Weng](https://raymondweng.dev/)。");
                            }
                        }
                        break;
                    case "stop":
                        String channelID = slashCommandInteractionEvent.getChannelId();
                        if (slashCommandInteractionEvent.getOption("channel_id") != null) {
                            channelID = slashCommandInteractionEvent.getOption("channel_id").getAsString();
                            if (slashCommandInteractionEvent.getJDA().getTextChannelById(channelID) == null) {
                                replySlashCommand(slashCommandInteractionEvent, "無效的頻道ID。");
                                return;
                            }
                            if (!slashCommandInteractionEvent.getGuild().getId().equals(slashCommandInteractionEvent.getJDA().getTextChannelById(channelID).getGuild().getId())) {
                                if (!ADMINS.contains(slashCommandInteractionEvent.getUser().getId())) {
                                    replySlashCommand(slashCommandInteractionEvent, "你只能停止同一個伺服器裡的頻道，除非你是開發者。");
                                    return;
                                }
                            }
                        }
                        if (isChannelMonitored(slashCommandInteractionEvent.getChannelId())) {
                            File file = new File("./db/" + channelID + ".db");
                            if (file.exists()) {
                                file.delete();
                            }
                            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
                                try (Statement statement = connection.createStatement()) {
                                    statement.execute("PRAGMA busy_timeout=5000");
                                }
                                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM monitored_channels WHERE channel_id = ?")) {
                                    ps.setString(1, channelID);
                                    ps.executeUpdate();
                                }
                            }
                            Logger.log(Logger.MONITOR_CHANNEL, "停止追蹤：" + Main.jda.getTextChannelById(channelID).getGuild().getName() + " - " + Main.jda.getTextChannelById(channelID).getName() + " (" + channelID + ")");
                            updateMonitoringCnt();
                            replySlashCommand(slashCommandInteractionEvent, "已經停止關注這裡並刪除先前紀錄。");
                        } else {
                            replySlashCommand(slashCommandInteractionEvent, "這裡還沒開始過，可能沒辦法停止");
                        }
                        break;
                    case "list":
                        if (ADMINS.contains(slashCommandInteractionEvent.getUser().getId())) {
                            Map<String, List<String>> map;
                            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
                                try (Statement statement = connection.createStatement()) {
                                    statement.execute("PRAGMA busy_timeout=5000");
                                }
                                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM monitored_channels")) {
                                    try (ResultSet rs = ps.executeQuery()) {
                                        map = new HashMap<>();
                                        while (rs.next()) {
                                            String channelId = rs.getString("channel_id");
                                            Channel channel = Main.jda.getTextChannelById(channelId);
                                            if (channel == null) {
                                                try (Statement statement = connection.createStatement()) {
                                                    statement.executeUpdate("DELETE FROM monitored_channels WHERE channel_id = '" + channelId + "'");
                                                }
                                                continue;
                                            }
                                            Guild guild = Main.jda.getTextChannelById(channelId).getGuild();
                                            if (map.containsKey(guild.getName() + "(" + guild.getId() + ")")) {
                                                map.get(guild.getName() + "(" + guild.getId() + ")").add(channel.getName() + "(" + channel.getId() + ")");
                                                continue;
                                            }
                                            map.put(
                                                    guild.getName() + "(" + guild.getId() + ")",
                                                    List.of(channel.getName() + "(" + channel.getId() + ")")
                                            );
                                        }
                                    }
                                }
                            }
                            StringBuilder msg = new StringBuilder("目前監控中的頻道有：\n");
                            for (String key : map.keySet()) {
                                msg.append(key).append(":\n");
                                boolean first = true;
                                for (String channelName : map.get(key)) {
                                    if (first) {
                                        first = false;
                                    } else {
                                        msg.append(", ");
                                    }
                                    msg.append(channelName);
                                }
                                msg.append("\n\n");
                            }
                            replySlashCommand(slashCommandInteractionEvent, msg.toString());
                        } else {
                            slashCommandInteractionEvent
                                    .getInteraction()
                                    .getHook()
                                    .sendMessage("這個指令目前僅開發者可用，如有需要請聯絡[Raymond Weng](https://raymondweng.dev/)。")
                                    .queue();
                        }
                        break;
                }
            }
        } catch (SQLException e) {
            if (genericEvent instanceof MessageReceivedEvent messageReceivedEvent) {
                Logger.log(Logger.EXCEPTION_CHANNEL, "SQLException：" + messageReceivedEvent.getGuild().getName() + " - " + messageReceivedEvent.getChannel().getName() + " (" + messageReceivedEvent.getChannel().getId() + ")" + e.getMessage());
            }
            if (genericEvent instanceof SlashCommandInteractionEvent slashCommandInteractionEvent) {
                replySlashCommand(slashCommandInteractionEvent, "發生了資料庫錯誤，請稍後再試一次。如果這個問題持續存在，請聯絡[Raymond Weng](https://raymondweng.dev/)。");
                Logger.log(Logger.EXCEPTION_CHANNEL, "SQLException：" + slashCommandInteractionEvent.getGuild().getName() + " - " + slashCommandInteractionEvent.getChannel().getName() + " (" + slashCommandInteractionEvent.getChannel().getId() + ")" + e.getMessage());

            }
        }
    }

    private void replySlashCommand(SlashCommandInteractionEvent slashCommandInteractionEvent, String message) {
        slashCommandInteractionEvent.getInteraction().getHook().sendMessage(message).queue();
    }

    private void message(String channelId,
                         String messageId,
                         String author,
                         String messageContext,
                         String attachmentDesc,
                         boolean doReply,
                         String replyTo,
                         String replyContext) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/" + channelId + ".db")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO messages (message_id, author_id, content, has_attachment, attachment_desc, do_reply, reply_to_author, reply_to_content) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, messageId);
                ps.setString(2, author);
                ps.setString(3, messageContext);
                if (!attachmentDesc.isEmpty()) {
                    ps.setInt(4, 1);
                    ps.setString(5, attachmentDesc.toString());
                } else {
                    ps.setInt(4, 0);
                    ps.setString(5, null);
                }
                if (doReply) {
                    ps.setInt(6, 1);
                    ps.setString(7, replyTo);
                    ps.setString(8, replyContext);
                } else {
                    ps.setInt(6, 0);
                    ps.setString(7, null);
                    ps.setString(8, null);
                }
                ps.executeUpdate();
            }
        }
    }

    private boolean isChannelMonitored(String channelId) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM monitored_channels WHERE channel_id = ?")) {
                ps.setString(1, channelId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rs.close();
                        ps.close();
                        connection.close();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateMonitoringCnt() throws SQLException {
        int cnt = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) AS cnt FROM monitored_channels")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cnt = rs.getInt("cnt");
                    }
                }
            }
        }
        if (!Main.dotenv.get("DISCORD_CHANNEL_COUNT_VOICE_CHANNEL_ID").equals("-1")) {
            Main.jda.getVoiceChannelById(Main.dotenv.get("DISCORD_CHANNEL_COUNT_VOICE_CHANNEL_ID")).getManager().setName("監控頻道數：" + cnt).queue();
        }
    }
}