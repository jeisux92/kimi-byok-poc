package com.moonshot.kimichat.byok;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Renders an extracted artifact in a local WebView and publishes it to GitHub Pages.
 */
public class ArtifactPreviewActivity extends Activity {

    private String html = "";
    private String title = "artifact";
    private TextView publishStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        html = getIntent().getStringExtra("html");
        title = getIntent().getStringExtra("title");
        if (title == null || title.isEmpty()) {
            title = "artifact";
        }
        if (html == null) {
            html = "<html><body><p>missing artifact</p></body></html>";
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(t);
        Button publish = new Button(this);
        publish.setText("Publish");
        publish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmPublish();
            }
        });
        bar.addView(publish);
        root.addView(bar);

        publishStatus = new TextView(this);
        publishStatus.setTextSize(12);
        publishStatus.setPadding(dp(12), 0, dp(12), dp(4));
        root.addView(publishStatus);

        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.loadDataWithBaseURL("https://byok.local/", html, "text/html", "utf-8", null);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void confirmPublish() {
        final String pat = ByokPrefs.githubPat(this).trim();
        if (pat.isEmpty()) {
            Toast.makeText(this, "Add a GitHub token in ⚙ settings first", Toast.LENGTH_LONG).show();
            return;
        }
        if (!ByokPrefs.artifactsEnabled(this)) {
            Toast.makeText(this, "Artifacts are disabled in settings", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        TextView hint = new TextView(this);
        hint.setText("Repository name for GitHub Pages:");
        box.addView(hint);
        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText("byok-artifact-" + ArtifactExtractor.slug(title));
        box.addView(name);
        new AlertDialog.Builder(this)
                .setTitle("Publish artifact")
                .setView(box)
                .setPositiveButton("Publish", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        doPublish(pat, name.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doPublish(final String pat, final String repo) {
        publishStatus.setText("Publishing…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final GitHubPublisher.PublishResult r =
                        GitHubPublisher.publish(pat, repo, ByokPrefs.githubPrivateRepo(ArtifactPreviewActivity.this), html);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (r.ok) {
                            publishStatus.setText("✓ live: " + r.url + " (build may take ~1 min)");
                            publishStatus.setTextColor(0xFF339955);
                            showUrl(r.url);
                        } else {
                            publishStatus.setText("✗ " + r.url);
                            publishStatus.setTextColor(0xFFCC4444);
                        }
                    }
                });
            }
        }).start();
    }

    private void showUrl(final String url) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        TextView t = new TextView(this);
        t.setText(url);
        t.setTextIsSelectable(true);
        box.addView(t);
        new AlertDialog.Builder(this)
                .setTitle("Published to GitHub Pages")
                .setView(box)
                .setPositiveButton("Copy URL", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("url", url));
                        Toast.makeText(ArtifactPreviewActivity.this, "URL copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Close", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
