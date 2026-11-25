package com.moonshot.kimichat.byok;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider rows (switch / base URL / key / auto-fetched model picker), feature switches,
 * and the GitHub publishing section. Every change is persisted immediately.
 */
public class ByokSettingsActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingFetch = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(header("Providers"));
        for (int i = 0; i < ByokPrefs.PROVIDER_IDS.length; i++) {
            root.addView(providerRow(ByokPrefs.PROVIDER_IDS[i], ByokPrefs.PROVIDER_LABELS[i], i));
        }

        root.addView(header("System prompt (optional)"));
        root.addView(systemPromptField());

        root.addView(header("Features"));
        root.addView(switchRow("Artifacts (extract & preview HTML)",
                ByokPrefs.artifactsEnabled(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        ByokPrefs.setArtifactsEnabled(ByokSettingsActivity.this, v);
                    }
                }));

        root.addView(header("GitHub publishing"));
        root.addView(githubSection());

        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(done);
    }

    // ---------- provider row ----------

    private View providerRow(final String id, final String label, final int slot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(6), dp(10), dp(10));
        card.setBackgroundColor(0x14118888);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name);
        Switch en = new Switch(this);
        en.setChecked(ByokPrefs.providerEnabled(this, id));
        en.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                ByokPrefs.setProviderEnabled(ByokSettingsActivity.this, id, v);
            }
        });
        head.addView(en);
        card.addView(head);

        final EditText url = labeledField(card, "Base URL");
        url.setText(ByokPrefs.providerUrl(this, id));
        url.addTextChangedListener(saver(new TextConsumer() {
            @Override
            public void take(String s) {
                ByokPrefs.setProviderUrl(ByokSettingsActivity.this, id, s);
            }
        }));

        final EditText key = labeledField(card, "API key");
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(ByokPrefs.providerKey(this, id));
        key.addTextChangedListener(saver(new TextConsumer() {
            @Override
            public void take(String s) {
                ByokPrefs.setProviderKey(ByokSettingsActivity.this, id, s);
                scheduleModelFetch(id, url.getText().toString(), s);
            }
        }));

        final EditText model = labeledField(card, "Model");
        model.setText(ByokPrefs.providerModel(this, id));
        model.addTextChangedListener(saver(new TextConsumer() {
            @Override
            public void take(String s) {
                ByokPrefs.setProviderModel(ByokSettingsActivity.this, id, s);
            }
        }));

        final TextView status = new TextView(this);
        status.setTextSize(12);
        card.addView(status);

        // spinner appears once models are known; picking fills the model field
        final Spinner models = new Spinner(this);
        models.setVisibility(View.GONE);
        card.addView(models);

        String cached = ByokPrefs.providerModelsCache(this, id);
        if (!cached.isEmpty()) {
            applyModels(models, status, model, id, cached);
        }
        return card;
    }

    private void scheduleModelFetch(final String id, final String baseUrl, final String key) {
        if (key == null || key.trim().length() < 8 || baseUrl == null || baseUrl.trim().isEmpty()) {
            return;
        }
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final ByokApi.Result res = ByokApi.models(baseUrl.trim(), key.trim());
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                onModelsFetched(id, res);
                            }
                        });
                    }
                }).start();
            }
        };
        pendingFetch.add(r);
        ui.removeCallbacks(r);
        ui.postDelayed(r, 800); // debounce pasting
    }

    private void onModelsFetched(String id, ByokApi.Result res) {
        LinearLayout card = findProviderCard(id);
        if (card == null) {
            return;
        }
        Spinner models = (Spinner) card.getChildAt(card.getChildCount() - 1);
        TextView status = (TextView) card.getChildAt(card.getChildCount() - 2);
        EditText modelField = (EditText) card.getChildAt(card.getChildCount() - 3);
        if (res.ok) {
            ByokPrefs.setProviderModelsCache(this, id, res.content);
            applyModels(models, status, modelField, id, res.content);
        } else {
            status.setText("✗ model fetch failed: " + res.content);
            status.setTextColor(0xFFCC4444);
            models.setVisibility(View.GONE);
        }
    }

    private LinearLayout findProviderCard(String id) {
        // provider cards are children 1..4 of the root list (after the "Providers" header)
        ViewGroup root = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        LinearLayout list = (LinearLayout) root.getChildAt(0);
        for (int i = 0; i < ByokPrefs.PROVIDER_IDS.length; i++) {
            if (ByokPrefs.PROVIDER_IDS[i].equals(id)) {
                return (LinearLayout) list.getChildAt(i + 1);
            }
        }
        return null;
    }

    private void applyModels(Spinner models, TextView status, EditText modelField,
                             String id, String csv) {
        List<String> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            if (!s.trim().isEmpty()) {
                ids.add(s.trim());
            }
        }
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ids);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        models.setAdapter(ad);
        models.setVisibility(View.VISIBLE);
        status.setText("✓ " + ids.size() + " models fetched");
        status.setTextColor(0xFF339955);
        String saved = ByokPrefs.providerModel(this, id);
        int at = ids.indexOf(saved);
        if (at >= 0) {
            models.setSelection(at);
        } else {
            String def = ByokPrefs.defaultModel(id);
            at = ids.indexOf(def);
            if (at < 0 && !ids.isEmpty()) {
                at = 0;
            }
            if (at >= 0) {
                models.setSelection(at);
                modelField.setText(ids.get(at));
                ByokPrefs.setProviderModel(this, id, ids.get(at));
            }
        }
        models.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long row) {
                String picked = (String) p.getItemAtPosition(pos);
                if (picked != null && !picked.contentEquals(modelField.getText())) {
                    modelField.setText(picked);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
    }

    // ---------- misc fields ----------

    private View systemPromptField() {
        final EditText et = new EditText(this);
        et.setHint("Sent as the system message");
        et.setText(ByokPrefs.systemPrompt(this));
        et.setMaxLines(3);
        et.addTextChangedListener(saver(new TextConsumer() {
            @Override
            public void take(String s) {
                ByokPrefs.setSystemPrompt(ByokSettingsActivity.this, s);
            }
        }));
        return et;
    }

    private View githubSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(6), dp(10), dp(10));
        box.setBackgroundColor(0x14118888);

        final EditText pat = labeledField(box, "GitHub Personal Access Token");
        pat.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pat.setText(ByokPrefs.githubPat(this));
        pat.addTextChangedListener(saver(new TextConsumer() {
            @Override
            public void take(String s) {
                ByokPrefs.setGithubPat(ByokSettingsActivity.this, s);
            }
        }));

        final TextView who = new TextView(this);
        who.setTextSize(12);
        box.addView(who);
        Button check = new Button(this);
        check.setText("Check token");
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String token = pat.getText().toString().trim();
                if (token.isEmpty()) {
                    who.setText("Enter a token first");
                    return;
                }
                who.setText("Checking…");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String login = GitHubPublisher.whoAmI(token);
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (login == null) {
                                    who.setText("✗ token rejected (401) — check scope");
                                    who.setTextColor(0xFFCC4444);
                                } else {
                                    ByokPrefs.setGithubLogin(ByokSettingsActivity.this, login);
                                    who.setText("✓ authenticated as " + login);
                                    who.setTextColor(0xFF339955);
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        box.addView(check);

        box.addView(switchRow("Publish artifacts to private repos",
                ByokPrefs.githubPrivateRepo(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        ByokPrefs.setGithubPrivateRepo(ByokSettingsActivity.this, v);
                    }
                }));
        TextView note = new TextView(this);
        note.setTextSize(12);
        note.setText("Use a fine-grained PAT limited to contents+pages write on your artifact repos.");
        box.addView(note);
        return box;
    }

    private EditText labeledField(LinearLayout parent, String hint) {
        TextView l = new TextView(this);
        l.setText(hint);
        l.setTextSize(12);
        parent.addView(l);
        EditText et = new EditText(this);
        et.setSingleLine(true);
        parent.addView(et);
        return et;
    }

    private View switchRow(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(l);
        row.addView(s);
        return row;
    }

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private interface TextConsumer {
        void take(String s);
    }

    private TextWatcher saver(final TextConsumer c) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                c.take(s.toString());
            }
        };
    }
}
