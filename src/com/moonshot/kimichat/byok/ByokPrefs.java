package com.moonshot.kimichat.byok;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Device-private storage for BYOK configuration. Values never leave the device except
 * to the endpoint the user configured (provider API / api.github.com).
 */
public final class ByokPrefs {

    public static final String[] PROVIDER_IDS = {"deepseek", "moonshot", "openai", "custom"};
    public static final String[] PROVIDER_LABELS = {"DeepSeek", "Moonshot OpenPlatform", "OpenAI", "Custom"};
    public static final String[] PROVIDER_URLS = {
            "https://api.deepseek.com", "https://api.moonshot.ai/v1", "https://api.openai.com/v1", ""
    };
    public static final String[] PROVIDER_DEFAULT_MODELS = {
            "deepseek-chat", "moonshot-v1-8k", "gpt-4o-mini", ""
    };

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("byok_prefs", Context.MODE_PRIVATE);
    }

    // --- feature switches ---
    public static boolean artifactsEnabled(Context c) {
        return prefs(c).getBoolean("feature_artifacts", true);
    }

    public static void setArtifactsEnabled(Context c, boolean v) {
        prefs(c).edit().putBoolean("feature_artifacts", v).apply();
    }

    // --- per-provider config ---
    public static boolean providerEnabled(Context c, String id) {
        return prefs(c).getBoolean("en_" + id, false);
    }

    public static void setProviderEnabled(Context c, String id, boolean v) {
        prefs(c).edit().putBoolean("en_" + id, v).apply();
    }

    public static String providerUrl(Context c, String id) {
        String d = defaultValue(id);
        return prefs(c).getString("url_" + id, d == null ? "" : d);
    }

    public static void setProviderUrl(Context c, String id, String v) {
        prefs(c).edit().putString("url_" + id, v).apply();
    }

    public static String providerKey(Context c, String id) {
        return prefs(c).getString("key_" + id, "");
    }

    public static void setProviderKey(Context c, String id, String v) {
        prefs(c).edit().putString("key_" + id, v).apply();
    }

    public static String providerModel(Context c, String id) {
        return prefs(c).getString("model_" + id, "");
    }

    public static void setProviderModel(Context c, String id, String v) {
        prefs(c).edit().putString("model_" + id, v).apply();
    }

    public static String providerModelsCache(Context c, String id) {
        return prefs(c).getString("models_" + id, "");
    }

    public static void setProviderModelsCache(Context c, String id, String csv) {
        prefs(c).edit().putString("models_" + id, csv).apply();
    }

    public static String systemPrompt(Context c) {
        return prefs(c).getString("system_prompt", "");
    }

    public static void setSystemPrompt(Context c, String v) {
        prefs(c).edit().putString("system_prompt", v).apply();
    }

    // --- GitHub publish config ---
    public static String githubPat(Context c) {
        return prefs(c).getString("gh_pat", "");
    }

    public static void setGithubPat(Context c, String v) {
        prefs(c).edit().putString("gh_pat", v).apply();
    }

    public static String githubLogin(Context c) {
        return prefs(c).getString("gh_login", "");
    }

    public static void setGithubLogin(Context c, String v) {
        prefs(c).edit().putString("gh_login", v).apply();
    }

    public static boolean githubPrivateRepo(Context c) {
        return prefs(c).getBoolean("gh_private", false);
    }

    public static void setGithubPrivateRepo(Context c, boolean v) {
        prefs(c).edit().putBoolean("gh_private", v).apply();
    }

    /** First enabled provider with a key, by slot order. Returns null when none ready. */
    public static int activeProvider(Context c) {
        int saved = prefs(c).getInt("active_provider", -1);
        if (saved >= 0 && saved < PROVIDER_IDS.length
                && providerEnabled(c, PROVIDER_IDS[saved])
                && !providerKey(c, PROVIDER_IDS[saved]).isEmpty()
                && !providerUrl(c, PROVIDER_IDS[saved]).trim().isEmpty()) {
            return saved;
        }
        for (int i = 0; i < PROVIDER_IDS.length; i++) {
            if (providerEnabled(c, PROVIDER_IDS[i])
                    && !providerKey(c, PROVIDER_IDS[i]).isEmpty()
                    && !providerUrl(c, PROVIDER_IDS[i]).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void setActiveProvider(Context c, int index) {
        prefs(c).edit().putInt("active_provider", index).apply();
    }

    private static String defaultValue(String id) {
        for (int i = 0; i < PROVIDER_IDS.length; i++) {
            if (PROVIDER_IDS[i].equals(id)) {
                return PROVIDER_URLS[i];
            }
        }
        return "";
    }

    public static String defaultModel(String id) {
        for (int i = 0; i < PROVIDER_IDS.length; i++) {
            if (PROVIDER_IDS[i].equals(id)) {
                return PROVIDER_DEFAULT_MODELS[i];
            }
        }
        return "";
    }

    private ByokPrefs() {
    }
}
