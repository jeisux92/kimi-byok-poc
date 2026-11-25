package com.moonshot.kimichat.byok;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Login-free BYOK chat: provider quick-switcher, chat log, artifacts entry point.
 * Launcher alias "Kimi BYOK" and kimi://byok both land here.
 */
public class ByokMainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<String[]> history = new ArrayList<>(); // {role, content}
    private TextView log;
    private EditText input;
    private Spinner providerSpinner;
    private final List<Integer> enabledIndexes = new ArrayList<>();
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        appendLog("BYOK chat ready. Configure a provider in ⚙ (paste a key — models load automatically).\n");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProviderSpinner();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        // top bar: provider switcher + settings + artifacts
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        providerSpinner = new Spinner(this);
        providerSpinner.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(providerSpinner);
        top.addView(button("⚙", 48, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ByokMainActivity.this, ByokSettingsActivity.class));
            }
        }));
        top.addView(button("🖼", 48, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openArtifacts();
            }
        }));
        root.addView(top);

        // chat log
        log = new TextView(this);
        log.setTextSize(14);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        ScrollView scroller = new ScrollView(this);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroller.addView(log);
        root.addView(scroller);

        // input row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        input = new EditText(this);
        input.setHint("Ask anything…");
        input.setMaxLines(4);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);
        Button send = button("Send", 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        send.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(send);
        root.addView(row);

        setContentView(root);
    }

    private Button button(String label, int widthDp, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        if (widthDp > 0) {
            b.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(48)));
        }
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refreshProviderSpinner() {
        enabledIndexes.clear();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < ByokPrefs.PROVIDER_IDS.length; i++) {
            String id = ByokPrefs.PROVIDER_IDS[i];
            if (ByokPrefs.providerEnabled(this, id)
                    && !ByokPrefs.providerKey(this, id).trim().isEmpty()
                    && !ByokPrefs.providerUrl(this, id).trim().isEmpty()) {
                enabledIndexes.add(i);
                names.add(ByokPrefs.PROVIDER_LABELS[i]);
            }
        }
        if (names.isEmpty()) {
            names.add("no provider enabled — open ⚙");
        }
        android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(ad);
        int active = ByokPrefs.activeProvider(this);
        int pos = enabledIndexes.indexOf(active);
        if (pos >= 0) {
            providerSpinner.setSelection(pos);
        }
    }

    private void send() {
        if (busy) {
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        final int idx = currentProviderIndex();
        if (idx < 0) {
            appendLog("⚠ Enable a provider and paste its API key in ⚙ settings first.\n");
            return;
        }
        input.setText("");
        appendLog("You: " + text + "\n");
        final String url = ByokPrefs.providerUrl(this, ByokPrefs.PROVIDER_IDS[idx]);
        final String key = ByokPrefs.providerKey(this, ByokPrefs.PROVIDER_IDS[idx]);
        final String model = ByokPrefs.providerModel(this, ByokPrefs.PROVIDER_IDS[idx]);
        final String system = ByokPrefs.systemPrompt(this);
        busy = true;
        appendLog("…\n");
        final String userText = text;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ByokApi.Result r = ByokApi.chat(url, key, model, system,
                        new ArrayList<>(history), userText);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (r.ok) {
                            history.add(new String[]{"user", userText});
                            history.add(new String[]{"assistant", r.content});
                            appendLog("\nAI: " + r.content + "\n\n");
                            if (ArtifactExtractor.firstIn(r.content) != null
                                    && ByokPrefs.artifactsEnabled(ByokMainActivity.this)) {
                                appendLog("🖼 artifact detected — tap 🖼 to preview & publish.\n");
                            }
                        } else {
                            appendLog("\n✗ " + r.content + "\n\n");
                        }
                    }
                });
            }
        }).start();
    }

    private int currentProviderIndex() {
        int pos = providerSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= enabledIndexes.size()) {
            return ByokPrefs.activeProvider(this);
        }
        return enabledIndexes.get(pos);
    }

    private void openArtifacts() {
        if (!ByokPrefs.artifactsEnabled(this)) {
            Toast.makeText(this, "Artifacts are disabled in settings", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] m : history) {
            if ("assistant".equals(m[0])) {
                sb.append(m[1]).append("\n\n");
            }
        }
        final List<ArtifactExtractor.Artifact> artifacts = ArtifactExtractor.scan(sb.toString());
        if (artifacts.isEmpty()) {
            Toast.makeText(this, "No artifacts in this conversation yet", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> titles = new ArrayList<>();
        for (ArtifactExtractor.Artifact a : artifacts) {
            titles.add(a.title);
        }
        new AlertDialog.Builder(this)
                .setTitle("Artifacts")
                .setItems(titles.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        ArtifactExtractor.Artifact a = artifacts.get(which);
                        Intent i = new Intent(ByokMainActivity.this, ArtifactPreviewActivity.class);
                        i.putExtra("html", a.html);
                        i.putExtra("title", a.title);
                        startActivity(i);
                    }
                })
                .show();
    }

    private void appendLog(String s) {
        if (TextUtils.isEmpty(s)) {
            return;
        }
        log.append(s);
    }
}
