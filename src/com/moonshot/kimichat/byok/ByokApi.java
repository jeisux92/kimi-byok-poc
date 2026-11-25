package com.moonshot.kimichat.byok;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal OpenAI-compatible HTTP client: /chat/completions and /models.
 * Plain HttpURLConnection, no third-party dependencies.
 */
public final class ByokApi {

    public static final class Result {
        public final boolean ok;
        public final String content;   // assistant text on success, error message on failure
        public final int httpCode;

        Result(boolean ok, String content, int httpCode) {
            this.ok = ok;
            this.content = content;
            this.httpCode = httpCode;
        }
    }

    /** Sends a chat completion. messages: array of {"role","content"}. */
    public static Result chat(String baseUrl, String key, String model, String systemPrompt,
                              List<String[]> history, String userText) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            }
            if (history != null) {
                for (String[] m : history) {
                    msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
                }
            }
            msgs.put(new JSONObject().put("role", "user").put("content", userText));
            body.put("messages", msgs);
            String path = join(baseUrl, "/chat/completions");
            JSONObject resp = postJson(path, key, body.toString());
            int code = resp.optInt("__code", 0);
            if (code != 200) {
                return new Result(false, humanizeError(code, resp.optString("__body", "")), code);
            }
            JSONObject payload = resp.optJSONObject("__json");
            if (payload == null) {
                return new Result(false, "Empty response from provider", code);
            }
            String text = extractContent(payload);
            if (text == null) {
                return new Result(false, "Unexpected response shape from provider", code);
            }
            return new Result(true, text, code);
        } catch (Exception e) {
            return new Result(false, "Network error: " + e.getMessage(), 0);
        }
    }

    /** Fetches model ids via GET {baseUrl}/models. Returns ids or an error-bearing single-entry list. */
    public static Result models(String baseUrl, String key) {
        try {
            HttpURLConnection conn = open(join(baseUrl, "/models"), key, "GET", null);
            int code = conn.getResponseCode();
            String body = readStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();
            if (code != 200) {
                return new Result(false, humanizeError(code, body), code);
            }
            JSONObject json = new JSONObject(body);
            JSONArray data = json.optJSONArray("data");
            List<String> ids = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    String id = data.optJSONObject(i) == null ? null : data.optJSONObject(i).optString("id", null);
                    if (id != null && !id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
            if (ids.isEmpty()) {
                return new Result(false, "Provider returned no models", code);
            }
            return new Result(true, joinIds(ids), code);
        } catch (Exception e) {
            return new Result(false, "Network error: " + e.getMessage(), 0);
        }
    }

    static String joinIds(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    static String extractContent(JSONObject payload) {
        JSONArray choices = payload.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return null;
        }
        JSONObject first = choices.optJSONObject(0);
        if (first == null) {
            return null;
        }
        JSONObject message = first.optJSONObject("message");
        if (message != null) {
            return message.optString("content", null);
        }
        return first.optString("text", null);
    }

    static String humanizeError(int code, String body) {
        String detail = trimTo(body.replaceAll("\\s+", " "), 220);
        if (code == 401 || code == 403) {
            return "Invalid API key or insufficient access (" + code + ")" + suffix(detail);
        }
        if (code == 429) {
            return "Rate limited by provider (429)" + suffix(detail);
        }
        return "Provider error (HTTP " + code + ")" + suffix(detail);
    }

    private static String suffix(String detail) {
        return detail.isEmpty() ? "" : ": " + detail;
    }

    static String join(String base, String path) {
        String b = base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b + path;
    }

    /** POST helper returning wrapper: __code, __body, __json, and echoes the request result. */
    static JSONObject postJson(String url, String key, String jsonBody) throws Exception {
        HttpURLConnection conn = open(url, key, "POST", "application/json");
        byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(out.length);
        OutputStream os = conn.getOutputStream();
        os.write(out);
        os.flush();
        os.close();
        int code = conn.getResponseCode();
        String body = readStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        JSONObject wrapper = new JSONObject();
        wrapper.put("__code", code);
        wrapper.put("__body", body);
        try {
            wrapper.put("__json", new JSONObject(body));
        } catch (Exception ignore) {
            // non-JSON body stays absent
        }
        return wrapper;
    }

    static HttpURLConnection open(String url, String key, String method, String contentType)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod(method);
        if (key != null && !key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        if (contentType != null) {
            conn.setRequestProperty("Content-Type", contentType);
        }
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "byok-poc/0.1");
        if ("POST".equals(method)) {
            conn.setDoOutput(true);
        }
        return conn;
    }

    static String readStream(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return trimTo(sb.toString(), 200000);
    }

    static String trimTo(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private ByokApi() {
    }
}
