package dev.raymondweng;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Summarizer implements Runnable {
    public final String channelID;
    public static ConcurrentHashMap<String, ReentrantLock> channelLocks = new ConcurrentHashMap<>();
    public static HttpClient httpClient = HttpClient.newHttpClient();

    public Summarizer(String channelID) {
        this.channelID = channelID;
    }

    @Override
    public void run() {
        // avoid threads use old summary to summarize new things
        // one channel -> one thread
        ReentrantLock reentrantLock = channelLocks.computeIfAbsent(channelID, k -> new ReentrantLock());
        if(!reentrantLock.tryLock()) return;

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./db/" + channelID + ".db")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
            }
            String lastSummary;
            try(Statement statement = connection.createStatement()) {
                try(ResultSet resultSet = statement.executeQuery("SELECT value FROM status WHERE key = 'last_summary'")) {
                    resultSet.next();
                    lastSummary = resultSet.getString("value");
                }
            }
            String message;
            String lastSummarizeMessage = "";
            try(Statement statement = connection.createStatement()) {
                try(ResultSet resultSet = statement.executeQuery(("UPDATE messages set processed = 1 where message_id IN (SELECT message_id FROM messages WHERE processed = 0 ORDER BY message_id LIMIT 10) returning *"))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    while (resultSet.next()) {
                        stringBuilder.append(resultSet.getString("content")).append("\n");
                        lastSummarizeMessage = resultSet.getString("message_id");
                    }
                    message = stringBuilder.toString();
                }
            }
            String prompt = "你是一個專業的會議記錄員，負責整理 Discord 群組的對話重點。\n\n" +
                    "## 規則\n" +
                    "- 輸出必須是你依照訊息重新撰寫的摘要，嚴格禁止完全照抄原始訊息\n" +
                    "- 若新訊息不包含任何有意義的資訊（例如只有數字、符號、閒聊），則直接輸出先前摘要，不做任何修改，也不添加任何內容\n" +
                    "- 嚴禁虛構、推測或補充任何訊息中未提及的資訊" +
                    "- 忽略閒聊、表情符號回應、敏感內容、敏感訊息（例如密碼）\n" +
                    "- 聚焦在重要的人、事、時、地、物及決議\n" +
                    "- 以條列式呈現，每點不超過40字，總字數控制在200字內\n" +
                    "- 完全使用繁體中文摘要（除了英文專有名詞）\n" +
                    "- 只輸出摘要內容，不要有任何前言或說明\n\n" +
                    "## 先前摘要\n" +
                    lastSummary + "\n\n" +
                    "## 新訊息（格式：<傳送者>內容[附檔](回覆)）\n" +
                    message + "\n\n" +
                    "## 輸出：整合後的最新摘要";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(Main.dotenv.get("OLLAMA_API_URL")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "model", "mistral",
                                    "prompt", prompt,
                                    "stream", false
                            ))
                    ))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() == 200) {
                String summary = new ObjectMapper().readTree(httpResponse.body()).get("response").asText();
                try(PreparedStatement preparedStatement = connection.prepareStatement("UPDATE status SET value = ? WHERE key = 'last_summary'")) {
                    preparedStatement.setString(1, summary);
                    preparedStatement.executeUpdate();
                }
                try(PreparedStatement preparedStatement = connection.prepareStatement("UPDATE status SET value = ? WHERE key = 'last_summarize_message_id'")) {
                    preparedStatement.setString(1, lastSummarizeMessage);
                    preparedStatement.executeUpdate();
                }
                Logger.log(Logger.SUMMARY_CHANNEL, "Summarized: " + Main.jda.getTextChannelById(channelID).getGuild().getName() + " - " + Main.jda.getTextChannelById(channelID).getName() + "(" + channelID + ")");
            } else {
                Logger.log(Logger.EXCEPTION_CHANNEL, "Summarizer API error:" + channelID + ": " + httpResponse.statusCode() + " " + httpResponse.body());
            }
            try(Statement statement = connection.createStatement()){
                try(ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS cnt FROM messages WHERE processed = 0")) {
                    resultSet.next();
                    if(resultSet.getInt("cnt") > 0) {
                        Thread.startVirtualThread(new Summarizer(channelID));
                        return;
                    }
                }
            }
            try(Statement statement = connection.createStatement()){
                statement.execute("DELETE FROM messages WHERE processed = 1");
            }
        }catch (SQLException e) {
            Logger.log(Logger.EXCEPTION_CHANNEL, "Summarizer connection failed:" + channelID + ": " + e.getMessage());
        } catch (IOException | InterruptedException e) {
            Logger.log(Logger.EXCEPTION_CHANNEL, "Summarizer API request failed:" + channelID + ": " + e.getMessage());
        } finally {
            reentrantLock.unlock();
        }
    }
}
