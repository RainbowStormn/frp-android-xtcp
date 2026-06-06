package com.example.frpcvisitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements LogRepository.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 10;

    private final LogRepository logs = LogRepository.getInstance();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private EditText serverAddrInput;
    private EditText serverPortInput;
    private EditText tokenInput;
    private EditText serverNameInput;
    private EditText secretKeyInput;
    private EditText bindPortInput;
    private CheckBox keepTunnelOpenInput;
    private TextView logView;
    private ScrollView pageScroll;
    private boolean startAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        stageBinaryOnFirstLaunch();
    }

    private View createContentView() {
        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, padding, padding, padding);
        pageScroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText(R.string.screen_description);
        description.setTextSize(14);
        description.setPadding(0, dp(4), 0, dp(12));
        content.addView(description, matchWrap());

        serverAddrInput = addInput(content, R.string.hint_server_addr, false, null);
        serverPortInput = addInput(content, R.string.hint_server_port, false, "7000");
        serverPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        tokenInput = addInput(content, R.string.hint_token, true, null);
        serverNameInput = addInput(content, R.string.hint_server_name, false, null);
        secretKeyInput = addInput(content, R.string.hint_secret_key, true, null);
        bindPortInput = addInput(content, R.string.hint_bind_port, false, "6000");
        bindPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        keepTunnelOpenInput = new CheckBox(this);
        keepTunnelOpenInput.setText(R.string.keep_tunnel_open);
        content.addView(keepTunnelOpenInput, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, dp(8));
        content.addView(actions, matchWrap());

        Button startButton = new Button(this);
        startButton.setText(R.string.action_start);
        startButton.setOnClickListener(view -> handleStart());
        actions.addView(startButton, weightedButton());

        Button stopButton = new Button(this);
        stopButton.setText(R.string.action_stop);
        stopButton.setOnClickListener(view -> handleStop());
        actions.addView(stopButton, weightedButton());

        Button clearButton = new Button(this);
        clearButton.setText(R.string.action_clear_logs);
        clearButton.setOnClickListener(view -> logs.clear());
        actions.addView(clearButton, weightedButton());

        TextView logTitle = new TextView(this);
        logTitle.setText(R.string.log_title);
        logTitle.setTextSize(16);
        logTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logTitle.setPadding(0, dp(8), 0, dp(4));
        content.addView(logTitle, matchWrap());

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(12);
        logView.setTextIsSelectable(true);
        logView.setMinHeight(dp(240));
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logView.setBackgroundColor(0xFFF2F2F2);
        content.addView(logView, matchWrap());

        applySystemBarInsets(content, padding);
        return pageScroll;
    }

    private EditText addInput(
            LinearLayout parent,
            int hintResource,
            boolean secret,
            String defaultValue) {
        EditText input = new EditText(this);
        input.setHint(hintResource);
        input.setSingleLine(true);
        input.setTextSize(16);
        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (defaultValue != null) {
            input.setText(defaultValue);
        }
        parent.addView(input, matchWrap());
        return input;
    }

    private void handleStart() {
        try {
            FrpcConfigManager.Config config = readConfig();
            FrpcConfigManager.write(this, config);
            logs.setSensitiveValues(config.token, config.secretKey);
            logs.append("[app] 配置已保存");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                startAfterPermission = true;
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
                return;
            }
            startServiceCompat();
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception.getMessage());
        }
    }

    private FrpcConfigManager.Config readConfig() {
        return new FrpcConfigManager.Config(
                text(serverAddrInput),
                parsePort(serverPortInput, "serverPort"),
                text(tokenInput),
                text(serverNameInput),
                text(secretKeyInput),
                parsePort(bindPortInput, "bindPort"),
                keepTunnelOpenInput.isChecked());
    }

    private void startServiceCompat() {
        Intent intent = FrpcService.createStartIntent(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            logs.append("[app] 已请求启动前台服务");
        } catch (RuntimeException exception) {
            showError("无法启动服务: " + exception.getMessage());
        }
    }

    private void handleStop() {
        try {
            startService(FrpcService.createStopIntent(this));
        } catch (RuntimeException exception) {
            showError("无法停止服务: " + exception.getMessage());
        }
    }

    private void stageBinaryOnFirstLaunch() {
        backgroundExecutor.execute(() -> {
            try {
                FrpcBinaryManager.copyAssetToPrivateDirectory(this);
                logs.append("[app] frpc 二进制已准备");
            } catch (IOException exception) {
                logs.append("[app] frpc 二进制尚未准备: " + exception.getMessage());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && startAfterPermission) {
            startAfterPermission = false;
            if (grantResults.length == 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        R.string.notification_permission_denied,
                        Toast.LENGTH_LONG).show();
            }
            startServiceCompat();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        logs.addListener(this);
        renderLogs(logs.snapshot());
    }

    @Override
    protected void onStop() {
        logs.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onLineAdded(String line) {
        renderLogs(logs.snapshot());
        pageScroll.post(() -> pageScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onLogsCleared() {
        logView.setText("");
    }

    private void renderLogs(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line);
        }
        logView.setText(text);
    }

    private void showError(String message) {
        String safeMessage = message == null ? "未知错误" : message;
        logs.append("[app] " + safeMessage);
        Toast.makeText(this, safeMessage, Toast.LENGTH_LONG).show();
    }

    private static String text(EditText input) {
        return input.getText().toString().trim();
    }

    private static int parsePort(EditText input, String fieldName) {
        String value = text(input);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " 必须是整数");
        }
    }

    private void applySystemBarInsets(View content, int basePadding) {
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars());
                view.setPadding(
                        basePadding + bars.left,
                        basePadding + bars.top,
                        basePadding + bars.right,
                        basePadding + bars.bottom);
            } else {
                view.setPadding(
                        basePadding + insets.getSystemWindowInsetLeft(),
                        basePadding + insets.getSystemWindowInsetTop(),
                        basePadding + insets.getSystemWindowInsetRight(),
                        basePadding + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
