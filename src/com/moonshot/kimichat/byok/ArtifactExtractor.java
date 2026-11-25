package com.moonshot.kimichat.byok;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts single-file HTML artifacts from assistant replies: full HTML documents,
 * or ```html fenced code blocks. POC scope: self-contained pages only.
 */
public final class ArtifactExtractor {

    public static final class Artifact {
        public final String title;
        public final String html;

        public Artifact(String title, String html) {
            this.title = title;
            this.html = html;
        }
    }

    private static final Pattern FENCE =
            Pattern.compile("```([a-zA-Z]*)[ \\t]*\\r?\\n(.*?)\\r?\\n?```", Pattern.DOTALL);
    private static final Pattern HTML_DOC = Pattern.compile(
            "<!\\s*DOCTYPE\\s+html[\\s\\S]*</\\s*html\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile(
            "<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Scans a whole conversation transcript for artifacts, deduplicated by content. */
    public static List<Artifact> scan(String transcript) {
        List<Artifact> out = new ArrayList<>();
        if (transcript == null || transcript.isEmpty()) {
            return out;
        }
        Matcher doc = HTML_DOC.matcher(transcript);
        while (doc.find()) {
            add(out, doc.group());
        }
        Matcher fence = FENCE.matcher(transcript);
        while (fence.find()) {
            String lang = fence.group(1) == null ? "" : fence.group(1).toLowerCase();
            String body = fence.group(2);
            if ("html".equals(lang) || looksLikeHtml(body)) {
                add(out, body);
            }
        }
        return out;
    }

    /** Returns the first artifact in a single reply, or null. */
    public static Artifact firstIn(String reply) {
        List<Artifact> all = scan(reply);
        return all.isEmpty() ? null : all.get(0);
    }

    private static boolean looksLikeHtml(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return t.startsWith("<!DOCTYPE") || t.startsWith("<!doctype")
                || (t.startsWith("<html") && t.contains("</html>"))
                || (t.startsWith("<div") && t.contains("<script"));
    }

    private static void add(List<Artifact> out, String html) {
        String trimmed = html.trim();
        for (Artifact a : out) {
            if (a.html.equals(trimmed)) {
                return;
            }
        }
        out.add(new Artifact(titleFor(trimmed), trimmed));
    }

    private static String titleFor(String html) {
        Matcher m = TITLE.matcher(html);
        if (m.find() && !m.group(1).trim().isEmpty()) {
            return trimTo(m.group(1).trim(), 60);
        }
        return "artifact-" + (html.hashCode() & 0xffff);
    }

    /** Filesystem/url-safe slug for repo naming. */
    public static String slug(String title) {
        String s = title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            s = "artifact";
        }
        return trimTo(s, 40);
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private ArtifactExtractor() {
    }
}
