package dev.raymondweng;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;
import java.util.List;

public class MessageController implements EventListener {
    List<String> ADMINS = List.of("1066517249906704524");

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
                }
            }
            if (genericEvent instanceof SlashCommandInteractionEvent slashCommandInteractionEvent) {
                slashCommandInteractionEvent.deferReply().setEphemeral(true).queue();
                switch (slashCommandInteractionEvent.getName()) {
                    case "summary":
                        if (isChannelMonitored(slashCommandInteractionEvent.getChannelId())) {
                            //TODO return result
                            slashCommandInteractionEvent
                                    .getJDA()
                                    .getGuildById("1484354171204407418")
                                    .getTextChannelById("1485810509788876971")
                                    .sendMessage("呼叫總結：" +
                                            slashCommandInteractionEvent.getGuild().getName() +
                                            " - " +
                                            slashCommandInteractionEvent.getChannel().getName() +
                                            " ("
                                            + slashCommandInteractionEvent.getChannelId() +
                                            ")"
                                    )
                                    .queue();
                        } else {
                            slashCommandInteractionEvent
                                    .getInteraction()
                                    .getHook()
                                    .sendMessage("這個頻道沒有被我們紀錄。" +
                                            "為了節約效能，我們並不會紀錄每個頻道，如果需要紀錄，請使用`/monitor`指令" +
                                            "（目前僅開發者可用，如有需要請聯絡[Raymond Weng](https://raymondweng.dev/)）。")
                                    .queue();
                        }
                        break;
                    case "monitor":
                        if (isChannelMonitored(slashCommandInteractionEvent.getChannelId())) {
                            slashCommandInteractionEvent
                                    .getInteraction()
                                    .getHook()
                                    .sendMessage("我們已經在記錄這個頻道的內容了！")
                                    .queue();
                        } else {
                            if (slashCommandInteractionEvent.getUser().getId().equals("1066517249906704524")) {
                                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
                                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO monitored_channels (channel_id) VALUES (?)")) {
                                        ps.setString(1, slashCommandInteractionEvent.getChannelId());
                                        ps.executeUpdate();
                                    }
                                }
                                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/" + slashCommandInteractionEvent.getChannelId() + ".db")) {
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
                                slashCommandInteractionEvent
                                        .getJDA()
                                        .getGuildById("1484354171204407418")
                                        .getTextChannelById("1484379156878721124")
                                        .sendMessage("追蹤頻道：" +
                                                slashCommandInteractionEvent.getGuild().getName() +
                                                " - " +
                                                slashCommandInteractionEvent.getChannel().getName() +
                                                " ("
                                                + slashCommandInteractionEvent.getChannelId() +
                                                ")"
                                        )
                                        .queue();
                                updateMonitoringCnt();
                                slashCommandInteractionEvent
                                        .getInteraction()
                                        .getHook()
                                        .sendMessage("已經成功紀錄這個頻道，請使用`/summary`來取得統整。我們會閱讀一些先前的訊息，這可能需要一點時間。")
                                        .queue();
                                //TODO message history reading
                            } else {
                                slashCommandInteractionEvent
                                        .getInteraction()
                                        .getHook()
                                        .sendMessage("這個指令目前僅開發者可用，如有需要請聯絡[Raymond Weng](https://raymondweng.dev/)。")
                                        .queue();
                            }
                        }
                        break;
                    case "stop":
                        String channelID = slashCommandInteractionEvent.getChannelId();
                        if (slashCommandInteractionEvent.getOption("channel_id") != null) {
                            channelID = slashCommandInteractionEvent.getOption("channel_id").getAsString();
                            if (slashCommandInteractionEvent.getJDA().getTextChannelById(channelID) == null) {
                                slashCommandInteractionEvent
                                        .getInteraction()
                                        .getHook()
                                        .sendMessage("無效的頻道ID。")
                                        .queue();
                                return;
                            }
                            if (!slashCommandInteractionEvent.getGuild().getId().equals(slashCommandInteractionEvent.getJDA().getTextChannelById(channelID).getGuild().getId())) {
                                if (!ADMINS.contains(slashCommandInteractionEvent.getUser().getId())) {
                                    slashCommandInteractionEvent
                                            .getInteraction()
                                            .getHook()
                                            .sendMessage("你沒有停止這個頻道的權限。")
                                            .queue();
                                    return;
                                }
                            }
                        }
                        if (isChannelMonitored(slashCommandInteractionEvent.getChannelId())) {
                            stop(channelID);
                            slashCommandInteractionEvent
                                    .getInteraction()
                                    .getHook()
                                    .sendMessage("已經停止關注這裡並刪除先前紀錄。")
                                    .queue();
                        } else {
                            slashCommandInteractionEvent
                                    .getInteraction()
                                    .getHook()
                                    .sendMessage("這裡還沒開始過，可能沒辦法停止")
                                    .queue();
                        }
                        break;
                    case "list":
                        if (slashCommandInteractionEvent.getUser().getId().equals("1066517249906704524")) {
                            //TODO list watching
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
                messageReceivedEvent
                        .getJDA()
                        .getGuildById("1484354171204407418")
                        .getTextChannelById("1485816952550195240")
                        .sendMessage("SQLException：" +
                                messageReceivedEvent.getGuild().getName() +
                                " - " +
                                messageReceivedEvent.getChannel().getName() +
                                " (" +
                                messageReceivedEvent.getChannel().getId() +
                                ")" +
                                e.getMessage()
                        )
                        .queue();
            }
            if (genericEvent instanceof SlashCommandInteractionEvent slashCommandInteractionEvent) {
                slashCommandInteractionEvent
                        .getInteraction()
                        .getHook()
                        .sendMessage("發生了資料庫錯誤，請稍後再試一次。如果這個問題持續存在，請聯絡[Raymond Weng](https://raymondweng.dev/)。")
                        .queue();
                slashCommandInteractionEvent
                        .getJDA()
                        .getGuildById("1484354171204407418")
                        .getTextChannelById("1485816952550195240")
                        .sendMessage("SQLException：" +
                                slashCommandInteractionEvent.getGuild().getName() +
                                " - " +
                                slashCommandInteractionEvent.getChannel().getName() +
                                " (" +
                                slashCommandInteractionEvent.getChannelId() +
                                ")" +
                                e.getMessage()
                        )
                        .queue();
            }
        }
    }

    private synchronized void summarize() {
        //TODO summarize
    }

    private void message(String channelId,
                         String messageId,
                         String author,
                         String messageContext,
                         String attachmentDesc,
                         boolean doReply,
                         String replyTo,
                         String replyContext) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/" + channelId + ".db");
        PreparedStatement ps = connection.prepareStatement("INSERT INTO messages (message_id, author_id, content, has_attachment, attachment_desc, do_reply, reply_to_author, reply_to_content) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
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
        ps.close();
        connection.close();
    }

    private boolean isChannelMonitored(String channelId) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db");
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM monitored_channels WHERE channel_id = ?");
        ps.setString(1, channelId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            rs.close();
            ps.close();
            connection.close();
            return true;
        }
        rs.close();
        ps.close();
        connection.close();
        return false;
    }

    private void stop(String channelID) throws SQLException {
        File file = new File("./db/" + channelID + ".db");
        if (file.exists()) {
            file.delete();
        }
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db");
            PreparedStatement ps = connection.prepareStatement("DELETE FROM monitored_channels WHERE channel_id = ?");
            ps.setString(1, channelID);
            ps.executeUpdate();
            ps.close();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Main.jda
                .getGuildById("1484354171204407418")
                .getTextChannelById("1484379156878721124")
                .sendMessage("停止追蹤：" +
                        Main.jda.getTextChannelById(channelID).getGuild().getName() +
                        " - " +
                        Main.jda.getTextChannelById(channelID).getName() +
                        " ("
                        + channelID +
                        ")"
                )
                .queue();
        updateMonitoringCnt();
    }

    private void updateMonitoringCnt() throws SQLException {
        int cnt = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/monitored_channels.db")) {
            try(PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) AS cnt FROM monitored_channels")) {
                try(ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cnt = rs.getInt("cnt");
                    }
                }
            }
        }
        Main.jda.getVoiceChannelById("1485501726915301487").getManager().setName("監控頻道數：" + cnt).queue();
    }
}