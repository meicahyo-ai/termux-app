package com.termux.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-app AI chat + explicit, allowlisted command approval.
 * Calls ~/.termux-lab/ai_bridge.py; no model-generated raw shell is executed.
 * v0.8 MVP: conversation is kept in memory only while the Activity exists.
 */
public final class TermuxLabAiChatPanel {
    private static final int BG = Color.rgb(10, 9, 8);
    private static final int PANEL = Color.rgb(24, 21, 17);
    private static final int PANEL_2 = Color.rgb(31, 27, 21);
    private static final int BORDER = Color.rgb(91, 74, 48);
    private static final int TEXT = Color.rgb(246, 226, 188);
    private static final int MUTED = Color.rgb(180, 163, 132);
    private static final int AMBER = Color.rgb(238, 188, 101);
    private static final int AMBER_DARK = Color.rgb(92, 63, 26);
    private static final int GREEN = Color.rgb(126, 207, 112);
    private static final int RED = Color.rgb(236, 92, 78);

    private final AppCompatActivity activity;
    private final Runnable scrollDown;
    private final LinearLayout body;
    private final LinearLayout composer;
    private final LinearLayout messagesHost;
    private final List<Message> messages = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText promptInput;
    private TextView sendButton;
    private TextView cancelButton;
    private TextView statusText;
    private TextView modelButton;
    private TextView modeChatButton;
    private TextView modePlanButton;
    private String backend = "codex";
    private String model = "";
    private String mode = "chat";
    private boolean busy;
    private long taskSerial;
    private volatile Process activeProcess;
    // Exactly one plan may be approved. Old cards stay visible as read-only history.
    private ActionPlan activePlan;


    public TermuxLabAiChatPanel(AppCompatActivity activity, Runnable scrollDown) {
        this.activity = activity;
        this.scrollDown = scrollDown;
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        composer = new LinearLayout(activity);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(10), dp(7), dp(10), dp(8));
        composer.setBackgroundColor(PANEL);
        messagesHost = new LinearLayout(activity);
        messagesHost.setOrientation(LinearLayout.VERTICAL);
        buildBody();
        buildComposer();
    }

    public View contentView() { return body; }
    public View composerView() { return composer; }

    public void dispose() {
        taskSerial++;
        if (activePlan != null && activePlan.lifecycle.cancel()) {
            refreshActionCard(activePlan);
            activePlan = null;
        }
        cancelRunningProcess();
    }

    private void buildBody() {
        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(5), dp(7), dp(5), dp(12));
        title.addView(text("AI CHAT / CONTROL", 18, TEXT, true));
        title.addView(text("Chat dengan CLI nyata • perintah perlu persetujuan", 10, MUTED, false));
        body.addView(title);

        HorizontalScrollView bar = new HorizontalScrollView(activity);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, 0, 0, dp(10));
        modeChatButton = button("CHAT", () -> setMode("chat"));
        modePlanButton = button("PERINTAH", () -> setMode("plan"));
        controls.addView(modeChatButton, barButtonLp());
        controls.addView(modePlanButton, barButtonLp());
        controls.addView(button("TOOLS", this::chooseTools), barButtonLp());
        controls.addView(button("HAPUS CHAT", this::askClear), barButtonLp());
        bar.addView(controls);
        body.addView(bar);

        LinearLayout context = box();
        TextView heading = text("WORKSPACE  /root/projects/engineering-lab", 10, AMBER, true);
        context.addView(heading);
        context.addView(text("Chat membaca proyek melalui Codex atau Claude dalam mode terbatas. " +
                "Tugas hanya dieksekusi setelah kamu melihat rencana dan menyetujuinya. " +
                "Prompt/kode dapat dikirim ke provider AI eksternal.", 11, MUTED, false));
        body.addView(context, blockLp());

        body.addView(messagesHost, blockLp());
        statusText = text("Siap. Ketik pertanyaan atau pilih PERINTAH.", 10, MUTED, false);
        statusText.setPadding(dp(6), dp(8), dp(6), dp(12));
        body.addView(statusText);
        showBubble("assistant", "Termux Lab AI siap. Saya bisa membantu analisis repo, " +
                "atau menyusun tindakan seperti Git status, OCR review, dan tes. " +
                "Belum ada perintah yang dijalankan.", false);
        setMode("chat");
    }

    private void buildComposer() {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        promptInput = new EditText(activity);
        promptInput.setTextColor(TEXT);
        promptInput.setHintTextColor(MUTED);
        promptInput.setTextSize(13);
        promptInput.setTypeface(Typeface.DEFAULT);
        promptInput.setHint("Tanyakan atau beri perintah...");
        promptInput.setMinLines(1);
        promptInput.setMaxLines(4);
        promptInput.setPadding(dp(9), dp(8), dp(9), dp(8));
        promptInput.setBackground(round(BG, BORDER, 1, 9));
        line.addView(promptInput, new LinearLayout.LayoutParams(0, -2, 1f));

        sendButton = button("KIRIM", this::sendPrompt);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-2, dp(42));
        sendLp.setMargins(dp(7), 0, 0, 0);
        line.addView(sendButton, sendLp);
        composer.addView(line);

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(6), 0, 0);
        modelButton = button("Codex • default ▾", this::chooseModel);
        bottom.addView(modelButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        cancelButton = button("STOP", this::cancelCurrent);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-2, dp(38));
        stopLp.setMargins(dp(8), 0, 0, 0);
        bottom.addView(cancelButton, stopLp);
        composer.addView(bottom);
        setBusy(false);
    }

    private void setMode(String next) {
        if (busy) return;
        mode = next;
        if (modeChatButton != null) modeChatButton.setBackground(round("chat".equals(mode) ? AMBER_DARK : PANEL_2, "chat".equals(mode) ? AMBER : BORDER, 1, 7));
        if (modePlanButton != null) modePlanButton.setBackground(round("plan".equals(mode) ? AMBER_DARK : PANEL_2, "plan".equals(mode) ? AMBER : BORDER, 1, 7));
        if (promptInput != null) promptInput.setHint("plan".equals(mode)
                ? "Contoh: cek Git lalu jelaskan apa yang berubah..." : "Tanyakan apa saja tentang proyek...");
        if (statusText != null && !busy) statusText.setText("plan".equals(mode)
                ? "Mode PERINTAH: AI memilih aksi allowlist, lalu meminta persetujuan."
                : "Mode CHAT: analisis read-only, tanpa eksekusi aksi dari aplikasi.");
    }

    private void chooseModel() {
        if (busy) { statusText.setText("Tunggu atau STOP permintaan sebelum mengganti model."); return; }
        String[] options = {
                "Codex • model default konfigurasi CLI",
                "Codex • ID model manual",
                "Claude Code • model default (perlu akun aktif)",
                "Claude Code • ID model manual"
        };
        new AlertDialog.Builder(activity).setTitle("Provider / Model")
                .setItems(options, (dialog, which) -> {
                    backend = (which <= 1) ? "codex" : "claude";
                    if (which == 0 || which == 2) {
                        model = "";
                        updateModelLabel();
                    } else {
                        askModelId();
                    }
                })
                .setNegativeButton("BATAL", null).show();
    }

    private void askModelId() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(model);
        input.setHint("ID persis dari provider, mis. model-yang-tersedia");
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setPadding(dp(20), dp(7), dp(20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(activity)
                .setTitle("ID model manual")
                .setMessage("Hanya model yang diizinkan akun/provider bisa dipakai. Model pada aplikasi ChatGPT tidak otomatis tersedia di Codex/API.")
                .setView(wrapper)
                .setNegativeButton("BATAL", null)
                .setPositiveButton("GUNAKAN", (dialog, which) -> {
                    String id = input.getText().toString().trim();
                    if (!id.matches("[A-Za-z0-9._:/-]{1,96}")) {
                        statusText.setText("ID model tidak valid; menggunakan model default.");
                        model = "";
                    } else model = id;
                    updateModelLabel();
                }).show();
    }

    private void updateModelLabel() {
        if (modelButton != null) modelButton.setText(("codex".equals(backend) ? "Codex" : "Claude")
                + " • " + (model.isEmpty() ? "default" : model) + " ▾");
    }

    private void sendPrompt() {
        if (busy) return;
        String message = promptInput.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;
        if (message.length() > 8000) {
            statusText.setText("Pesan terlalu panjang (maksimum 8.000 karakter).");
            return;
        }
        JSONObject req = new JSONObject();
        try {
            req.put("mode", mode);
            req.put("backend", backend);
            req.put("model", model);
            req.put("prompt", message);
            JSONArray history = new JSONArray();
            for (int i = Math.max(0, messages.size() - 8); i < messages.size(); i++) {
                Message old = messages.get(i);
                if ("assistant".equals(old.role) || "user".equals(old.role)) {
                    history.put(new JSONObject().put("role", old.role).put("text", old.text));
                }
            }
            req.put("history", history);
        } catch (Exception error) {
            statusText.setText("Gagal menyiapkan permintaan: " + error.getMessage());
            return;
        }
        // A new request invalidates any old unapproved plan. No stale card may be run.
        supersedePendingPlan();
        promptInput.setText("");
        showBubble("user", message, true);
        runBridge(req, "Memproses lewat " + ("codex".equals(backend) ? "Codex" : "Claude") + "…", null);
    }

    private void chooseTools() {
        if (busy) { statusText.setText("Hentikan proses aktif sebelum memilih tool."); return; }
        String[] labels = {
                "Git status (baca saja)", "Git diff (baca saja)", "Git log (baca saja)",
                "Jalankan pytest (mengeksekusi kode proyek)",
                "OpenCodeReview: review (mungkin berbayar)",
                "OpenCodeReview: scan . (mungkin berbayar)"
        };
        String[] ids = {"git_status", "git_diff", "git_log", "pytest", "ocr_review", "ocr_scan"};
        new AlertDialog.Builder(activity).setTitle("Tools • pilih tindakan")
                .setItems(labels, (dialog, which) -> {
                    String action = ids[which];
                    showActionCard(action, "Tindakan manual dipilih. Tinjau perintahnya sebelum menjalankan.");
                })
                .setNegativeButton("BATAL", null).show();
    }

    private String[] actionDetails(String action) {
        switch (action) {
            case "git_status": return new String[]{"Git status", "git status --short --branch", "Membaca status Git."};
            case "git_diff": return new String[]{"Git diff", "git diff --stat && git diff --no-ext-diff -- .", "Membaca perubahan Git."};
            case "git_log": return new String[]{"Git log", "git log -5 --oneline", "Membaca riwayat commit."};
            case "pytest": return new String[]{"Pytest", "if [ -x .venv/bin/python ]; then .venv/bin/python -m pytest -q; elif [ -x venv/bin/python ]; then venv/bin/python -m pytest -q; else python3 -m pytest -q; fi", "Tes mengeksekusi kode proyek dan bisa membuat file."};
            case "ocr_review": return new String[]{"OCR review", "ocr review", "Dapat mengirim kode ke provider AI dan menggunakan kuota/API berbayar."};
            case "ocr_scan": return new String[]{"OCR scan", "ocr scan .", "Dapat memindai seluruh proyek, mengirim kode, dan menggunakan kuota/API berbayar."};
            default: return null;
        }
    }

    /** Card is a one-use approval capability, not merely a display of command text. */
    private final class ActionPlan {
        final String action;
        final TermuxLabActionLifecycle lifecycle = new TermuxLabActionLifecycle();
        final LinearLayout card;
        final TextView headline;
        final LinearLayout buttons;
        int exitCode = -1;

        ActionPlan(String action, LinearLayout card, TextView headline, LinearLayout buttons) {
            this.action = action;
            this.card = card;
            this.headline = headline;
            this.buttons = buttons;
        }
    }

    private void supersedePendingPlan() {
        if (activePlan == null) return;
        if (activePlan.lifecycle.supersede()) {
            refreshActionCard(activePlan);
            activePlan = null;
        }
    }

    private void refreshActionCard(ActionPlan plan) {
        String label;
        int color;
        switch (plan.lifecycle.state()) {
            case PENDING:
                label = "RENCANA EKSEKUSI • MENUNGGU PERSETUJUAN";
                color = AMBER;
                break;
            case REVIEWING:
                label = "RENCANA EKSEKUSI • KONFIRMASI TERBUKA";
                color = AMBER;
                break;
            case EXECUTING:
                label = "RENCANA EKSEKUSI • SEDANG DIJALANKAN";
                color = AMBER;
                break;
            case SUCCEEDED:
                label = "RENCANA EKSEKUSI • SELESAI (EXIT " + plan.exitCode + ")";
                color = GREEN;
                break;
            case FAILED:
                label = "RENCANA EKSEKUSI • GAGAL (EXIT " + plan.exitCode + ")";
                color = RED;
                break;
            case CANCELLED:
                label = "RENCANA EKSEKUSI • DIBATALKAN";
                color = MUTED;
                break;
            default:
                label = "RENCANA EKSEKUSI • DIGANTIKAN RENCANA BARU";
                color = MUTED;
                break;
        }
        plan.headline.setText(label);
        plan.headline.setTextColor(color);
        // Hide review + cancel controls for all non-pending states.
        // This also blocks tapping a second confirmation during execution.
        plan.buttons.setVisibility(plan.lifecycle.state() == TermuxLabActionLifecycle.State.PENDING
                ? View.VISIBLE : View.GONE);
    }

    private void showActionCard(String action, String summary) {
        if (busy) return;
        String[] detail = actionDetails(action);
        if (detail == null) {
            showBubble("system", "Aksi ditolak: tidak termasuk allowlist.", false);
            statusText.setText("Aksi tidak tersedia.");
            return;
        }
        supersedePendingPlan();
        LinearLayout card = box();
        TextView headline = text("", 11, AMBER, true);
        card.addView(headline);
        card.addView(text(summary, 12, TEXT, false));
        TextView command = text("$ " + detail[1], 12, GREEN, false);
        command.setTextIsSelectable(true);
        command.setPadding(0, dp(8), 0, dp(8));
        card.addView(command);
        card.addView(text("Workspace: /root/projects/engineering-lab\n" + detail[2], 10, MUTED, false));
        LinearLayout actions = new LinearLayout(activity);
        actions.setPadding(0, dp(8), 0, 0);
        ActionPlan plan = new ActionPlan(action, card, headline, actions);
        actions.addView(button("TINJAU & JALANKAN", () -> confirmExecute(plan)), barButtonLp());
        actions.addView(button("BATAL", () -> {
            if (activePlan != plan || !plan.lifecycle.cancel()) return;
            refreshActionCard(plan);
            activePlan = null;
            statusText.setText("Rencana dibatalkan. Tidak ada perintah dijalankan.");
        }), barButtonLp());
        card.addView(actions);
        activePlan = plan;
        refreshActionCard(plan);
        messagesHost.addView(card, blockLp());
        statusText.setText("Rencana siap. Tinjau lalu setujui, atau batalkan.");
        scrollDown.run();
    }

    private void confirmExecute(ActionPlan plan) {
        if (busy || activePlan != plan || !plan.lifecycle.beginReview()) return;
        String[] detail = actionDetails(plan.action);
        if (detail == null) {
            plan.lifecycle.cancel();
            refreshActionCard(plan);
            activePlan = null;
            return;
        }
        refreshActionCard(plan);
        new AlertDialog.Builder(activity)
                .setTitle("Konfirmasi eksekusi: " + detail[0])
                .setMessage("Jalankan di Ubuntu, folder engineering-lab?\n\n$ " + detail[1] +
                        "\n\n" + detail[2] + "\n\nHanya tindakan ini yang diizinkan.")
                .setNegativeButton("BATAL", null)
                .setPositiveButton("SETUJU & JALANKAN", (dialog, which) -> {
                    // Validation and transition happen atomically on the Android UI thread.
                    if (busy || activePlan != plan ||
                            plan.lifecycle.state() != TermuxLabActionLifecycle.State.REVIEWING) return;
                    JSONObject req = new JSONObject();
                    try {
                        req.put("mode", "execute");
                        req.put("action", plan.action);
                    } catch (Exception error) {
                        statusText.setText("Gagal menyiapkan eksekusi: " + error.getMessage());
                        return;
                    }
                    if (!plan.lifecycle.approve()) return;
                    refreshActionCard(plan);
                    showBubble("system", "Disetujui: $ " + detail[1], false);
                    runBridge(req, "Menjalankan " + detail[0] + "…", plan);
                })
                .setOnDismissListener(dialog -> {
                    // Covers BATAL, back button, or tapping outside the dialog.
                    if (plan.lifecycle.declineReview()) refreshActionCard(plan);
                })
                .show();
    }

    private void runBridge(JSONObject request, String label, ActionPlan executionPlan) {
        if (busy) return;
        final long serial = ++taskSerial;
        setBusy(true);
        statusText.setText(label);
        new Thread(() -> {
            String answer = null;
            String rawLog = "";
            Process ownedProcess = null;
            try {
                File python = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/python");
                File bridge = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.termux-lab/ai_bridge.py");
                if (!python.isFile() || !bridge.isFile()) {
                    throw new IllegalStateException("Python atau ai_bridge.py belum terpasang. Jalankan upgrade v0.8.");
                }
                ProcessBuilder pb = new ProcessBuilder(python.getAbsolutePath(), bridge.getAbsolutePath());
                pb.directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
                Map<String, String> env = pb.environment();
                env.put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
                env.put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
                env.put("TMPDIR", TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/tmp");
                env.put("PATH", TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin:/system/bin:/system/xbin");
                File preload = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/lib/libtermux-exec-ld-preload.so");
                if (preload.isFile()) env.put("LD_PRELOAD", preload.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                ownedProcess = proc;
                if (serial != taskSerial) {
                    proc.destroy();
                    return;
                }
                activeProcess = proc;
                try (OutputStreamWriter writer = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(request.toString());
                }
                StringBuilder logs = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("TLJSON:")) answer = line.substring(7);
                        else if (logs.length() < 6000) logs.append(line).append('\n');
                    }
                }
                rawLog = logs.toString();
                proc.waitFor();
            } catch (Exception error) {
                rawLog = error.getClass().getSimpleName() + ": " + error.getMessage() + "\n" + rawLog;
            } finally { if (activeProcess == ownedProcess) activeProcess = null; }
            final String payload = answer;
            final String diagnostic = rawLog;
            handler.post(() -> {
                if (serial != taskSerial) return;
                setBusy(false);
                if (payload == null) {
                    failExecutionPlan(executionPlan, -1);
                    showBubble("system", "Backend tidak memberikan hasil. " + diagnostic, false);
                    statusText.setText("Gagal menjalankan bridge.");
                    return;
                }
                try { handleResponse(request, new JSONObject(payload), executionPlan); }
                catch (Exception e) {
                    failExecutionPlan(executionPlan, -1);
                    showBubble("system", "Respons backend tidak valid: " + e.getMessage(), false);
                    statusText.setText("Format respons tidak valid.");
                }
            });
        }, "termux-lab-ai-bridge").start();
    }

    private void finishExecutionPlan(ActionPlan plan, int exitCode, boolean success) {
        if (plan == null) return;
        plan.exitCode = exitCode;
        if (plan.lifecycle.complete(success)) refreshActionCard(plan);
        if (activePlan == plan) activePlan = null;
    }

    private void failExecutionPlan(ActionPlan plan, int exitCode) {
        finishExecutionPlan(plan, exitCode, false);
    }

    private void handleResponse(JSONObject request, JSONObject result, ActionPlan executionPlan) {
        String reqMode = request.optString("mode", "chat");
        // For execute, even a nonzero exit must close the single-use approval card.
        // The old implementation returned early on !ok, leaving a reusable pending card.
        if ("execute".equals(reqMode)) {
            int exit = result.has("exit") ? result.optInt("exit", -1) : -1;
            boolean success = result.optBoolean("ok", false) && exit == 0;
            finishExecutionPlan(executionPlan, exit, success);
            String title = result.optString("title", "Perintah");
            String output = result.optString("output", "");
            String error = result.optString("error", "");
            StringBuilder report = new StringBuilder(success ? "SELESAI" : "GAGAL")
                    .append(" • ").append(title).append(" • exit ").append(exit);
            if (!output.isEmpty()) report.append("\n\n").append(output);
            if (!error.isEmpty()) report.append("\n\nERROR: ").append(error);
            showBubble("system", report.toString(), false);
            statusText.setText(success ? "Aksi selesai. Periksa output di atas."
                    : "Aksi gagal. Periksa output di atas. Persetujuan lama tidak berlaku.");
            return;
        }
        if (!result.optBoolean("ok", false)) {
            String error = result.optString("error", "Unknown error");
            showBubble("system", "GAGAL: " + error, false);
            statusText.setText("Gagal. Cek login CLI / sandbox / jaringan.");
            return;
        }
        if ("chat".equals(reqMode)) {
            showBubble("assistant", result.optString("message", "(Kosong)"), true);
            statusText.setText("Selesai. Mode chat read-only.");
        } else if ("plan".equals(reqMode)) {
            String message = result.optString("message", "");
            showBubble("assistant", message, true);
            String action = result.optString("action", "none");
            if (!"none".equals(action)) showActionCard(action, message);
            else statusText.setText("Tidak ada tindakan allowlist yang sesuai.");
        }
    }

    private void cancelCurrent() {
        if (!busy) return;
        ++taskSerial;
        if (activePlan != null && activePlan.lifecycle.cancel()) {
            refreshActionCard(activePlan);
            activePlan = null;
        }
        cancelRunningProcess();
        setBusy(false);
        showBubble("system", "Permintaan dibatalkan oleh pengguna.", false);
        statusText.setText("Dibatalkan.");
    }

    private void cancelRunningProcess() {
        Process p = activeProcess;
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) { }
            handler.postDelayed(() -> {
                if (p.isAlive()) { try { p.destroyForcibly(); } catch (Exception ignored) { } }
            }, 1500);
        }
    }

    private void setBusy(boolean isBusy) {
        busy = isBusy;
        if (sendButton != null) {
            sendButton.setEnabled(!isBusy);
            sendButton.setAlpha(isBusy ? 0.5f : 1f);
        }
        if (cancelButton != null) {
            cancelButton.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        }
        if (modeChatButton != null) modeChatButton.setEnabled(!isBusy);
        if (modePlanButton != null) modePlanButton.setEnabled(!isBusy);
    }

    private void askClear() {
        if (busy) { statusText.setText("Hentikan proses aktif sebelum menghapus chat."); return; }
        new AlertDialog.Builder(activity)
                .setTitle("Hapus percakapan?")
                .setMessage("Riwayat percakapan panel ini akan dikosongkan. Tidak menghapus proyek atau akun AI.")
                .setNegativeButton("BATAL", null)
                .setPositiveButton("HAPUS", (dialog, which) -> {
                    if (busy) return;
                    if (activePlan != null && activePlan.lifecycle.cancel()) {
                        refreshActionCard(activePlan);
                        activePlan = null;
                    }
                    messages.clear();
                    messagesHost.removeAllViews();
                    showBubble("assistant", "Percakapan baru. Siap menerima instruksi.", false);
                    statusText.setText("Percakapan dihapus. Tidak ada rencana aktif.");
                }).show();
    }

    private void showBubble(String role, String message, boolean store) {
        if (store) messages.add(new Message(role, message));
        LinearLayout bubble = box();
        boolean user = "user".equals(role);
        bubble.setBackground(round(user ? Color.rgb(49, 37, 25) : PANEL, user ? AMBER : BORDER, 1, 9));
        bubble.addView(text(user ? "KAMU" : "assistant".equals(role) ? "AI  •  " + ("codex".equals(backend) ? "CODEX" : "CLAUDE") : "TERMUX LAB", 10, user ? AMBER : GREEN, true));
        TextView bodyText = text(message, 12, TEXT, false);
        bodyText.setTypeface(Typeface.DEFAULT);
        bodyText.setTextIsSelectable(true);
        bodyText.setPadding(0, dp(5), 0, 0);
        bubble.addView(bodyText);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(user ? dp(38) : 0, 0, user ? 0 : dp(18), dp(9));
        messagesHost.addView(bubble, lp);
        scrollDown.run();
    }

    private LinearLayout box() {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(12), dp(11), dp(12), dp(11));
        v.setBackground(round(PANEL, BORDER, 1, 8));
        return v;
    }

    private LinearLayout.LayoutParams blockLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private LinearLayout.LayoutParams barButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(7), 0);
        return lp;
    }

    private TextView button(String label, Runnable action) {
        TextView v = text(label, 11, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(11), dp(9), dp(11), dp(9));
        v.setBackground(round(PANEL_2, BORDER, 1, 7));
        v.setOnClickListener(x -> action.run());
        return v;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(width), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Message {
        final String role;
        final String text;
        Message(String role, String text) { this.role = role; this.text = text; }
    }
}
