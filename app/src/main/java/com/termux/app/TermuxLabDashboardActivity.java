package com.termux.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

public final class TermuxLabDashboardActivity extends AppCompatActivity {

    private static final int BG = Color.rgb(7, 7, 11);
    private static final int CARD = Color.rgb(24, 23, 34);
    private static final int CARD_STROKE = Color.rgb(55, 50, 73);
    private static final int TEXT = Color.rgb(244, 242, 248);
    private static final int MUTED = Color.rgb(170, 165, 180);
    private static final int PURPLE = Color.rgb(166, 111, 255);
    private static final int GREEN = Color.rgb(72, 215, 137);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Termux Lab");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildDashboard());
    }

    private View buildDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(root);

        root.addView(text("Termux Lab", 34, TEXT, true));

        TextView subtitle = text("Terminal • Ubuntu • AI • Dev Tools", 17, MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(4), 0, dp(26));
        root.addView(subtitle, subLp);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(cardDrawable(Color.rgb(18, 17, 26), PURPLE, 1));
        hero.addView(text(">_  Termux Lab  v0.2", 24, TEXT, true));

        TextView heroSub = text(
            "Fork Termux sungguhan. Dashboard dan terminal berjalan dalam package yang sama.",
            15, MUTED, false);
        LinearLayout.LayoutParams heroSubLp = new LinearLayout.LayoutParams(-1, -2);
        heroSubLp.setMargins(0, dp(8), 0, 0);
        hero.addView(heroSub, heroSubLp);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(0, 0, 0, dp(22));
        root.addView(hero, heroLp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        root.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        addCard(grid, ">_", "Terminal", "Shell Termux asli", this::openTerminal);
        addCard(grid, "◉", "Ubuntu", "PRoot distro",
            () -> runInTerminal("Ubuntu", "exec proot-distro login ubuntu"));
        addCard(grid, "AI", "Codex", "AI coding CLI",
            () -> runInTerminal("Codex",
                "command -v codex >/dev/null 2>&1 && exec codex || { echo 'Codex belum terpasang'; exec bash; }"));
        addCard(grid, "</>", "Claude", "Claude Code CLI",
            () -> runInTerminal("Claude",
                "command -v claude >/dev/null 2>&1 && exec claude || { echo 'Claude belum terpasang'; exec bash; }"));
        addCard(grid, "V", "Neovim", "Editor terminal",
            () -> runInTerminal("Neovim",
                "command -v nvim >/dev/null 2>&1 && exec nvim || { echo 'Neovim belum terpasang'; exec bash; }"));
        addCard(grid, "G", "Git", "Status repo aktif",
            () -> runInTerminal("Git", "git status; exec bash"));
        addCard(grid, "Py", "Python", "Python shell",
            () -> runInTerminal("Python",
                "command -v python >/dev/null 2>&1 && exec python || exec python3"));
        addCard(grid, "⬆", "Update", "pkg update",
            () -> runInTerminal("Update", "pkg update; exec bash"));

        TextView sysLabel = text("STATUS SISTEM", 14, PURPLE, true);
        LinearLayout.LayoutParams sysLabelLp = new LinearLayout.LayoutParams(-1, -2);
        sysLabelLp.setMargins(dp(4), dp(26), 0, dp(12));
        root.addView(sysLabel, sysLabelLp);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(20), dp(18), dp(20), dp(18));
        status.setBackground(cardDrawable(Color.rgb(18, 17, 26), CARD_STROKE, 1));
        status.addView(text("Termux Lab  •  Fork internal", 16, GREEN, false));
        status.addView(statusLine("Android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"));
        status.addView(statusLine("Perangkat", Build.MANUFACTURER + " " + Build.MODEL));
        status.addView(statusLine("Arsitektur", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown"));
        status.addView(statusLine("Package", getPackageName()));
        root.addView(status);

        TextView note = text(
            "Build ini tetap memakai package com.termux agar bootstrap dan paket Termux kompatibel.",
            13, MUTED, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(16), 0, 0);
        root.addView(note, noteLp);

        return scroll;
    }

    private TextView statusLine(String key, String value) {
        TextView v = text(key + "   •   " + value, 15, TEXT, false);
        v.setPadding(0, dp(12), 0, 0);
        return v;
    }

    private void addCard(GridLayout grid, String icon, String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(cardDrawable(CARD, CARD_STROKE, 1));

        card.addView(text(icon, 24, PURPLE, true));

        TextView titleView = text(title, 20, TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(12), 0, dp(5));
        card.addView(titleView, titleLp);
        card.addView(text(subtitle, 14, MUTED, false));

        card.setOnClickListener(v -> action.run());

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(150);
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
            Toast.makeText(
                this,
                "Bootstrap Termux belum siap. Buka Terminal sekali terlebih dahulu.",
                Toast.LENGTH_LONG
            ).show();
            openTerminal();
            return;
        }

        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(bash)
            .build();

        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka sesi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        g.setCornerRadius(dp(22));
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

