package com.termux.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.File;
import java.util.Locale;

public final class TermuxLabDashboardActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(8,8,12);
    private static final int PANEL = Color.rgb(18,18,26);
    private static final int CARD = Color.rgb(24,23,34);
    private static final int STROKE = Color.rgb(58,52,78);
    private static final int TEXT = Color.rgb(246,244,250);
    private static final int MUTED = Color.rgb(171,166,182);
    private static final int PURPLE = Color.rgb(165,112,255);
    private static final int GREEN = Color.rgb(78,218,143);
    private static final int ORANGE = Color.rgb(244,177,89);

    private TextView ramValue;
    private TextView storageValue;
    private TextView storageHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Termux Lab");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildDashboard());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    private View buildDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(26));
        scroll.addView(root);

        root.addView(text("Termux Lab", 32, TEXT, true));
        TextView subtitle = text("Mobile engineering cockpit", 16, MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(2), 0, dp(18));
        root.addView(subtitle, subLp);

        root.addView(hero());

        TextView mainLabel = section("AKSI UTAMA");
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(-1, -2);
        mainLp.setMargins(dp(4), dp(22), 0, dp(10));
        root.addView(mainLabel, mainLp);

        GridLayout mainGrid = new GridLayout(this);
        mainGrid.setColumnCount(2);
        root.addView(mainGrid, new LinearLayout.LayoutParams(-1, -2));

        addCard(mainGrid, ">_", "Terminal", "Shell Termux asli", this::openTerminal);
        addCard(mainGrid, "◉", "Ubuntu", "Masuk PRoot distro",
            () -> runInTerminal("Ubuntu", "exec proot-distro login ubuntu"));
        addCard(mainGrid, "AI", "Codex", "AI coding CLI",
            () -> runInTerminal("Codex", "command -v codex >/dev/null 2>&1 && exec codex || { echo 'Codex belum terpasang'; exec bash; }"));
        addCard(mainGrid, "C", "Claude", "Claude Code CLI",
            () -> runInTerminal("Claude", "command -v claude >/dev/null 2>&1 && exec claude || { echo 'Claude belum terpasang'; exec bash; }"));

        TextView devLabel = section("DEV TOOLS");
        LinearLayout.LayoutParams devLp = new LinearLayout.LayoutParams(-1, -2);
        devLp.setMargins(dp(4), dp(22), 0, dp(10));
        root.addView(devLabel, devLp);

        GridLayout devGrid = new GridLayout(this);
        devGrid.setColumnCount(2);
        root.addView(devGrid, new LinearLayout.LayoutParams(-1, -2));

        addSmall(devGrid, "V", "Neovim",
            () -> runInTerminal("Neovim", "command -v nvim >/dev/null 2>&1 && exec nvim || { echo 'Neovim belum terpasang'; exec bash; }"));
        addSmall(devGrid, "G", "Git status",
            () -> runInTerminal("Git", "git status; exec bash"));
        addSmall(devGrid, "Py", "Python",
            () -> runInTerminal("Python", "command -v python >/dev/null 2>&1 && exec python || exec python3"));
        addSmall(devGrid, "⬆", "Update",
            () -> runInTerminal("Update", "pkg update && pkg upgrade; exec bash"));

        TextView sysLabel = section("STATUS SISTEM");
        LinearLayout.LayoutParams sysLp = new LinearLayout.LayoutParams(-1, -2);
        sysLp.setMargins(dp(4), dp(22), 0, dp(10));
        root.addView(sysLabel, sysLp);
        root.addView(systemPanel());

        TextView infoLabel = section("INFO");
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.setMargins(dp(4), dp(22), 0, dp(10));
        root.addView(infoLabel, infoLp);
        root.addView(infoPanel());

        return scroll;
    }

    private View hero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(cardDrawable(PANEL, PURPLE, 1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(text(">_  Termux Lab", 22, TEXT, true),
            new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(text("v0.3", 14, PURPLE, true));
        hero.addView(top);

        TextView d = text("Termux, Ubuntu, AI CLI, dan dev tools dalam satu aplikasi.", 14, MUTED, false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(0, dp(8), 0, 0);
        hero.addView(d, dlp);
        return hero;
    }

    private View systemPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(cardDrawable(PANEL, STROKE, 1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout ram = metricBox("RAM");
        ramValue = text("...", 20, TEXT, true);
        ram.addView(ramValue);

        LinearLayout storage = metricBox("STORAGE");
        storageValue = text("...", 20, TEXT, true);
        storage.addView(storageValue);

        row.addView(ram, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.setMargins(dp(8), 0, 0, 0);
        row.addView(storage, slp);
        panel.addView(row);

        storageHint = text("Menghitung...", 13, MUTED, false);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(0, dp(14), 0, 0);
        panel.addView(storageHint, hlp);

        return panel;
    }

    private LinearLayout metricBox(String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(cardDrawable(CARD, STROKE, 1));
        box.addView(text(label, 12, PURPLE, true));
        return box;
    }

    private View infoPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(cardDrawable(PANEL, STROKE, 1));
        panel.addView(infoLine("Android", Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT));
        panel.addView(infoLine("Perangkat", Build.MANUFACTURER + " " + Build.MODEL));
        panel.addView(infoLine("Arsitektur", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown"));
        panel.addView(infoLine("Package", getPackageName()));
        return panel;
    }

    private TextView infoLine(String key, String value) {
        TextView v = text(key + "   •   " + value, 14, TEXT, false);
        v.setPadding(0, dp(7), 0, dp(7));
        return v;
    }

    private TextView section(String value) {
        return text(value, 13, PURPLE, true);
    }

    private void refreshStats() {
        if (ramValue == null || storageValue == null || storageHint == null) return;

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long usedRam = mi.totalMem - mi.availMem;
        ramValue.setText(human(usedRam) + " / " + human(mi.totalMem));

        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getTotalBytes();
        long free = stat.getAvailableBytes();
        long used = total - free;
        int percent = total > 0 ? (int)((used * 100L) / total) : 0;

        storageValue.setText(percent + "%");
        storageHint.setText(human(free) + " kosong dari " + human(total));
        storageHint.setTextColor(percent >= 90 ? ORANGE : MUTED);
    }

    private String human(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb);
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private void addCard(GridLayout grid, String icon, String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(cardDrawable(CARD, STROKE, 1));
        card.addView(text(icon, 23, PURPLE, true));

        TextView tv = text(title, 19, TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(0, dp(10), 0, dp(4));
        card.addView(tv, tlp);
        card.addView(text(subtitle, 13, MUTED, false));
        card.setOnClickListener(v -> action.run());

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(138);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(card, lp);
    }

    private void addSmall(GridLayout grid, String icon, String title, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(cardDrawable(CARD, STROKE, 1));

        card.addView(text(icon, 18, PURPLE, true));

        TextView tv = text(title, 15, TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(dp(10), 0, 0, 0);
        card.addView(tv, tlp);
        card.setOnClickListener(v -> action.run());

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(72);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(card, lp);
    }

    private void openTerminal() {
        startActivity(new Intent(this, TermuxActivity.class));
    }

    private void runInTerminal(String sessionName, String shellCommand) {
        String bash = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash";
        if (!new File(bash).exists()) {
            Toast.makeText(this, "Bootstrap Termux belum siap.", Toast.LENGTH_LONG).show();
            openTerminal();
            return;
        }

        Uri uri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(bash)
            .build();

        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, uri);
        intent.setClass(this, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-lc", shellCommand});
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TermuxConstants.TERMUX_HOME_DIR_PATH);
        intent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.TERMINAL_SESSION.getName());
        intent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, false);
        intent.putExtra(
            TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY)
        );
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, sessionName);
        intent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Termux Lab: " + sessionName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable cardDrawable(int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

