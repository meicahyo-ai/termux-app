package com.termux.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Plugin installer and manager. Only declarative template packs are supported. */
public final class TermuxLabExtensionsActivity extends AppCompatActivity {
    private static final int REQUEST_PLUGIN_ZIP = 4184;
    private static final int BG = Color.rgb(10, 9, 8);
    private static final int TEXT = Color.rgb(245, 226, 191);
    private static final int AMBER = Color.rgb(238, 188, 101);
    private static final int MUTED = Color.rgb(180, 163, 132);
    private TermuxLabPluginStore plugins;
    private LinearLayout content;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        try { plugins = new TermuxLabPluginStore(new File(TermuxConstants.TERMUX_HOME_DIR_PATH)); }
        catch (IOException e) {
            new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("Tutup",(d,w) -> finish()).show();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        TextView heading = text("TERMUX LAB / EXTENSIONS", 19, AMBER);
        root.addView(heading);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(12),px(8),px(12),px(12));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        showList();
    }

    private int px(int dp) { return (int)(dp*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setPadding(px(10),px(10),px(10),px(10));
        return label;
    }
    private Button button(String value, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setOnClickListener(listener);
        return b;
    }
    private void row(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) row.addView(b, new LinearLayout.LayoutParams(0,-2,1));
        content.addView(row);
    }
    private void error(Exception e) {
        new AlertDialog.Builder(this).setTitle("Plugin gagal")
                .setMessage(e.getMessage()).setPositiveButton("Tutup",null).show();
    }

    private void showList() {
        content.removeAllViews();
        content.addView(text("Plugin Manager",20,TEXT));
        content.addView(text("Plugin mandiri Termux Lab. Versi awal mendukung plugin template Markdown berbasis ZIP. " +
                "Kode dari plugin pihak ketiga TIDAK dieksekusi.",12,MUTED));
        row(button("INSTALL ZIP", v -> selectPlugin()),button("KEMBALI", v -> finish()));
        content.addView(text("PLUGIN TERPASANG",12,AMBER));
        if (plugins.list().isEmpty()) content.addView(text("Belum ada plugin. Instal paket template Markdown (.zip).",13,MUTED));
        for (TermuxLabPluginStore.Pack pack : plugins.list()) {
            content.addView(text(pack.name + " • v" + pack.version + "\n" + pack.id + " • " + (pack.enabled ? "AKTIF" : "NONAKTIF"),13,TEXT));
            row(button(pack.enabled ? "NONAKTIFKAN" : "AKTIFKAN", v -> {
                        try { plugins.setEnabled(pack.id,!pack.enabled); showList(); }
                        catch (IOException e) { error(e); }
                    }),
                    button("HAPUS", v -> new AlertDialog.Builder(this)
                        .setTitle("Hapus plugin " + pack.name + "?")
                        .setMessage("Hanya folder plugin ini yang dihapus. Catatan pengguna tidak dihapus.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus", (d,w) -> {
                            try { plugins.uninstall(pack.id); showList(); }
                            catch (IOException e) { error(e); }
                        }).show()));
        }
        content.addView(text("Batas keamanan: plugin hanya berupa metadata dan template. " +
                "Tidak ada akses otomatis ke Shell, Git, AI, jaringan, atau sistem Android.",11,MUTED));
    }

    private void selectPlugin() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent,REQUEST_PLUGIN_ZIP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode != REQUEST_PLUGIN_ZIP || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            TermuxLabPluginStore.Pack installed = plugins.install(stream);
            Toast.makeText(this,"Plugin terpasang: " + installed.name,Toast.LENGTH_LONG).show();
            showList();
        } catch (Exception e) { error(e); }
    }
}
