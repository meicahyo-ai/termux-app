package com.termux.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Native notes UI of Termux Lab, intentionally independent from Obsidian. */
public final class TermuxLabKnowledgeActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(10, 9, 8);
    private static final int TEXT = Color.rgb(245, 226, 191);
    private static final int MUTED = Color.rgb(180, 163, 132);
    private static final int AMBER = Color.rgb(238, 188, 101);
    private static final int PANEL = Color.rgb(29, 26, 22);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autosave = () -> saveDraft(false);
    private TermuxLabVaultStore vault;
    private TermuxLabPluginStore plugins;
    private LinearLayout content;
    private EditText editor;
    private TextView saveState;
    private String editingName;
    private String editingFingerprint;
    private boolean dirty;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        try {
            File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
            vault = new TermuxLabVaultStore(home);
            plugins = new TermuxLabPluginStore(home);
        } catch (IOException error) {
            new AlertDialog.Builder(this).setTitle("Vault tidak tersedia")
                    .setMessage(error.getMessage()).setPositiveButton("Tutup", (d,w) -> finish()).show();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(px(10),px(8),px(10),px(8));
        top.addView(button("KEMBALI", v -> onBackPressed()));
        TextView title = label("TERMUX LAB / KNOWLEDGE", 15, AMBER);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title);
        root.addView(top);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(12),px(10),px(12),px(16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showList();
    }

    private int px(int dp) { return (int) (dp * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setPadding(px(7), px(8), px(7), px(8));
        return view;
    }
    private Button button(String name, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(name);
        btn.setTextSize(11);
        btn.setAllCaps(false);
        btn.setTextColor(TEXT);
        btn.setOnClickListener(listener);
        return btn;
    }
    private void row(Button... buttons) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) line.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(line);
    }
    private void msg(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void error(String title, Exception error) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(error.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private void showList() {
        handler.removeCallbacks(autosave);
        editor = null; editingName = null; dirty = false;
        content.removeAllViews();
        content.addView(label("KNOWLEDGE VAULT", 20, TEXT));
        content.addView(label("Mandiri • Markdown (.md) • folder lokal Termux Lab", 11, MUTED));
        row(button("+ CATATAN", v -> promptNew(false)), button("+ FOLDER", v -> promptNew(true)));
        row(button("TEMPLATE", v -> chooseTemplate()),
                button("PLUGINS", v -> startActivity(new Intent(this, TermuxLabExtensionsActivity.class))));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Cari judul atau isi catatan...");
        search.setTextSize(13);
        content.addView(search);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        updateSearch(results, "");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { updateSearch(results, s.toString()); }
            public void afterTextChanged(Editable s) { }
        });
    }

    private void updateSearch(LinearLayout host, String query) {
        host.removeAllViews();
        try {
            List<String> notes = vault.search(query);
            host.addView(label(notes.size() + " CATATAN", 11, AMBER));
            if (notes.isEmpty()) host.addView(label("Belum ada catatan. Buat catatan pertama.", 12, MUTED));
            int count = 0;
            for (String note : notes) {
                if (++count > 120) { host.addView(label("Hasil dibatasi 120 catatan.", 11, MUTED)); break; }
                TextView link = label("▸ " + note, 14, TEXT);
                link.setBackgroundColor(PANEL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, px(5));
                host.addView(link, lp);
                link.setOnClickListener(v -> openNote(note));
            }
        } catch (IOException error) { host.addView(label("Gagal membaca vault: " + error.getMessage(), 12, Color.RED)); }
    }

    private void promptNew(boolean folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(folder ? "Nama folder" : "Judul catatan (atau Folder/Judul)");
        new AlertDialog.Builder(this).setTitle(folder ? "Folder baru" : "Catatan baru")
                .setView(input).setNegativeButton("Batal", null)
                .setPositiveButton("Buat", (d,w) -> {
                    try {
                        String value = input.getText().toString();
                        if (folder) { vault.createFolder(value); showList(); }
                        else openNote(vault.create(value));
                    } catch (IOException error) { error("Tidak dapat membuat", error); }
                }).show();
    }

    private void chooseTemplate() {
        try {
            List<TermuxLabPluginStore.Template> templates = plugins.templates();
            if (templates.isEmpty()) { msg("Belum ada plugin template aktif. Buka PLUGINS."); return; }
            String[] names = new String[templates.size()];
            for (int i = 0; i < names.length; i++) names[i] = templates.get(i).title + " • " + templates.get(i).pluginId;
            new AlertDialog.Builder(this).setTitle("Buat catatan dari template")
                    .setItems(names, (dialog,index) -> {
                        TermuxLabPluginStore.Template template = templates.get(index);
                        EditText name = new EditText(this);
                        name.setSingleLine(true);
                        name.setText(template.title);
                        new AlertDialog.Builder(this).setTitle("Nama catatan")
                                .setView(name).setNegativeButton("Batal", null)
                                .setPositiveButton("Buat", (d,w) -> {
                                    try {
                                        String note = vault.create(name.getText().toString());
                                        vault.save(note, template.markdown, vault.fingerprint(note));
                                        openNote(note);
                                    } catch (IOException e) { error("Template gagal", e); }
                                }).show();
                    }).show();
        } catch (IOException e) { error("Plugin template gagal dibaca", e); }
    }

    private void openNote(String name) {
        if (dirty && !saveDraft(false)) return;
        handler.removeCallbacks(autosave);
        try {
            String body = vault.read(name);
            editingName = name;
            editingFingerprint = vault.fingerprint(name);
            dirty = false;
            content.removeAllViews();
            content.addView(label(name, 19, AMBER));
            row(button("SIMPAN", v -> saveDraft(true)), button("DAFTAR", v -> {
                if (saveDraft(true)) showList();
            }), button("HAPUS", v -> confirmTrash()));
            saveState = label("Tersimpan • otomatis menyimpan setelah berhenti mengetik", 11, MUTED);
            content.addView(saveState);
            editor = new EditText(this);
            editor.setTextColor(TEXT);
            editor.setHintTextColor(MUTED);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setTextSize(13);
            editor.setGravity(Gravity.TOP);
            editor.setMinHeight(px(400));
            editor.setBackgroundColor(PANEL);
            editor.setPadding(px(12), px(12), px(12), px(12));
            editor.setText(body);
            content.addView(editor, new LinearLayout.LayoutParams(-1, -2));
            editor.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    dirty = true;
                    saveState.setText("Belum tersimpan...");
                    handler.removeCallbacks(autosave);
                    handler.postDelayed(autosave, 1200);
                }
                public void afterTextChanged(Editable s) { }
            });
            showLinks(name, body);
        } catch (IOException error) { error("Catatan tidak bisa dibuka", error); }
    }

    private void showLinks(String name, String markdown) {
        content.addView(label("LINKS & BACKLINKS (diperbarui saat catatan dibuka)", 11, AMBER));
        for (String target : TermuxLabVaultStore.outgoing(markdown)) {
            TextView link = label("→ [[" + target + "]]", 12, TEXT);
            content.addView(link);
            link.setOnClickListener(v -> {
                if (dirty && !saveDraft(true)) return;
                try {
                    String candidate = target.endsWith(".md") ? target : target + ".md";
                    if (!vault.list().contains(candidate)) {
                        new AlertDialog.Builder(this).setMessage("Catatan belum ada. Buat '" + target + "'?")
                                .setNegativeButton("Batal", null)
                                .setPositiveButton("Buat", (d,w) -> {
                                    try { openNote(vault.create(target)); }
                                    catch (IOException e) { error("Gagal membuka link", e); }
                                }).show();
                    } else openNote(candidate);
                } catch (IOException e) { error("Gagal membuka link", e); }
            });
        }
        try {
            for (String back : vault.backlinks(name)) {
                TextView link = label("← " + back, 12, MUTED);
                content.addView(link);
                link.setOnClickListener(v -> openNote(back));
            }
        } catch (IOException error) { msg("Backlink gagal: " + error.getMessage()); }
    }

    private boolean saveDraft(boolean showToast) {
        handler.removeCallbacks(autosave);
        if (!dirty || editor == null || editingName == null) return true;
        try {
            editingFingerprint = vault.save(editingName, editor.getText().toString(), editingFingerprint);
            dirty = false;
            if (saveState != null) saveState.setText("Tersimpan");
            if (showToast) msg("Catatan tersimpan");
            return true;
        } catch (IOException error) {
            if (saveState != null) saveState.setText("GAGAL MENYIMPAN: " + error.getMessage());
            error("Catatan tidak tersimpan", error);
            return false;
        }
    }

    private void confirmTrash() {
        if (editingName == null) return;
        new AlertDialog.Builder(this).setTitle("Pindahkan ke Trash?")
                .setMessage("Catatan tidak dihapus permanen. File dipindahkan ke .trash dalam vault.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Pindahkan", (d,w) -> {
                    try {
                        handler.removeCallbacks(autosave);
                        dirty = false;
                        vault.trash(editingName);
                        showList();
                    } catch (IOException error) { error("Tidak bisa memindahkan", error); }
                }).show();
    }

    @Override public void onBackPressed() {
        if (editingName != null) {
            if (saveDraft(false)) showList();
        } else super.onBackPressed();
    }

    @Override protected void onStop() {
        if (dirty) saveDraft(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(autosave);
        super.onDestroy();
    }
}
