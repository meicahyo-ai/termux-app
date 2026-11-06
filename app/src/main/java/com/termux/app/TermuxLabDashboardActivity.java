package com.termux.app;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.SystemClock;
import android.util.Log;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.app.activities.SettingsActivity;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
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
    private final Map<String, TextView> sessionTabButtons = new LinkedHashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private LinearLayout contentHost;
    private ScrollView mainScroll;
    private LinearLayout aiComposerHost;
    private TermuxLabAiChatPanel aiChatPanel;
    private TextView headerStatus;
    private TextView ramValue;
    private TextView storageValue;
    private TextView loadValue;
    private TextView batteryValue;
    private File currentDir;
    private String activeSection = "Home";

    private TextView gitBranchValue;
    private TextView gitStateValue;
    private TextView gitCommitValue;
    private LinearLayout gitFilesHost;
    private TextView homeUbuntuValue;
    private TextView homeSessionsValue;
    private TextView homeCommitValue;
    private long lastCpuTotal = -1L;
    private long lastCpuIdle = -1L;

    private TerminalView embeddedTerminalView;
    private TerminalSession embeddedSession;
    private TextView embeddedTitle;
    private TextView embeddedState;
    private final HashMap<TerminalSession, TermuxSession> embeddedSessionWrappers = new HashMap<>();
    private final LinkedHashMap<String, TermuxSession> embeddedSessionsByName = new LinkedHashMap<>();
    private final EmbeddedTerminalClient embeddedClient = new EmbeddedTerminalClient();
    private String embeddedSessionName = "Shell";
    private String embeddedCommand = "exec bash -l";
    private String requestedEmbeddedName;
    private String requestedEmbeddedCommand;
    private int embeddedFontSize = 17;

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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(TOP);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        currentDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        setContentView(buildApp());
        showSection("Home");
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

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshTick);
        if (aiChatPanel != null) aiChatPanel.dispose();
        for (TerminalSession session : embeddedSessionWrappers.keySet().toArray(new TerminalSession[0])) {
            try {
                if (session != null && session.isRunning()) session.finishIfRunning();
            } catch (Exception ignored) {
            }
        }
        embeddedSessionWrappers.clear();
        embeddedSessionsByName.clear();
        super.onDestroy();
    }

    private View buildApp() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));

        View dividerTop = new View(this);
        dividerTop.setBackgroundColor(BORDER_SOFT);
        root.addView(dividerTop, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(9), dp(9), dp(9), dp(12));
        scroll.addView(contentHost, new ScrollView.LayoutParams(-1, -2));

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        aiComposerHost = new LinearLayout(this);
        aiComposerHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(aiComposerHost, new LinearLayout.LayoutParams(-1, -2));

        View dividerBottom = new View(this);
        dividerBottom.setBackgroundColor(BORDER_SOFT);
        root.addView(dividerBottom, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(buildNavBar(), new LinearLayout.LayoutParams(-1, dp(52)));
        root.requestApplyInsets();
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(TOP);

        TextView logo = text(">_", 20, AMBER, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(PANEL_2, AMBER, 1, 8));
        logo.setPadding(dp(8), dp(4), dp(8), dp(4));
        bar.addView(logo);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(9), 0, dp(6), 0);
        bar.addView(titles, titleLp);

        TextView title = text("Termux Lab // Cockpit", 14, TEXT, true);
        title.setSingleLine(true);
        titles.addView(title);
        TextView sub = text("engineering-lab • " + labVersionLabel(), 10, MUTED, false);
        sub.setSingleLine(true);
        titles.addView(sub);

        headerStatus = chip("● READY", GREEN);
        headerStatus.setOnClickListener(v -> showSection("Home"));
        bar.addView(headerStatus);
        logo.setOnClickListener(v -> showSection("Home"));
        titles.setOnClickListener(v -> showSection("Home"));
        return bar;
    }

    private View buildNavBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackgroundColor(TOP);

        addNav(row, "Explorer", "▣\nFiles");
        addNav(row, "Git", "⑂\nGit");
        addNav(row, "AI", "◇\nAI");
        addNav(row, "Ops", "⌁\nOps");
        addNav(row, "Terminal", ">_\nTerm");
        addNav(row, "Settings", "⚙\nSet");
        return row;
    }

    private void addNav(LinearLayout row, String key, String label) {
        TextView button = text(label, 9, MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setLines(2);
        button.setPadding(dp(2), dp(3), dp(2), dp(3));
        button.setBackground(round(PANEL, BORDER_SOFT, 1, 7));
        button.setOnClickListener(v -> showSection(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
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
        gitBranchValue = null;
        gitStateValue = null;
        gitCommitValue = null;
        gitFilesHost = null;
        homeUbuntuValue = null;
        homeSessionsValue = null;
        homeCommitValue = null;
        contentHost.removeAllViews();
        if (aiComposerHost != null) aiComposerHost.removeAllViews();

        switch (section) {
            case "Home":
                buildHomeSection();
                break;
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

    private void buildHomeSection() {
        contentHost.addView(sectionTitle("WORKSPACE", "engineering-lab • Ubuntu PRoot • tap header untuk Home"));

        LinearLayout hero = panel();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("engineering-lab", 18, TEXT, true));
        labels.addView(text(UBUNTU_REPO, 10, MUTED, false));
        titleRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        titleRow.addView(chip("● ACTIVE", GREEN));
        hero.addView(titleRow);

        LinearLayout healthRow = new LinearLayout(this);
        healthRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams healthLp = new LinearLayout.LayoutParams(-1, -2);
        healthLp.setMargins(0, dp(10), 0, dp(5));
        hero.addView(healthRow, healthLp);

        LinearLayout gitBox = compactMetric("GIT");
        gitStateValue = text("…", 12, GREEN, true);
        gitBox.addView(gitStateValue);
        healthRow.addView(gitBox, weightedMetricLp());

        LinearLayout ubuntuBox = compactMetric("UBUNTU");
        homeUbuntuValue = text("checking", 12, AMBER, true);
        ubuntuBox.addView(homeUbuntuValue);
        healthRow.addView(ubuntuBox, weightedMetricLp());

        LinearLayout sessionBox = compactMetric("SESSIONS");
        homeSessionsValue = text(countRunningEmbeddedSessions() + " live", 12, BLUE, true);
        sessionBox.addView(homeSessionsValue);
        healthRow.addView(sessionBox, weightedMetricLp());

        LinearLayout resourcesRow = new LinearLayout(this);
        resourcesRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout ramBox = compactMetric("RAM");
        ramValue = text("…", 12, GREEN, true);
        ramBox.addView(ramValue);
        resourcesRow.addView(ramBox, weightedMetricLp());

        LinearLayout storageBox = compactMetric("STORAGE");
        storageValue = text("…", 12, AMBER, true);
        storageValue.setMaxLines(2);
        storageBox.addView(storageValue);
        resourcesRow.addView(storageBox, weightedMetricLp());

        hero.addView(resourcesRow);

        LinearLayout commit = new LinearLayout(this);
        commit.setOrientation(LinearLayout.VERTICAL);
        commit.setPadding(dp(9), dp(8), dp(9), dp(8));
        commit.setBackground(round(Color.rgb(18, 16, 13), BORDER_SOFT, 1, 5));
        LinearLayout.LayoutParams commitLp = new LinearLayout.LayoutParams(-1, -2);
        commitLp.setMargins(0, dp(7), 0, dp(3));
        commit.setLayoutParams(commitLp);
        commit.addView(text("LAST COMMIT", 9, MUTED, true));
        homeCommitValue = text("loading…", 11, TEXT, true);
        homeCommitValue.setMaxLines(2);
        homeCommitValue.setEllipsize(TextUtils.TruncateAt.END);
        commit.addView(homeCommitValue);
        hero.addView(commit);

        hero.addView(actionBar(
            new Action("TERMINAL", () -> launchEmbedded("Shell", "cd ~/termux-lab-real 2>/dev/null || cd ~; exec bash -l")),
            new Action("CODEX", () -> launchEmbedded("Codex", ubuntuRepoInteractive("exec codex"))),
            new Action("CLAUDE", () -> launchEmbedded("Claude", ubuntuRepoInteractive("exec claude"))),
            new Action("REVIEW", () -> launchEmbedded("Codex Review", ubuntuRepoInteractive("codex review --uncommitted")))
        ));
        hero.addView(actionBar(
            new Action("KNOWLEDGE", () -> startActivity(new Intent(this, TermuxLabKnowledgeActivity.class))),
            new Action("PLUGINS", () -> startActivity(new Intent(this, TermuxLabExtensionsActivity.class)))
        ));
        contentHost.addView(hero, blockLp());

        LinearLayout sessions = panel();
        LinearLayout sessionsHeader = new LinearLayout(this);
        sessionsHeader.setOrientation(LinearLayout.HORIZONTAL);
        sessionsHeader.setGravity(Gravity.CENTER_VERTICAL);
        sessionsHeader.addView(text("LIVE SESSIONS", 11, AMBER, true), new LinearLayout.LayoutParams(0, -2, 1f));
        sessionsHeader.addView(chip(countRunningEmbeddedSessions() + " LIVE", countRunningEmbeddedSessions() > 0 ? GREEN : MUTED));
        sessions.addView(sessionsHeader);

        if (embeddedSessionsByName.isEmpty()) {
            TextView empty = text("Belum ada sesi embedded. Buka Shell, Codex, Claude, atau Ubuntu.", 11, MUTED, false);
            empty.setPadding(0, dp(8), 0, dp(4));
            sessions.addView(empty);
        } else {
            for (Map.Entry<String, TermuxSession> entry : embeddedSessionsByName.entrySet()) {
                TerminalSession session = entry.getValue() == null ? null : entry.getValue().getTerminalSession();
                boolean running = session != null && session.isRunning();
                String subtitle = running ? "running • tap untuk buka" : "stopped";
                sessions.addView(infoRow(entry.getKey(), subtitle, () -> {
                    if (running) {
                        embeddedSessionName = entry.getKey();
                        embeddedSession = session;
                        showSection("Terminal");
                    }
                }));
            }
        }
        contentHost.addView(sessions, blockLp());

        LinearLayout quick = panel();
        quick.addView(text("QUICK STATUS", 11, AMBER, true));
        TextView status = terminalBox("Memuat status workspace…");
        status.setMinHeight(dp(96));
        quick.addView(status);
        contentHost.addView(quick, blockLp());

        captureCommand("workspace", ubuntuCapture(
            "cd " + shellQuote(UBUNTU_REPO) + " 2>/dev/null || exit 2; " +
            "printf 'Branch : '; git branch --show-current; " +
            "printf 'State  : '; n=$(git status --porcelain | wc -l | tr -d ' '); [ \"$n\" = 0 ] && echo clean || echo \"$n changed\"; " +
            "printf 'Codex  : '; codex --version 2>/dev/null | head -1; " +
            "printf 'Claude : '; claude --version 2>/dev/null | head -1"), status);

        refreshHomeGitState();
        refreshHomeUbuntu();
        refreshHomeCommit();
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
        }), new Action("TERMINAL HERE", () -> launchEmbedded("Shell", "cd " + shellQuote(currentDir.getAbsolutePath()) + " && exec bash -l")),
            new Action("UBUNTU PROJECT", () -> launchEmbedded("Ubuntu", ubuntuInteractive("cd " + shellQuote(UBUNTU_REPO) + " && exec bash")))));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Cari di folder ini…");
        search.setTextSize(12);
        search.setPadding(dp(11), dp(7), dp(11), dp(7));
        search.setBackground(round(PANEL_2, BORDER_SOFT, 1, 5));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView clear = text("CLEAR", 9, MUTED, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(10), dp(9), dp(10), dp(9));
        clear.setBackground(round(PANEL_2, BORDER_SOFT, 1, 5));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-2, -2);
        clearLp.setMargins(dp(6), 0, 0, 0);
        searchRow.addView(clear, clearLp);
        contentHost.addView(searchRow, blockLp());

        TextView resultCount = text("", 9, MUTED, false);
        resultCount.setPadding(dp(4), 0, dp(4), dp(5));
        contentHost.addView(resultCount);

        LinearLayout filesPanel = panel();
        filesPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        contentHost.addView(filesPanel, blockLp());
        populateExplorerFiles(filesPanel, resultCount, "");

        clear.setOnClickListener(v -> {
            search.setText("");
            search.requestFocus();
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                populateExplorerFiles(filesPanel, resultCount, value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout workspace = panel();
        workspace.addView(text("WORKSPACE SHORTCUTS", 11, AMBER, true));
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

    private void populateExplorerFiles(LinearLayout filesPanel, TextView resultCount, String query) {
        filesPanel.removeAllViews();
        File[] files = currentDir.listFiles();
        if (files == null) {
            resultCount.setText("folder tidak dapat dibaca");
            filesPanel.addView(text("Tidak dapat membaca direktori ini.", 12, RED, false));
            return;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        int shown = 0;
        int matched = 0;

        for (File file : files) {
            if (!needle.isEmpty() && !file.getName().toLowerCase(Locale.US).contains(needle)) continue;
            matched++;
            if (shown >= 80) continue;
            filesPanel.addView(fileRow(file));
            shown++;
        }

        resultCount.setText(matched + (matched == 1 ? " item" : " items") +
            (needle.isEmpty() ? "" : " • filter: " + query.trim()));

        if (matched == 0) {
            TextView empty = text("Tidak ada item yang cocok.", 11, MUTED, false);
            empty.setPadding(dp(8), dp(8), dp(8), dp(8));
            filesPanel.addView(empty);
        } else if (matched > shown) {
            TextView more = text("… " + (matched - shown) + " item lain", 10, MUTED, false);
            more.setPadding(dp(8), dp(8), dp(8), dp(6));
            filesPanel.addView(more);
        }
    }

    private View fileRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(5), dp(9), dp(5));
        row.setBackground(round(Color.rgb(18, 16, 13), BORDER_SOFT, 1, 4));

        String icon = file.isDirectory() ? "▸" : "•";
        TextView iconView = text(icon, 14, file.isDirectory() ? AMBER : BLUE, true);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(20), -2));

        TextView name = text(file.getName(), 12, TEXT, false);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        String meta = file.isDirectory() ? "DIR" : human(file.length());
        TextView metaView = text(meta, 9, MUTED, true);
        metaView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(metaView, new LinearLayout.LayoutParams(dp(72), -2));

        row.setOnClickListener(v -> {
            if (file.isDirectory()) {
                currentDir = file;
                showSection("Explorer");
            } else {
                File parent = file.getParentFile();
                String parentPath = parent == null ? currentDir.getAbsolutePath() : parent.getAbsolutePath();
                launchEmbedded("Neovim", "cd " + shellQuote(parentPath) + " && exec nvim " + shellQuote(file.getName()));
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(38));
        lp.setMargins(0, 0, 0, dp(3));
        row.setLayoutParams(lp);
        return row;
    }

    private void buildGitSection() {
        contentHost.addView(sectionTitle("GIT / REVIEW", "Ubuntu • " + UBUNTU_REPO));

        LinearLayout header = panel();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("engineering-lab", 17, TEXT, true));
        labels.addView(text("workspace repository", 10, MUTED, false));
        top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(top);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(-1, -2);
        sumLp.setMargins(0, dp(10), 0, dp(5));
        header.addView(summary, sumLp);

        LinearLayout branchBox = compactMetric("BRANCH");
        gitBranchValue = text("…", 12, BLUE, true);
        branchBox.addView(gitBranchValue);
        summary.addView(branchBox, weightedMetricLp());

        LinearLayout stateBox = compactMetric("STATE");
        gitStateValue = text("…", 12, GREEN, true);
        stateBox.addView(gitStateValue);
        summary.addView(stateBox, weightedMetricLp());

        LinearLayout commitBox = new LinearLayout(this);
        commitBox.setOrientation(LinearLayout.VERTICAL);
        commitBox.setPadding(dp(9), dp(8), dp(9), dp(8));
        commitBox.setBackground(round(Color.rgb(18, 16, 13), BORDER_SOFT, 1, 5));
        commitBox.addView(text("LAST COMMIT", 9, MUTED, true));
        gitCommitValue = text("…", 11, AMBER, true);
        gitCommitValue.setMaxLines(2);
        gitCommitValue.setEllipsize(TextUtils.TruncateAt.END);
        commitBox.addView(gitCommitValue);
        header.addView(commitBox, blockLp());

        header.addView(actionBar(
            new Action("PERFORM REVIEW", () -> launchEmbedded("Codex Review", ubuntuRepoInteractive("codex review --uncommitted"))),
            new Action("REPO SHELL", () -> launchEmbedded("Ubuntu", ubuntuRepoInteractive("exec bash")))
        ));
        contentHost.addView(header, blockLp());

        TextView output = terminalBox("Memuat status repository…");
        output.setMinHeight(dp(135));
        contentHost.addView(output, blockLp());

        contentHost.addView(actionBar(
            new Action("STATUS", () -> captureUbuntuRepo("git status", "git status --short --branch", output)),
            new Action("DIFF", () -> captureUbuntuRepo("git diff", "git diff --stat && printf '\\n--- DIFF ---\\n' && git diff --color=never | head -160", output)),
            new Action("LOG", () -> captureUbuntuRepo("git log", "git log --oneline --decorate -12", output)),
            new Action("REFRESH", () -> { refreshGitSummary(); refreshGitFiles(); captureUbuntuRepo("git status", "git status --short --branch", output); })
        ));

        contentHost.addView(actionBar(
            new Action("PULL", () -> confirmAndRun("Git pull", "Jalankan git pull di engineering-lab?", () -> launchEmbedded("Git Pull", ubuntuRepoInteractive("git pull; exec bash")))),
            new Action("PUSH", () -> confirmAndRun("Git push", "Push commit lokal ke remote repository?", () -> launchEmbedded("Git Push", ubuntuRepoInteractive("git push; exec bash")))),
            new Action("SOURCE FORK", () -> launchEmbedded("Termux Lab source", "cd ~/termux-lab-real && git status; exec bash"))
        ));

        LinearLayout changes = panel();
        LinearLayout wtHeader = new LinearLayout(this);
        wtHeader.setOrientation(LinearLayout.HORIZONTAL);
        wtHeader.setGravity(Gravity.CENTER_VERTICAL);
        wtHeader.addView(text("WORKTREE", 11, AMBER, true), new LinearLayout.LayoutParams(0, -2, 1f));
        wtHeader.addView(chip("tap file → nvim", MUTED));
        changes.addView(wtHeader);
        gitFilesHost = new LinearLayout(this);
        gitFilesHost.setOrientation(LinearLayout.VERTICAL);
        changes.addView(gitFilesHost);
        contentHost.addView(changes, blockLp());

        refreshGitSummary();
        refreshGitFiles();
        captureUbuntuRepo("git status", "git status --short --branch", output);
    }

    private void buildAiSection() {
        if (aiChatPanel == null) {
            aiChatPanel = new TermuxLabAiChatPanel(this,
                () -> {
                    if (mainScroll != null && "AI".equals(activeSection)) {
                        mainScroll.post(() -> mainScroll.fullScroll(View.FOCUS_DOWN));
                    }
                });
        }
        contentHost.addView(aiChatPanel.contentView(), new LinearLayout.LayoutParams(-1, -2));
        aiComposerHost.addView(aiChatPanel.composerView(), new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildOpsSection() {
        contentHost.addView(sectionTitle("OBSERVABILITY / OPS", "device + Termux + Ubuntu"));

        LinearLayout metricRow1 = new LinearLayout(this);
        metricRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout ram = metric("RAM");
        ramValue = text("…", 16, GREEN, true);
        ram.addView(ramValue);
        metricRow1.addView(ram, weightedMetricLp());

        LinearLayout storage = metric("STORAGE");
        storageValue = text("…", 16, AMBER, true);
        storage.addView(storageValue);
        metricRow1.addView(storage, weightedMetricLp());
        contentHost.addView(metricRow1, blockLp());

        LinearLayout metricRow2 = new LinearLayout(this);
        metricRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout cpu = metric("CPU / CORES");
        loadValue = text(Runtime.getRuntime().availableProcessors() + " cores", 16, BLUE, true);
        cpu.addView(loadValue);
        metricRow2.addView(cpu, weightedMetricLp());

        LinearLayout battery = metric("BATTERY");
        batteryValue = text("…", 16, TEXT, true);
        battery.addView(batteryValue);
        metricRow2.addView(battery, weightedMetricLp());
        contentHost.addView(metricRow2, blockLp());

        TextView output = terminalBox("Pilih inspeksi sistem di bawah.");
        contentHost.addView(output, blockLp());

        contentHost.addView(actionBar(
            new Action("UBUNTU", () -> captureCommand("Ubuntu", "proot-distro list", output)),
            new Action("PROCESSES", () -> captureCommand("processes", "ps -A -o PID,PPID,STAT,NAME 2>&1 | head -80", output)),
            new Action("DISK", () -> captureCommand("disk", "df -h /storage/emulated/0 2>&1; printf '\\nHOME\\n'; du -sh $HOME 2>/dev/null || true", output)),
            new Action("REFRESH", this::refreshStats)
        ));

        contentHost.addView(actionBar(
            new Action("TERMUX UPDATE", () -> launchEmbedded("Termux Update", "pkg update; exec bash")),
            new Action("UBUNTU UPDATE", () -> launchEmbedded("Ubuntu Update", ubuntuInteractive("apt update; exec bash"))),
            new Action("TOP", () -> launchEmbedded("top", "exec top"))
        ));

        LinearLayout device = panel();
        device.addView(text("DEVICE", 11, AMBER, true));
        device.addView(text(Build.MANUFACTURER + " " + Build.MODEL + " • Android " + Build.VERSION.RELEASE, 12, TEXT, false));
        device.addView(text("ABI: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown") + " • " + Runtime.getRuntime().availableProcessors() + " CPU cores", 10, MUTED, false));
        contentHost.addView(device, blockLp());
    }

    private void buildTerminalSection() {
        contentHost.addView(sectionTitle("TERMINAL / WORKSPACE", "native TerminalView • persistent session tabs"));

        LinearLayout sessionBar = new LinearLayout(this);
        sessionBar.setOrientation(LinearLayout.HORIZONTAL);
        sessionBar.setGravity(Gravity.CENTER_VERTICAL);
        embeddedTitle = chip("● " + embeddedSessionName, AMBER);
        sessionBar.addView(embeddedTitle);
        embeddedState = text(embeddedSession != null && embeddedSession.isRunning() ? "running" : "idle", 10, MUTED, true);
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-2, -2);
        stateLp.setMargins(dp(8), 0, 0, 0);
        sessionBar.addView(embeddedState, stateLp);
        contentHost.addView(sessionBar, blockLp());

        contentHost.addView(buildSessionTabs());

        LinearLayout terminalPanel = panel();
        terminalPanel.setPadding(dp(4), dp(4), dp(4), dp(4));
        TerminalView terminalView = ensureEmbeddedTerminalView();
        if (terminalView.getParent() != null) {
            ((android.view.ViewGroup) terminalView.getParent()).removeView(terminalView);
        }
        terminalPanel.addView(terminalView, new LinearLayout.LayoutParams(-1, dp(420)));
        contentHost.addView(terminalPanel, blockLp());

        contentHost.addView(actionBar(
            new Action("ESC", () -> sendEmbedded("\u001b")),
            new Action("CTRL-C", () -> sendEmbedded("\u0003")),
            new Action("TAB", () -> sendEmbedded("\t")),
            new Action("←", () -> sendEmbedded("\u001b[D")),
            new Action("↑", () -> sendEmbedded("\u001b[A")),
            new Action("↓", () -> sendEmbedded("\u001b[B")),
            new Action("→", () -> sendEmbedded("\u001b[C")),
            new Action("CLEAR", () -> sendEmbedded("clear\r"))
        ));

        contentHost.addView(actionBar(
            new Action("FULLSCREEN", this::openTerminal),
            new Action("PERSISTENT SHELL", () -> runInTerminal("Shell", "exec bash -l")),
            new Action("TMUX DEV", () -> switchEmbeddedSession("tmux dev", "exec tmux new-session -A -s dev")),
            new Action("KILL CURRENT", this::killCurrentEmbeddedSession)
        ));

        if (requestedEmbeddedCommand != null) {
            String name = requestedEmbeddedName == null ? "Shell" : requestedEmbeddedName;
            String command = requestedEmbeddedCommand;
            requestedEmbeddedName = null;
            requestedEmbeddedCommand = null;
            switchEmbeddedSession(name, command);
        } else if (embeddedSession == null || !embeddedSession.isRunning()) {
            switchEmbeddedSession(embeddedSessionName, embeddedCommand);
        } else {
            terminalView.attachSession(embeddedSession);
            updateEmbeddedHeader();
        }
    }

    private void buildSettingsSection() {
        contentHost.addView(sectionTitle("SETTINGS", "Termux Lab + Termux core"));

        LinearLayout settings = panel();
        settings.addView(settingRow("Termux settings", "Buka SettingsActivity bawaan Termux", () -> startActivity(new Intent(this, SettingsActivity.class))));
        settings.addView(settingRow("Overlay permission", "Hanya dibutuhkan untuk sesi fullscreen/persistent. Terminal embedded tidak membutuhkannya.", this::openOverlaySettings));
        settings.addView(settingRow("termux.properties", "Edit konfigurasi terminal", () -> runInTerminal("termux.properties", "mkdir -p ~/.termux; touch ~/.termux/termux.properties; exec nvim ~/.termux/termux.properties")));
        settings.addView(settingRow("Source repository", "meicahyo-ai/termux-app", () -> openUri("https://github.com/meicahyo-ai/termux-app")));
        settings.addView(settingRow("Knowledge Vault", "Catatan Markdown mandiri milik Termux Lab",
            () -> startActivity(new Intent(this, TermuxLabKnowledgeActivity.class))));
        settings.addView(settingRow("Extension Manager", "Pasang plugin template Markdown dari ZIP lokal",
            () -> startActivity(new Intent(this, TermuxLabExtensionsActivity.class))));
        contentHost.addView(settings, blockLp());

        LinearLayout about = panel();
        about.addView(text("TERMUX LAB " + labVersionLabel(), 16, TEXT, true));
        about.addView(text("Mobile AI engineering cockpit: chat dan command planner dengan konfirmasi per-aksi, model CLI Codex/Claude, persistent sessions, Explorer, Git, dan Ops.", 12, MUTED, false));
        about.addView(text("Package: " + getPackageName(), 11, MUTED, false));
        contentHost.addView(about, blockLp());
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(8));
        box.addView(text(title, 18, TEXT, true));
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
            button.setPadding(dp(10), dp(8), dp(10), dp(8));
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

    private LinearLayout compactMetric(String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(7), dp(8), dp(7));
        box.setBackground(round(Color.rgb(18, 16, 13), BORDER_SOFT, 1, 5));
        box.addView(text(label, 9, MUTED, true));
        return box;
    }

    private LinearLayout.LayoutParams weightedMetricLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(0, 0, dp(5), 0);
        return lp;
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

    private void refreshSessionIndicators() {
        boolean ready = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash").exists();
        int live = countRunningEmbeddedSessions();

        if (headerStatus != null) {
            headerStatus.setText(ready ? ("● READY" + (live > 0 ? " • " + live : "")) : "● BOOTSTRAP");
            headerStatus.setTextColor(ready ? GREEN : RED);
        }

        if (homeSessionsValue != null) {
            homeSessionsValue.setText(live + " live");
            homeSessionsValue.setTextColor(live > 0 ? GREEN : MUTED);
        }
    }

    private void refreshStats() {
        refreshSessionIndicators();

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
            if ("Home".equals(activeSection)) {
                storageValue.setText(human(free) + " free\n" + percent + "% used");
            } else {
                storageValue.setText(percent + "% • " + human(free) + " free");
            }
            storageValue.setTextColor(percent >= 90 ? RED : AMBER);
        }

        if (loadValue != null) loadValue.setText(readCpuUsage());
        if (batteryValue != null) batteryValue.setText(readBattery());
    }

    private String readCpuUsage() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu ")) return Runtime.getRuntime().availableProcessors() + " cores";
            String[] p = line.trim().split("\\s+");
            long user = Long.parseLong(p[1]);
            long nice = Long.parseLong(p[2]);
            long system = Long.parseLong(p[3]);
            long idle = Long.parseLong(p[4]);
            long iowait = p.length > 5 ? Long.parseLong(p[5]) : 0L;
            long irq = p.length > 6 ? Long.parseLong(p[6]) : 0L;
            long softirq = p.length > 7 ? Long.parseLong(p[7]) : 0L;
            long steal = p.length > 8 ? Long.parseLong(p[8]) : 0L;
            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            String fallback = Runtime.getRuntime().availableProcessors() + " cores";
            if (lastCpuTotal < 0L || total <= lastCpuTotal) {
                lastCpuTotal = total;
                lastCpuIdle = idleAll;
                return fallback;
            }
            long totalDelta = total - lastCpuTotal;
            long idleDelta = idleAll - lastCpuIdle;
            lastCpuTotal = total;
            lastCpuIdle = idleAll;
            if (totalDelta <= 0L) return fallback;
            int usage = (int) Math.max(0, Math.min(100, ((totalDelta - idleDelta) * 100L) / totalDelta));
            return usage + "% • " + Runtime.getRuntime().availableProcessors() + "c";
        } catch (Exception e) {
            return Runtime.getRuntime().availableProcessors() + " cores";
        }
    }

    private int countRunningEmbeddedSessions() {
        int count = 0;
        for (TermuxSession wrapper : embeddedSessionsByName.values()) {
            TerminalSession session = wrapper == null ? null : wrapper.getTerminalSession();
            if (session != null && session.isRunning()) count++;
        }
        return count;
    }

    private void refreshHomeUbuntu() {
        if (homeUbuntuValue == null) return;
        new Thread(() -> {
            String out = runCapture(
                "proot-distro login ubuntu -- bash -lc " + shellQuote("printf ready"),
                2, 64
            ).trim();
            boolean ready = out.contains("ready");
            runOnUiThread(() -> {
                if (homeUbuntuValue == null) return;
                homeUbuntuValue.setText(ready ? "ready" : "offline");
                homeUbuntuValue.setTextColor(ready ? GREEN : RED);
            });
        }).start();
    }

    private void refreshHomeCommit() {
        if (homeCommitValue == null) return;
        new Thread(() -> {
            String out = runCapture(
                ubuntuCapture("cd " + shellQuote(UBUNTU_REPO) + " && git log -1 --format='%h • %s'"),
                4, 512
            ).trim();
            runOnUiThread(() -> {
                if (homeCommitValue != null) homeCommitValue.setText(out.isEmpty() ? "n/a" : out);
            });
        }).start();
    }

    private void refreshHomeGitState() {
        if (gitStateValue == null) return;
        new Thread(() -> {
            String out = runCapture(ubuntuCapture("cd " + shellQuote(UBUNTU_REPO) + " && git status --porcelain | wc -l | tr -d ' '"), 4, 256).trim();
            runOnUiThread(() -> {
                if (gitStateValue == null) return;
                boolean clean = "0".equals(out);
                gitStateValue.setText(clean ? "clean" : (out.isEmpty() ? "?" : out + " changed"));
                gitStateValue.setTextColor(clean ? GREEN : AMBER);
            });
        }).start();
    }

    private void refreshGitFiles() {
        if (gitFilesHost == null) return;
        gitFilesHost.removeAllViews();
        gitFilesHost.addView(text("memuat…", 10, MUTED, false));
        new Thread(() -> {
            String out = runCapture(ubuntuCapture("cd " + shellQuote(UBUNTU_REPO) + " && git status --porcelain"), 80, 12000);
            String[] lines = out.isEmpty() ? new String[0] : out.split("\\n");
            runOnUiThread(() -> {
                if (gitFilesHost == null) return;
                gitFilesHost.removeAllViews();
                if (lines.length == 0 || (lines.length == 1 && lines[0].trim().isEmpty())) {
                    TextView clean = text("✓ working tree clean", 11, GREEN, true);
                    clean.setPadding(0, dp(8), 0, dp(3));
                    gitFilesHost.addView(clean);
                    return;
                }
                int shown = 0;
                for (String line : lines) {
                    if (line.trim().isEmpty() || shown >= 30) continue;
                    String rawStatus = line.length() >= 2 ? line.substring(0, 2) : "??";
                    String status = rawStatus.trim();
                    String path = line.length() > 3 ? line.substring(3) : line.trim();
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(6), 0, dp(6));

                    String badgeText;
                    int badgeColor;
                    if ("??".equals(rawStatus)) {
                        badgeText = "NEW";
                        badgeColor = BLUE;
                    } else if (rawStatus.length() > 0 && rawStatus.charAt(0) != ' ' && rawStatus.charAt(0) != '?') {
                        badgeText = "STAGED";
                        badgeColor = GREEN;
                    } else if (rawStatus.length() > 1 && rawStatus.charAt(1) != ' ') {
                        badgeText = "MOD";
                        badgeColor = AMBER;
                    } else {
                        badgeText = status.isEmpty() ? "MOD" : status;
                        badgeColor = AMBER;
                    }
                    TextView badge = chip(badgeText, badgeColor);
                    row.addView(badge);
                    TextView file = text(path, 11, TEXT, false);
                    file.setSingleLine(true);
                    file.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, -2, 1f);
                    flp.setMargins(dp(8), 0, 0, 0);
                    row.addView(file, flp);
                    final String selectedPath = path;
                    row.setOnClickListener(v -> launchEmbedded("Neovim", ubuntuRepoInteractive("exec nvim " + shellQuote(selectedPath))));
                    gitFilesHost.addView(row);
                    shown++;
                }
            });
        }).start();
    }

    private View buildSessionTabs() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(6));
        hsv.addView(row);
        sessionTabButtons.clear();
        addSessionTab(row, "Shell", "exec bash -l");
        addSessionTab(row, "Ubuntu", ubuntuInteractive("exec bash"));
        addSessionTab(row, "Codex", ubuntuRepoInteractive("exec codex"));
        addSessionTab(row, "Claude", ubuntuRepoInteractive("exec claude"));
        addSessionTab(row, "Neovim", "exec nvim");
        updateSessionTabs();
        return hsv;
    }

    private void addSessionTab(LinearLayout row, String name, String command) {
        TextView tab = text(name, 10, MUTED, true);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(12), dp(8), dp(12), dp(8));
        tab.setOnClickListener(v -> switchEmbeddedSession(name, command));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        row.addView(tab, lp);
        sessionTabButtons.put(name, tab);
    }

    private void updateSessionTabs() {
        for (Map.Entry<String, TextView> entry : sessionTabButtons.entrySet()) {
            String name = entry.getKey();
            TextView tab = entry.getValue();
            TermuxSession wrapper = embeddedSessionsByName.get(name);
            TerminalSession session = wrapper == null ? null : wrapper.getTerminalSession();
            boolean running = session != null && session.isRunning();
            boolean active = name.equals(embeddedSessionName);
            tab.setText((running ? "● " : "○ ") + name);
            tab.setTextColor(active ? TEXT : (running ? GREEN : MUTED));
            tab.setBackground(round(active ? AMBER_DARK : PANEL_2, active ? AMBER : BORDER_SOFT, 1, 12));
        }

        // Session tabs, header READY badge, dan Home session count harus
        // membaca snapshot session map yang sama pada frame UI yang sama.
        refreshSessionIndicators();
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
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (level < 0 || scale <= 0) return "n/a";
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        return Math.round(level * 100f / scale) + "%" + (charging ? " ⚡" : "");
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

    private void refreshGitSummary() {
        if (gitBranchValue == null || gitStateValue == null || gitCommitValue == null) {
            refreshGitFiles();
            return;
        }
        String script = "cd " + shellQuote(UBUNTU_REPO) + " && " +
            "printf '%s\\n' \"$(git branch --show-current)\"; " +
            "printf '%s\\n' \"$(git status --porcelain | wc -l | tr -d ' ')\"; " +
            "git log -1 --format='%h %s'";
        new Thread(() -> {
            String out = runCapture(ubuntuCapture(script), 20, 4000);
            String[] lines = out.trim().split("\\n", 3);
            String branch = lines.length > 0 && !lines[0].isEmpty() ? lines[0] : "?";
            String count = lines.length > 1 && !lines[1].isEmpty() ? lines[1] : "?";
            String commit = lines.length > 2 && !lines[2].isEmpty() ? lines[2] : "?";
            runOnUiThread(() -> {
                if (gitBranchValue != null) gitBranchValue.setText(branch);
                if (gitStateValue != null) {
                    boolean clean = "0".equals(count);
                    gitStateValue.setText(clean ? "clean" : count + " changed");
                    gitStateValue.setTextColor(clean ? GREEN : AMBER);
                }
                if (gitCommitValue != null) gitCommitValue.setText(commit);
            });
        }).start();
    }

    private String runCapture(String command, int maxLines, int maxChars) {
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
            while ((line = br.readLine()) != null && lines < maxLines && sb.length() < maxChars) {
                sb.append(line).append('\n');
                lines++;
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String ubuntuInteractive(String innerCommand) {
        return "exec proot-distro login ubuntu -- bash -lc " + shellQuote(innerCommand);
    }

    private String ubuntuRepoInteractive(String command) {
        String inner = "cd " + shellQuote(UBUNTU_REPO) + " 2>/dev/null || { echo 'Repository " + UBUNTU_REPO + " tidak ditemukan'; exec bash; }; " + command;
        return ubuntuInteractive(inner);
    }

    private void launchEmbedded(String name, String command) {
        requestedEmbeddedName = name;
        requestedEmbeddedCommand = command;
        showSection("Terminal");
    }

    private TerminalView ensureEmbeddedTerminalView() {
        if (embeddedTerminalView == null) {
            embeddedTerminalView = new TerminalView(this, null);
            embeddedTerminalView.setTerminalViewClient(embeddedClient);
            embeddedTerminalView.setTextSize(embeddedFontSize);
            embeddedTerminalView.setFocusable(true);
            embeddedTerminalView.setFocusableInTouchMode(true);
            embeddedTerminalView.setBackgroundColor(Color.BLACK);
            embeddedTerminalView.setOnTouchListener((v, event) -> {
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }
        return embeddedTerminalView;
    }

    private void switchEmbeddedSession(String name, String command) {
        embeddedSessionName = name;
        embeddedCommand = command;

        TermuxSession existingWrapper = embeddedSessionsByName.get(name);
        if (existingWrapper != null) {
            TerminalSession existing = existingWrapper.getTerminalSession();
            if (existing != null && existing.isRunning()) {
                embeddedSession = existing;
                TerminalView existingView = ensureEmbeddedTerminalView();
                existingView.attachSession(existing);
                existingView.requestFocus();
                updateEmbeddedHeader();
                updateSessionTabs();
                return;
            }
            embeddedSessionsByName.remove(name);
        }

        ExecutionCommand execution = new ExecutionCommand(
            (int) (System.currentTimeMillis() & 0x7fffffff),
            TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash",
            new String[]{"-lc", command},
            null,
            TermuxConstants.TERMUX_HOME_DIR_PATH,
            Runner.TERMINAL_SESSION.getName(),
            false
        );
        execution.commandLabel = "Termux Lab: " + name;
        execution.shellName = "lab-" + name.toLowerCase(Locale.US).replace(' ', '-');
        execution.setShellCommandShellEnvironment = true;
        execution.terminalTranscriptRows = 3000;

        TermuxSession wrapper = TermuxSession.execute(
            this,
            execution,
            embeddedClient,
            termuxSession -> { },
            new TermuxShellEnvironment(),
            null,
            false
        );

        if (wrapper == null) {
            toast("Gagal membuat terminal embedded: " + name);
            return;
        }

        embeddedSession = wrapper.getTerminalSession();
        embeddedSessionWrappers.put(embeddedSession, wrapper);
        embeddedSessionsByName.put(name, wrapper);
        TerminalView view = ensureEmbeddedTerminalView();
        view.attachSession(embeddedSession);
        view.requestFocus();
        updateEmbeddedHeader();
        updateSessionTabs();
        view.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && view.hasFocus()) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private void killCurrentEmbeddedSession() {
        if (embeddedSession == null) {
            selectFallbackEmbeddedSession(null, true);
            return;
        }

        if (!embeddedSession.isRunning()) {
            TerminalSession staleSession = embeddedSession;
            String staleName = embeddedSessionName;
            TermuxSession staleWrapper = embeddedSessionWrappers.remove(staleSession);
            embeddedSessionsByName.remove(staleName);
            if (staleWrapper != null) {
                try { staleWrapper.finish(); } catch (Exception ignored) { }
            }
            embeddedSession = null;
            detachEmbeddedTerminalView();
            selectFallbackEmbeddedSession(staleName, true);
            return;
        }

        TerminalSession killedSession = embeddedSession;
        String killedName = embeddedSessionName;
        TermuxSession killedWrapper = embeddedSessionWrappers.remove(killedSession);

        // Putuskan TerminalView lebih dulu agar frame terakhir sesi yang mati
        // tidak tertinggal di cockpit saat proses dihentikan.
        detachEmbeddedTerminalView();
        embeddedSessionsByName.remove(killedName);
        embeddedSession = null;

        try {
            killedSession.finishIfRunning();
        } catch (Exception ignored) {
        }
        if (killedWrapper != null) {
            try { killedWrapper.finish(); } catch (Exception ignored) { }
        }

        selectFallbackEmbeddedSession(killedName, true);
    }

    private void detachEmbeddedTerminalView() {
        if (embeddedTerminalView == null) return;
        embeddedTerminalView.attachSession(null);
        embeddedTerminalView.clearFocus();
        embeddedTerminalView.invalidate();
    }

    private String findFallbackEmbeddedSessionName(@Nullable String excludedName) {
        String fallback = null;
        // LinkedHashMap mempertahankan urutan pembuatan. Dengan terus mengganti
        // nilai fallback, kita memilih sesi hidup yang dibuat paling akhir.
        for (Map.Entry<String, TermuxSession> entry : embeddedSessionsByName.entrySet()) {
            if (excludedName != null && excludedName.equals(entry.getKey())) continue;
            TermuxSession wrapper = entry.getValue();
            TerminalSession session = wrapper == null ? null : wrapper.getTerminalSession();
            if (session != null && session.isRunning()) fallback = entry.getKey();
        }
        return fallback;
    }

    private boolean attachExistingEmbeddedSession(String name) {
        if (name == null) return false;
        TermuxSession wrapper = embeddedSessionsByName.get(name);
        TerminalSession session = wrapper == null ? null : wrapper.getTerminalSession();
        if (session == null || !session.isRunning()) return false;

        embeddedSessionName = name;
        embeddedSession = session;
        TerminalView view = ensureEmbeddedTerminalView();
        view.attachSession(session);
        view.requestFocus();
        updateEmbeddedHeader();
        updateSessionTabs();
        return true;
    }

    private void selectFallbackEmbeddedSession(@Nullable String endedName, boolean announce) {
        String fallbackName = findFallbackEmbeddedSessionName(endedName);
        if (attachExistingEmbeddedSession(fallbackName)) {
            if (announce && endedName != null) toast(endedName + " dihentikan • pindah ke " + fallbackName);
            return;
        }

        // Tidak ada sesi lain yang hidup. Lepas TerminalView sepenuhnya dan
        // kembali ke state idle yang bersih, bukan menampilkan transcript mati.
        embeddedSession = null;
        embeddedSessionName = "Shell";
        embeddedCommand = "exec bash -l";
        detachEmbeddedTerminalView();
        updateEmbeddedHeader();
        updateSessionTabs();
        if (announce && endedName != null) toast(endedName + " dihentikan");
    }

    private void handleEmbeddedSessionFinished(@NonNull TerminalSession finishedSession) {
        TermuxSession wrapper = embeddedSessionWrappers.remove(finishedSession);
        String finishedName = null;
        if (wrapper != null) {
            for (Map.Entry<String, TermuxSession> entry : embeddedSessionsByName.entrySet()) {
                if (entry.getValue() == wrapper) {
                    finishedName = entry.getKey();
                    break;
                }
            }
            if (finishedName != null) embeddedSessionsByName.remove(finishedName);
            try { wrapper.finish(); } catch (Exception ignored) { }
        }

        if (finishedSession == embeddedSession) {
            detachEmbeddedTerminalView();
            embeddedSession = null;
            selectFallbackEmbeddedSession(finishedName, false);
        } else {
            updateEmbeddedHeader();
            updateSessionTabs();
        }
    }

    private void updateEmbeddedHeader() {
        boolean running = embeddedSession != null && embeddedSession.isRunning();
        if (embeddedTitle != null) embeddedTitle.setText((running ? "● " : "○ ") + embeddedSessionName);
        if (embeddedState != null) {
            int live = countRunningEmbeddedSessions();
            embeddedState.setText(running ? ("running • " + live + " live") : ("idle • " + live + " live"));
            embeddedState.setTextColor(running ? GREEN : MUTED);
        }
    }

    private void sendEmbedded(String text) {
        if (embeddedSession == null || !embeddedSession.isRunning()) {
            toast("Tidak ada terminal embedded yang aktif");
            return;
        }
        embeddedSession.write(text);
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

    private final class EmbeddedTerminalClient implements TerminalSessionClient, TerminalViewClient {
        @Override
        public void onTextChanged(@NonNull TerminalSession changedSession) {
            if (embeddedTerminalView != null && changedSession == embeddedSession) {
                embeddedTerminalView.post(embeddedTerminalView::onScreenUpdated);
            }
        }

        @Override
        public void onTitleChanged(@NonNull TerminalSession changedSession) {
            if (changedSession == embeddedSession) runOnUiThread(TermuxLabDashboardActivity.this::updateEmbeddedHeader);
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finishedSession) {
            // Session callbacks tidak diasumsikan datang dari UI thread.
            // Semua map + TerminalView lifecycle ditangani serial di main thread.
            runOnUiThread(() -> handleEmbeddedSessionFinished(finishedSession));
        }

        @Override
        public void onCopyTextToClipboard(@NonNull TerminalSession session, String value) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Termux Lab", value));
        }

        @Override
        public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
            if (session == null) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence value = clip.getItemAt(0).coerceToText(TermuxLabDashboardActivity.this);
            if (value != null) session.write(value.toString());
        }

        @Override public void onBell(@NonNull TerminalSession session) { }
        @Override public void onColorsChanged(@NonNull TerminalSession session) { if (embeddedTerminalView != null) embeddedTerminalView.postInvalidate(); }
        @Override public void onTerminalCursorStateChange(boolean state) { if (embeddedTerminalView != null) embeddedTerminalView.postInvalidate(); }
        @Override public void setTerminalShellPid(@NonNull TerminalSession session, int pid) { runOnUiThread(TermuxLabDashboardActivity.this::updateEmbeddedHeader); }
        @Override public Integer getTerminalCursorStyle() { return null; }

        @Override
        public float onScale(float scale) {
            if (scale > 1.1f) embeddedFontSize = Math.min(28, embeddedFontSize + 1);
            else if (scale < 0.9f) embeddedFontSize = Math.max(10, embeddedFontSize - 1);
            if (embeddedTerminalView != null) embeddedTerminalView.setTextSize(embeddedFontSize);
            return 1.0f;
        }

        @Override
        public void onSingleTapUp(MotionEvent e) {
            if (embeddedTerminalView == null) return;
            embeddedTerminalView.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(embeddedTerminalView, InputMethodManager.SHOW_IMPLICIT);
        }

        @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
        @Override public boolean shouldEnforceCharBasedInput() { return false; }
        @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
        @Override public boolean isTerminalViewSelected() { return embeddedTerminalView != null && embeddedTerminalView.hasFocus(); }
        @Override public void copyModeChanged(boolean copyMode) { }
        @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
        @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
        @Override public boolean onLongPress(MotionEvent event) { return false; }
        @Override public boolean readControlKey() { return false; }
        @Override public boolean readAltKey() { return false; }
        @Override public boolean readShiftKey() { return false; }
        @Override public boolean readFnKey() { return false; }
        @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
        @Override public void onEmulatorSet() { }

        @Override public void logError(String tag, String message) { Log.e(tag, message); }
        @Override public void logWarn(String tag, String message) { Log.w(tag, message); }
        @Override public void logInfo(String tag, String message) { Log.i(tag, message); }
        @Override public void logDebug(String tag, String message) { Log.d(tag, message); }
        @Override public void logVerbose(String tag, String message) { Log.v(tag, message); }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Log.e(tag, message, e); }
        @Override public void logStackTrace(String tag, Exception e) { Log.e(tag, "stacktrace", e); }
    }

    private static final class Action {
        final String label;
        final Runnable run;

        Action(String label, Runnable run) {
            this.label = label;
            this.run = run;
        }
    }

    private String labVersionLabel() {
        try {
            String version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;

            if (version == null) return "unknown";

            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern
                            .compile("lab\\.(\\d+\\.\\d+)")
                            .matcher(version);

            if (matcher.find()) {
                return "v0." + matcher.group(1);
            }

            return version;
        } catch (Exception error) {
            return "unknown";
        }
    }

}
