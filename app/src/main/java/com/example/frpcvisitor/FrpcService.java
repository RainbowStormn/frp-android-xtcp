package com.example.frpcvisitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FrpcService extends Service {
    private static final String ACTION_START =
            "com.example.frpcvisitor.action.START";
    private static final String ACTION_STOP =
            "com.example.frpcvisitor.action.STOP";
    private static final String CHANNEL_ID = "frpc_status";
    private static final int NOTIFICATION_ID = 1001;

    private final Object processLock = new Object();
    private final LogRepository logs = LogRepository.getInstance();
    private ExecutorService executor;
    private Process process;
    private boolean starting;
    private boolean stopping;

    public static Intent createStartIntent(Context context) {
        return new Intent(context, FrpcService.class).setAction(ACTION_START);
    }

    public static Intent createStopIntent(Context context) {
        return new Intent(context, FrpcService.class).setAction(ACTION_STOP);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopFrpcAndService();
            return START_NOT_STICKY;
        }

        try {
            enterForeground();
        } catch (RuntimeException exception) {
            logs.append("[app] 无法启动前台服务: " + safeMessage(exception));
            stopSelf();
            return START_NOT_STICKY;
        }

        synchronized (processLock) {
            stopping = false;
            if (process != null || starting) {
                logs.append("[app] frpc 已经在运行或正在启动");
                return START_NOT_STICKY;
            }
            starting = true;
        }
        executor.execute(this::runFrpc);
        return START_NOT_STICKY;
    }

    private void runFrpc() {
        Process startedProcess = null;
        boolean stoppedByUser = false;
        try {
            File binary = FrpcBinaryManager.prepare(this);
            File config = FrpcConfigManager.getConfigFile(this);
            if (!config.isFile() || config.length() == 0) {
                throw new IllegalStateException("配置文件不存在或为空");
            }

            ProcessBuilder builder = new ProcessBuilder(
                    binary.getAbsolutePath(),
                    "-c",
                    config.getAbsolutePath());
            builder.directory(config.getParentFile());
            builder.redirectErrorStream(true);
            builder.environment().put("HOME", getFilesDir().getAbsolutePath());
            builder.environment().put("TMPDIR", getCacheDir().getAbsolutePath());

            logs.append("[app] 正在启动 frpc");
            startedProcess = builder.start();
            synchronized (processLock) {
                starting = false;
                if (stopping) {
                    stoppedByUser = true;
                    terminateProcess(startedProcess);
                } else {
                    process = startedProcess;
                }
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    startedProcess.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line);
                }
            }

            int exitCode = startedProcess.waitFor();
            synchronized (processLock) {
                stoppedByUser = stoppedByUser || stopping;
                if (process == startedProcess) {
                    process = null;
                }
            }
            if (stoppedByUser) {
                logs.append("[app] frpc 已停止");
            } else {
                logs.append("[app] frpc 异常退出，退出码: " + exitCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (isStopping()) {
                stoppedByUser = true;
                logs.append("[app] frpc 已停止");
            } else {
                logs.append("[app] frpc 运行线程被中断");
            }
        } catch (Exception exception) {
            if (isStopping()) {
                stoppedByUser = true;
                logs.append("[app] frpc 已停止");
            } else {
                logs.append("[app] frpc 启动失败: " + safeMessage(exception));
            }
        } finally {
            synchronized (processLock) {
                starting = false;
                if (process == startedProcess) {
                    process = null;
                }
                stoppedByUser = stoppedByUser || stopping;
            }
            if (!stoppedByUser) {
                removeForeground();
                stopSelf();
            }
        }
    }

    private void stopFrpcAndService() {
        Process currentProcess;
        synchronized (processLock) {
            stopping = true;
            currentProcess = process;
            process = null;
        }
        if (currentProcess != null) {
            terminateProcess(currentProcess);
        } else {
            logs.append("[app] frpc 未在运行");
        }
        removeForeground();
        stopSelf();
    }

    private boolean isStopping() {
        synchronized (processLock) {
            return stopping;
        }
    }

    @SuppressWarnings("deprecation")
    private void removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private static void terminateProcess(Process target) {
        target.destroy();
        Thread forceStopThread = new Thread(() -> {
            try {
                Thread.sleep(1500);
                target.exitValue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IllegalThreadStateException stillRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    target.destroyForcibly();
                } else {
                    target.destroy();
                }
            }
        }, "frpc-force-stop");
        forceStopThread.setDaemon(true);
        forceStopThread.start();
    }

    private void enterForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent activityIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getService(
                this,
                1,
                createStopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_running))
                .setContentText(getString(R.string.notification_description))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(
                        android.R.drawable.ic_media_pause,
                        getString(R.string.action_stop),
                        stopIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    @Override
    public void onDestroy() {
        Process currentProcess;
        synchronized (processLock) {
            stopping = true;
            currentProcess = process;
            process = null;
        }
        if (currentProcess != null) {
            terminateProcess(currentProcess);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
