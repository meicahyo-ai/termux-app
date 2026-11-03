package com.termux.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.app.activities.SettingsActivity;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TermuxLabDashboardActivity extends AppCompatActivity {

    private static final int BG = Color.rgb(10, 9, 8);
    private static final int TOP = Color.rgb(19, 17, 14);
    private static final int PANEL = Color.rgb(24, 21, 17);
    private static final int PANEL_2 = Color.rgb(31, 27, 21);
    private static final int BORDER = Color.rgb(91, 74, 48);
    private static final int BORDER_SOFT = Color.rgb(58, 50, 39);
    private static final int TEXT = Color.rgb(246, 226, 188);
    private static final int MUTED = Color.rgb(180, 163, 132);
    private static final int AMBER = Color.rgb(238, 188, 101);
    private static final int AMBER_DARK = Color.rgb(92, 63, 26);
    private static final int GREEN = Color.rgb(126, 207, 112);
    private static final int RED = Color.rgb(236, 92, 78);
    private static final int BLUE = Color.rgb(101, 169, 226);

    private static final String UBUNTU_REPO = "/root/projects/engineering-lab";

    private final Map<String, TextView> navButtons = new LinkedHashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private LinearLayout contentHost;
    private TextView headerStatus;
    private TextView ramValue;
    private TextView storageValue;
    private TextView loadValue;
    private TextView batteryValue;
    private File currentDir;
    private String activeSection = "Explorer";

    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            refreshHandler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Termux Lab");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        currentDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        setContentView(buildApp());
        showSection("Explorer");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshTick);
        refreshHandler.post(refreshTick);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshTick);
        super.onPause();
    }

    private View buildApp() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));
        root.addView(buildNavBar(), new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(10), dp(10), dp(10), dp(18));
        scroll.addView(contentHost, new ScrollView.LayoutParams(-1, -2));

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        bar.setBackgroundColor(TOP);

        TextView logo = text(">_", 23, AMBER, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(PANEL_2, AMBER, 1, 8));
        logo.setPadding(dp(9), dp(5), dp(9), dp(5));
        bar.addView(logo);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(10), 0, dp(8), 0);
        bar.addView(titles, titleLp);

        titles.addView(text("Termux Lab // Engineering Cockpit", 17, TEXT, true));
        titles.addView(text("mobile workspace • v0.4", 11, MUTED, false));

        headerStatus = chip("● READY", GREEN);
        bar.addView(headerStatus);
        return bar;
    }

    private View buildNavBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(TOP);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(8));
        hsv.addView(row);

        addNav(row, "Explorer", "▣ Explorer");
        addNav(row, "Git", "⑂ Git");
        addNav(row, "AI", "◈ AI");
        addNav(row, "Ops", "⌁ Ops");
        addNav(row, "Terminal", ">_ Terminal");
        addNav(row, "Settings", "⚙ Settings");
        return hsv;
    }

    private void addNav(LinearLayout row, String key, String label) {
        TextView button = text(label, 13, MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(9), dp(12), dp(9));
        button.setBackground(round(PANEL, BORDER_SOFT, 1, 7));
        button.setOnClickListener(v -> showSection(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, lp);
        navButtons.put(key, button);
    }

    private void showSection(String section) {
        activeSection = section;
        for (Map.Entry<String, TextView> entry : navButtons.entrySet()) {
            boolean selected = entry.getKey().equals(section);
            entry.getValue().setTextColor(selected ? TEXT : MUTED);
            entry.getValue().setBackground(round(selected ? AMBER_DARK : PANEL,
                selected ? AMBER : BORDER_SOFT, 1, 7));
        }

        ramValue = null;
        storageValue = null;
        loadValue = null;
        batteryValue = null;
        contentHost.removeAllViews();

        switch (section) {
            case "Git":
                buildGitSection();
                break;
            case "AI":
                buildAiSection();
                break;
            case "Ops":
                buildOpsSection();
                break;
            case "Terminal":
                buildTerminalSection();
                break;
            case "Settings":
                buildSettingsSection();
                break;
            case "Explorer":
            default:
                buildExplorerSection();
                break;
        }
        refreshStats();
    }

    private void buildExplorerSection() {
        contentHost.addView(sectionTitle("EXPLORER", prettyPath(currentDir)));
        contentHost.addView(actionBar(new Action("HOME", () -> {
            currentDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
            showSection("Explorer");
        }), new Action("UP", () -> {
            File parent = currentDir.getParentFile();
            File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
            if (parent != null && currentDir.getAbsolutePath().startsWith(home.getAbsolutePath())) {
                currentDir = parent;
                if (!currentDir.getAbsolutePath().startsWith(home.getAbsolutePath())) currentDir = home;
            }
            showSection("Explorer");
        }), new Action("TERMINAL HERE", () -> runInTerminal("Workspace", "cd " + shellQuote(currentDir.getAbsolutePath()) + " && exec bash")),
            new Action("UBUNTU", () -> runInTerminal("Ubuntu", "exec proot-distro login ubuntu"))));

        LinearLayout filesPanel = panel();
        File[] files = currentDir.listFiles();
        if (files == null) {
            filesPanel.addView(text("Tidak dapat membaca direktori ini.", 13, RED, false));
        } else {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            int shown = 0;
            for (File file : files) {
                if (shown >= 24) break;
                filesPanel.addView(fileRow(file));
                shown++;
            }
            if (files.length > shown) {
                TextView more = text("… " + (files.length - shown) + " item lain tidak ditampilkan", 12, MUTED, false);
                more.setPadding(dp(10), dp(10), dp(10), dp(8));
                filesPanel.addView(more);
            }
        }
        contentHost.addView(filesPanel, blockLp());

        LinearLayout workspace = panel();
        workspace.addView(text("WORKSPACE SHORTCUTS", 12, AMBER, true));
        workspace.addView(infoRow("Termux source", "~/termux-lab-real", () -> {
            File f = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/termux-lab-real");
            if (f.isDirectory()) {
                currentDir = f;
                showSection("Explorer");
            } else {
                toast("~/termux-lab-real tidak ditemukan");
            }
        }));
        workspace.addView(infoRow("Ubuntu project", UBUNTU_REPO, () -> runUbuntuRepo("Project", "exec bash")));
        contentHost.addView(workspace, blockLp());
    }

    private View fileRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setBackground(round(PANEL_2, BORDER_SOFT, 1, 5));

        String icon = file.isDirectory() ? "▸" : "•";
        TextView iconView = text(icon, 16, file.isDirectory() ? AMBER : BLUE, true);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(22), -2));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(file.getName(), 13, TEXT, false));
        labels.addView(text(file.isDirectory() ? "folder" : human(file.length()), 10, MUTED, false));

        row.setOnClickListener(v -> {
            if (file.isDirectory()) {
                currentDir = file;
                showSection("Explorer");
            } else {
                File parent = file.getParentFile();
                String parentPath = parent == null ? currentDir.getAbsolutePath() : parent.getAbsolutePath();
                runInTerminal("Edit " + file.getName(), "cd " + shellQuote(parentPath) + " && exec nvim " + shellQuote(file.getName()));
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(5));
        row.setLayoutParams(lp);
        return row;
    }

    private void buildGitSection() {
        contentHost.addView(sectionTitle("GIT / REVIEW", "Ubuntu • " + UBUNTU_REPO));

        LinearLayout header = panel();
        header.addView(text("engineering-lab", 18, TEXT, true));
        header.addView(text("Repository kerja utama di Ubuntu PRoot", 11, MUTED, false));
        header.addView(actionBar(
            new Action("PERFORM REVIEW", () -> runUbuntuRepo("Codex Review", "codex review --uncommitted; exec bash")),
            new Action("OPEN REPO", () -> runUbuntuRepo("engineering-lab", "exec bash"))
        ));
        contentHost.addView(header, blockLp());

        TextView output = terminalBox("Memuat status repository…");
        contentHost.addView(output, blockLp());

        contentHost.addView(actionBar(
            new Action("STATUS", () -> captureUbuntuRepo("git status", "git status --short --branch", output)),
            new Action("DIFF", () -> captureUbuntuRepo("git diff", "git diff --stat && printf '\\n--- DIFF ---\\n' && git diff --color=never | head -160", output)),
            new Action("LOG", () -> captureUbuntuRepo("git log", "git log --oneline --decorate -12", output)),
            new Action("REFRESH", () -> captureUbuntuRepo("git status", "git status --short --branch", output))
        ));

        contentHost.addView(actionBar(
            new Action("PULL", () -> confirmAndRun("Git pull", "Jalankan git pull di engineering-lab?", () -> runUbuntuRepo("Git Pull", "git pull; exec bash"))),
            new Action("PUSH", () -> confirmAndRun("Git push", "Push commit lokal ke remote repository?", () -> runUbuntuRepo("Git Push", "git push; exec bash"))),
            new Action("SOURCE FORK", () -> runInTerminal("Termux Lab source", "cd ~/termux-lab-real && git status; exec bash"))
        ));

        captureUbuntuRepo("git status", "printf 'BRANCH: '; git branch --show-current; printf '\\n'; git status --short --branch; printf '\\nLAST COMMIT\\n'; git log -1 --oneline --decorate", output);
    }

    private void buildAiSection() {
        contentHost.addView(sectionTitle("AI CONTROL CENTER", "Ubuntu agents • project-aware"));

        LinearLayout ai = panel();
        ai.addView(toolRow("OpenAI Codex", "coding agent / review", GREEN,
            () -> runUbuntuRepo("Codex", "exec codex")));
        ai.addView(toolRow("Claude Code", "coding agent / workspace", AMBER,
            () -> runUbuntuRepo("Claude", "exec claude")));
        ai.addView(toolRow("Codex Review", "review perubahan Git saat ini", BLUE,
            () -> runUbuntuRepo("Codex Review", "codex review --uncommitted; exec bash")));
        ai.addView(toolRow("OpenCodeReview", "cek instalasi OCR CLI", MUTED,
            () -> runInUbuntu("OpenCodeReview", "if command -v ocr >/dev/null 2>&1; then ocr --help; else echo 'ocr tidak ditemukan'; fi; exec bash")));
        contentHost.addView(ai, blockLp());

        TextView status = terminalBox("Mengecek AI CLI…");
        contentHost.addView(status, blockLp());
        contentHost.addView(actionBar(
            new Action("REFRESH STATUS", () -> captureCommand("AI status", ubuntuCapture(
                "printf 'Codex : '; codex --version 2>&1 || true; " +
                "printf 'Claude: '; claude --version 2>&1 || true; " +
                "printf 'OCR   : '; if command -v ocr >/dev/null 2>&1; then ocr --help 2>/dev/null | head -1; else echo 'not found'; fi"), status)),
            new Action("UBUNTU SHELL", () -> runInTerminal("Ubuntu", "exec proot-distro login ubuntu"))
        ));
        captureCommand("AI status", ubuntuCapture(
            "printf 'Codex : '; codex --version 2>&1 || true; " +
            "printf 'Claude: '; claude --version 2>&1 || true; " +
            "printf 'OCR   : '; if command -v ocr >/dev/null 2>&1; then command -v ocr; else echo 'not found'; fi"), status);
    }

    private void buildOpsSection() {
        contentHost.addView(sectionTitle("OBSERVABILITY / OPS", "device + Termux + Ubuntu"));

        HorizontalScrollView metricsScroll = new HorizontalScrollView(this);
        metricsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metricsScroll.addView(metrics);

        LinearLayout ram = metric("RAM");
        ramValue = text("…", 18, GREEN, true);
        ram.addView(ramValue);
        metrics.addView(ram, metricLp());

        LinearLayout storage = metric("STORAGE");
        storageValue = text("…", 18, AMBER, true);
        storage.addView(storageValue);
        metrics.addView(storage, metricLp());

        LinearLayout load = metric("LOAD");
        loadValue = text("…", 18, BLUE, true);
        load.addView(loadValue);
        metrics.addView(load, metricLp());

        LinearLayout battery = metric("BATTERY");
        batteryValue = text("…", 18, TEXT, true);
        battery.addView(batteryValue);
        metrics.addView(battery, metricLp());

        contentHost.addView(metricsScroll, blockLp());

        TextView output = terminalBox("Pilih inspeksi sistem di bawah.");
        contentHost.addView(output, blockLp());

        contentHost.addView(actionBar(
            new Action("UBUNTU", () -> captureCommand("proot-distro list", "proot-distro list", output)),
            new Action("PROCESSES", () -> captureCommand("processes", "ps -A -o PID,PPID,STAT,NAME 2>&1 | head -80", output)),
            new Action("DISK", () -> captureCommand("disk", "df -h /storage/emulated/0 2>&1; printf '\\nHOME\\n'; du -sh $HOME 2>/dev/null || true", output)),
            new Action("REFRESH", this::refreshStats)
        ));

        contentHost.addView(actionBar(
            new Action("TERMUX UPDATE", () -> runInTerminal("Termux Update", "pkg update; exec bash")),
            new Action("UBUNTU UPDATE", () -> runInUbuntu("Ubuntu Update", "apt update; exec bash")),
            new Action("TOP", () -> runInTerminal("top", "exec top"))
        ));

        LinearLayout device = panel();
        device.addView(text("DEVICE", 12, AMBER, true));
        device.addView(text(Build.MANUFACTURER + " " + Build.MODEL + " • Android " + Build.VERSION.RELEASE, 13, TEXT, false));
        device.addView(text("ABI: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown"), 11, MUTED, false));
        contentHost.addView(device, blockLp());
    }

    private void buildTerminalSection() {
        contentHost.addView(sectionTitle("TERMINAL / SESSIONS", "Termux native + Ubuntu PRoot"));

        LinearLayout terminal = panel();
        terminal.addView(text("$ session control", 14, AMBER, true));
        TextView preview = terminalBox(
            "Termux Lab session router\n\n" +
            "• Native Termux shell\n" +
            "• Ubuntu 24.04 PRoot\n" +
            "• Codex / Claude inside Ubuntu\n" +
            "• tmux persistent session\n\n" +
            "Pilih sesi untuk membuka terminal asli Termux.");
        terminal.addView(preview);
        contentHost.addView(terminal, blockLp());

        contentHost.addView(actionBar(
            new Action("CURRENT TERMINAL", this::openTerminal),
            new Action("NEW SHELL", () -> runInTerminal("Shell", "exec bash -l")),
            new Action("UBUNTU", () -> runInTerminal("Ubuntu", "exec proot-distro login ubuntu"))
        ));
        contentHost.addView(actionBar(
            new Action("CODEX", () -> runUbuntuRepo("Codex", "exec codex")),
            new Action("CLAUDE", () -> runUbuntuRepo("Claude", "exec claude")),
            new Action("NEOVIM", () -> runInTerminal("Neovim", "exec nvim")),
            new Action("TMUX DEV", () -> runInTerminal("tmux dev", "exec tmux new-session -A -s dev"))
        ));
    }

    private void buildSettingsSection() {
        contentHost.addView(sectionTitle("SETTINGS", "Termux Lab + Termux core"));

        LinearLayout settings = panel();
        settings.addView(settingRow("Termux settings", "Buka SettingsActivity bawaan Termux", () -> startActivity(new Intent(this, SettingsActivity.class))));
        settings.addView(settingRow("Overlay permission", "Diperlukan Android 10+ untuk membuka sesi dari service", this::openOverlaySettings));
        settings.addView(settingRow("termux.properties", "Edit konfigurasi terminal", () -> runInTerminal("termux.properties", "mkdir -p ~/.termux; touch ~/.termux/termux.properties; exec nvim ~/.termux/termux.properties")));
        settings.addView(settingRow("Source repository", "meicahyo-ai/termux-app", () -> openUri("https://github.com/meicahyo-ai/termux-app")));
        contentHost.addView(settings, blockLp());

        LinearLayout about = panel();
        about.addView(text("TERMUX LAB v0.4", 16, TEXT, true));
        about.addView(text("UI workbench bergaya engineering console. Runtime terminal tetap memakai Termux core agar kompatibilitas shell, Ubuntu PRoot, dan sesi yang sudah ada tidak rusak.", 12, MUTED, false));
        about.addView(text("Package: " + getPackageName(), 11, MUTED, false));
        contentHost.addView(about, blockLp());
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(8));
        box.addView(text(title, 19, TEXT, true));
        box.addView(text(subtitle, 11, MUTED, false));
        return box;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(round(PANEL, BORDER, 1, 7));
        return panel;
    }

    private View actionBar(Action... actions) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));
        hsv.addView(row);
        for (Action action : actions) {
            TextView button = text(action.label, 11, TEXT, true);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(11), dp(9), dp(11), dp(9));
            button.setBackground(round(PANEL_2, BORDER, 1, 5));
            button.setOnClickListener(v -> action.run.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(6), 0);
            row.addView(button, lp);
        }
        return hsv;
    }

    private TextView terminalBox(String value) {
        TextView box = text(value, 11, TEXT, false);
        box.setTypeface(Typeface.MONOSPACE);
        box.setTextIsSelectable(true);
        box.setPadding(dp(11), dp(11), dp(11), dp(11));
        box.setBackground(round(Color.rgb(8, 8, 7), BORDER_SOFT, 1, 5));
        box.setMinHeight(dp(120));
        return box;
    }

    private View toolRow(String name, String subtitle, int color, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(round(PANEL_2, BORDER_SOFT, 1, 5));

        TextView dot = text("●", 15, color, true);
        row.addView(dot, new LinearLayout.LayoutParams(dp(25), -2));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(name, 14, TEXT, true));
        labels.addView(text(subtitle, 10, MUTED, false));

        TextView open = chip("OPEN", color);
        row.addView(open);
        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(lp);
        return row;
    }

    private View settingRow(String name, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(11), dp(10), dp(11));
        row.setBackground(round(PANEL_2, BORDER_SOFT, 1, 5));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(name, 13, TEXT, true));
        labels.addView(text(subtitle, 10, MUTED, false));
        row.addView(text("›", 22, AMBER, true));
        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(lp);
        return row;
    }

    private View infoRow(String name, String subtitle, Runnable action) {
        return settingRow(name, subtitle, action);
    }

    private LinearLayout metric(String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(11), dp(10), dp(11), dp(10));
        box.setBackground(round(PANEL, BORDER, 1, 6));
        box.addView(text(label, 10, MUTED, true));
        return box;
    }

    private LinearLayout.LayoutParams metricLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(145), -2);
        lp.setMargins(0, 0, dp(7), 0);
        return lp;
    }

    private LinearLayout.LayoutParams blockLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(9));
        return lp;
    }

    private TextView chip(String value, int color) {
        TextView chip = text(value, 10, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(5), dp(8), dp(5));
        chip.setBackground(round(PANEL_2, color, 1, 12));
        return chip;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private void refreshStats() {
        if (headerStatus != null) {
            boolean ready = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash").exists();
            headerStatus.setText(ready ? "● READY" : "● BOOTSTRAP");
            headerStatus.setTextColor(ready ? GREEN : RED);
        }

        if (ramValue != null) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long used = mi.totalMem - mi.availMem;
            ramValue.setText(human(used) + " / " + human(mi.totalMem));
        }

        if (storageValue != null) {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            int percent = total > 0 ? (int) (((total - free) * 100L) / total) : 0;
            storageValue.setText(percent + "% • " + human(free) + " free");
            storageValue.setTextColor(percent >= 90 ? RED : AMBER);
        }

        if (loadValue != null) loadValue.setText(readLoadAverage());
        if (batteryValue != null) batteryValue.setText(readBattery());
    }

    private String readLoadAverage() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String line = br.readLine();
            if (line == null) return "n/a";
            String[] parts = line.trim().split("\\s+");
            return parts.length >= 3 ? parts[0] + " / " + parts[1] + " / " + parts[2] : line;
        } catch (Exception e) {
            return "n/a";
        }
    }

    private String readBattery() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return "n/a";
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return "n/a";
        return Math.round(level * 100f / scale) + "%";
    }

    private String human(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb);
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.0f MB", mb);
    }

    private String prettyPath(File file) {
        String path = file.getAbsolutePath();
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        if (path.equals(home)) return "~";
        if (path.startsWith(home + "/")) return "~" + path.substring(home.length());
        return path;
    }

    private void captureUbuntuRepo(String label, String command, TextView output) {
        String script = "if [ -d " + shellQuote(UBUNTU_REPO + "/.git") + " ]; then cd " + shellQuote(UBUNTU_REPO) + " && " + command + "; else echo 'Repository " + UBUNTU_REPO + " tidak ditemukan'; fi";
        captureCommand(label, ubuntuCapture(script), output);
    }

    private String ubuntuCapture(String command) {
        return "proot-distro login ubuntu -- bash -lc " + shellQuote(command);
    }

    private void captureCommand(String label, String command, TextView target) {
        target.setText("$ " + label + "\n\nmenjalankan…");
        new Thread(() -> {
            String result;
            try {
                String bash = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash";
                ProcessBuilder pb = new ProcessBuilder(bash, "-lc", command);
                pb.directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
                Map<String, String> env = pb.environment();
                env.put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
                env.put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
                env.put("PATH", TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin:/system/bin:/system/xbin");
                env.put("TMPDIR", TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/tmp");
                File preload = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/lib/libtermux-exec-ld-preload.so");
                if (preload.exists()) env.put("LD_PRELOAD", preload.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                int lines = 0;
                while ((line = br.readLine()) != null && lines < 180 && sb.length() < 30000) {
                    sb.append(line).append('\n');
                    lines++;
                }
                int exit = process.waitFor();
                if (lines >= 180 || sb.length() >= 30000) sb.append("\n… output dipotong …\n");
                sb.append("\n[exit ").append(exit).append(']');
                result = "$ " + label + "\n\n" + sb;
            } catch (Exception e) {
                result = "$ " + label + "\n\nERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            String finalResult = result;
            runOnUiThread(() -> target.setText(finalResult));
        }).start();
    }

    private void runUbuntuRepo(String sessionName, String command) {
        String inner = "cd " + shellQuote(UBUNTU_REPO) + " 2>/dev/null || { echo 'Repository " + UBUNTU_REPO + " tidak ditemukan'; exec bash; }; " + command;
        runInTerminal(sessionName, "exec proot-distro login ubuntu -- bash -lc " + shellQuote(inner));
    }

    private void runInUbuntu(String sessionName, String command) {
        runInTerminal(sessionName, "exec proot-distro login ubuntu -- bash -lc " + shellQuote(command));
    }

    private void openTerminal() {
        startActivity(new Intent(this, TermuxActivity.class));
    }

    private void runInTerminal(String sessionName, String shellCommand) {
        String bash = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash";
        if (!new File(bash).exists()) {
            toast("Bootstrap Termux belum siap");
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
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, sessionName);
        intent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Termux Lab: " + sessionName);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } catch (Exception e) {
            toast("Gagal membuka sesi: " + e.getMessage());
        }
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            toast("Tidak dapat membuka pengaturan overlay");
        }
    }

    private void openUri(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception e) {
            toast("Tidak ada aplikasi untuk membuka tautan");
        }
    }

    private void confirmAndRun(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Jalankan", (dialog, which) -> action.run())
            .show();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Action {
        final String label;
        final Runnable run;

        Action(String label, Runnable run) {
            this.label = label;
            this.run = run;
        }
    }
}

