package dev.ftadvisor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Необязательный запрос к OpenAI-совместимому API (DeepSeek и т.п.). */
public final class AiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public static CompletableFuture<String> ask(String prompt) {
        try {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", prompt);
            JsonArray msgs = new JsonArray();
            msgs.add(msg);
            JsonObject body = new JsonObject();
            body.addProperty("model", Config.I.apiModel);
            body.add("messages", msgs);
            HttpRequest req = HttpRequest.newBuilder(URI.create(Config.I.apiUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Config.I.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(r -> {
                        if (r.statusCode() != 200) return "Ошибка API: HTTP " + r.statusCode();
                        try {
                            return JsonParser.parseString(r.body()).getAsJsonObject()
                                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                                    .getAsJsonObject("message").get("content").getAsString();
                        } catch (Exception e) {
                            return "Не удалось разобрать ответ API.";
                        }
                    })
                    .exceptionally(e -> "Нет связи с API: " + e.getClass().getSimpleName());
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Ошибка запроса: " + e.getMessage());
        }
    }
}
