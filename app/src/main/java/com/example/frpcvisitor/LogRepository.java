package com.example.frpcvisitor;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

public final class LogRepository {
    public interface Listener {
        void onLineAdded(String line);

        void onLogsCleared();
    }

    private static final int MAX_LINES = 500;
    private static final Pattern ANSI_ESCAPE =
            Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    private static final LogRepository INSTANCE = new LogRepository();

    private final Deque<String> lines = new ArrayDeque<>(MAX_LINES);
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<String> sensitiveValues = Collections.emptyList();

    private LogRepository() {
    }

    public static LogRepository getInstance() {
        return INSTANCE;
    }

    public synchronized void setSensitiveValues(String... values) {
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                filtered.add(value);
                String escaped = escapeForStructuredLog(value);
                if (!escaped.equals(value)) {
                    filtered.add(escaped);
                }
            }
        }
        sensitiveValues = filtered;
    }

    public void append(String rawLine) {
        final String safeLine;
        synchronized (this) {
            String plainLine = ANSI_ESCAPE.matcher(rawLine == null ? "" : rawLine)
                    .replaceAll("");
            safeLine = redact(plainLine);
            if (lines.size() == MAX_LINES) {
                lines.removeFirst();
            }
            lines.addLast(safeLine);
        }
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onLineAdded(safeLine);
            }
        });
    }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }

    public void clear() {
        synchronized (this) {
            lines.clear();
        }
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onLogsCleared();
            }
        });
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private String redact(String line) {
        String result = line;
        for (String value : sensitiveValues) {
            result = result.replace(value, "[REDACTED]");
        }
        return result;
    }

    private static String escapeForStructuredLog(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
