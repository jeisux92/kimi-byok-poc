package com.moonshot.kimichat.byok;

import android.util.Base64;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Publishes a single-file HTML artifact to GitHub Pages using the REST API only
 * (no git binary): create repo -> commit index.html -> enable Pages -> report URL.
 */
public final class GitHubPublisher {

    public static final class PublishResult {
        public final boolean ok;
        public final String url;      // live Pages URL on success, error message on failure
        public String repo;

        PublishResult(boolean ok, String url) {
            this.ok = ok;
            this.url = url;
        }
    }

    private static final String API = "https://api.github.com";

    /** Blocking; call from a background thread. */
    public static PublishResult publish(String pat, String repoName, boolean privateRepo, String html) {
        try {
            String login = whoAmI(pat);
            if (login == null) {
                return new PublishResult(false, "GitHub token rejected (401). Check the PAT.");
            }
            String repo = repoName == null || repoName.trim().isEmpty()
                    ? "byok-artifact-" + System.currentTimeMillis() / 1000 % 100000
                    : repoName.trim();
            // 1. create repo (422 name-exists is treated as reuse)
            Response create = raw("POST", API + "/user/repos", pat,
                    new JSONObject()
                            .put("name", repo)
                            .put("description", "Published from kimi-byok-poc")
                            .put("private", privateRepo)
                            .put("auto_init", false)
                            .toString());
            if (create.code == 422) {
                // name taken by us or someone else; try to reuse when we own it
                Response check = raw("GET", API + "/repos/" + login + "/" + repo, pat, null);
                if (check.code != 200) {
                    return new PublishResult(false, "Repo name '" + repo + "' already exists and is not yours.");
                }
            } else if (create.code != 201) {
                return new PublishResult(false, "Repo create failed (HTTP " + create.code + "): "
                        + messageOf(create.body));
            }
            // 2. commit index.html
            String encoded = Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            JSONObject putBody = new JSONObject()
                    .put("message", "Publish artifact from kimi-byok-poc")
                    .put("content", encoded);
            Response put = raw("PUT", API + "/repos/" + login + "/" + repo + "/contents/index.html",
                    pat, putBody.toString());
            if (put.code != 201 && put.code != 200) {
                return new PublishResult(false, "Commit failed (HTTP " + put.code + "): " + messageOf(put.body));
            }
            // 3. enable Pages (409 = already enabled)
            JSONObject pagesBody = new JSONObject()
                    .put("source", new JSONObject().put("branch", "main").put("path", "/"));
            Response pages = raw("POST", API + "/repos/" + login + "/" + repo + "/pages", pat,
                    pagesBody.toString());
            if (pages.code != 201 && pages.code != 409) {
                return new PublishResult(false, "Pages enable failed (HTTP " + pages.code + "): "
                        + messageOf(pages.body));
            }
            // 4. fetch Pages info for canonical URL
            Response info = raw("GET", API + "/repos/" + login + "/" + repo + "/pages", pat, null);
            String url = null;
            if (info.code == 200) {
                try {
                    url = new JSONObject(info.body).optString("html_url", null);
                } catch (Exception ignore) {
                    // fall through to constructed URL
                }
            }
            if (url == null || url.isEmpty()) {
                url = "https://" + login + ".github.io/" + repo + "/";
            }
            PublishResult r = new PublishResult(true, url);
            r.repo = repo;
            return r;
        } catch (Exception e) {
            return new PublishResult(false, "Publish error: " + e.getMessage());
        }
    }

    /** Blocking; returns the login for a PAT or null when rejected. */
    public static String whoAmI(String pat) {
        try {
            Response r = raw("GET", API + "/user", pat, null);
            if (r.code != 200) {
                return null;
            }
            return new JSONObject(r.body).optString("login", null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String messageOf(String body) {
        try {
            return new JSONObject(body).optString("message", "unknown");
        } catch (Exception e) {
            return ByokApi.trimTo(body.replaceAll("\\s+", " "), 160);
        }
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static Response raw(String method, String url, String pat, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + pat);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        conn.setRequestProperty("User-Agent", "byok-poc/0.1");
        if (jsonBody != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(out.length);
            conn.getOutputStream().write(out);
            conn.getOutputStream().close();
        }
        int code = conn.getResponseCode();
        String body = ByokApi.readStream(code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        return new Response(code, body);
    }

    private GitHubPublisher() {
    }
}
