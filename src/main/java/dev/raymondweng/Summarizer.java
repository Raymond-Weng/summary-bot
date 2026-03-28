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
            String prompt = "你現在面對許多Discord訊息，你的職責是要統整訊息內容，並且僅回傳統整的內容，不需要最任何其他的回應，且以繁體中文取代簡體中文。" +
                    "統整時避免紀錄敏感訊息及閒聊，統整出主題及討論的結果。當主題偏離且統整內容過長，刪除舊主題的統整內容，但至少保留最近的主題。" +
                    "這裡有上次統整的內容：\n" +
                    lastSummary +
                    "\n以下是新訊息，格式為：<傳送者>訊息內容[附加檔案名稱](回覆內容)，且中括號及小括號在內容不存在時會被省略：\n" +
                    message;
            System.out.println(prompt);
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
                System.out.println(summary);
            } else {
                Logger.log(Logger.EXCEPTION_CHANNEL, "Summarizer API error:" + channelID + ": " + httpResponse.statusCode() + " " + httpResponse.body());
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
