package com.moonshot.kimichat.byok;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Called from the host app's kimi:// router (h40.n.f) via one smali branch.
 * kimi://byok or kimi://chat -> BYOK chat; kimi://byok-settings -> settings.
 * Returns true when the URI was handled here and the host router should stop.
 */
public final class ByokRouter {

    public static boolean handleDeepLink(Context context, Intent intent) {
        try {
            if (context == null || intent == null) {
                return false;
            }
            Uri data = intent.getData();
            if (data == null) {
                return false;
            }
            if (!"kimi".equals(data.getScheme())) {
                return false;
            }
            String host = data.getHost() == null ? "" : data.getHost().toLowerCase();
            if ("byok".equals(host) || "byok-chat".equals(host) || "chat".equals(host)) {
                Intent i = new Intent(context, ByokMainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                return true;
            }
            if ("byok-settings".equals(host) || "settings".equals(host)) {
                Intent i = new Intent(context, ByokSettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                return true;
            }
            return false;
        } catch (Throwable t) {
            // never break the host router
            return false;
        }
    }

    private ByokRouter() {
    }
}
