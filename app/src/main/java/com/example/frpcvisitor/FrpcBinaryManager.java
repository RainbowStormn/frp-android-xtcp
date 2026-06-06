package com.example.frpcvisitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class FrpcBinaryManager {
    private static final String PACKAGED_BINARY_NAME = "libfrpc.so";
    private static final String PREFERENCES_NAME = "frpc_binary";
    private static final String KEY_STAGED_UPDATE_TIME = "staged_update_time";

    private FrpcBinaryManager() {
    }

    public static synchronized File prepare(Context context) throws IOException {
        File copiedBinary = copyAssetToPrivateDirectory(context);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return copiedBinary;
        }

        File packagedBinary = new File(
                context.getApplicationInfo().nativeLibraryDir,
                PACKAGED_BINARY_NAME);
        if (!packagedBinary.isFile()) {
            throw new IOException(
                    "APK 中没有 frpc。请把 frpc-arm64-v8a 放入 assets/bin 后重新构建 APK");
        }
        if (!packagedBinary.canExecute()) {
            throw new IOException("APK 中的 frpc 没有执行权限: " + packagedBinary);
        }
        return packagedBinary;
    }

    public static synchronized File copyAssetToPrivateDirectory(Context context) throws IOException {
        String assetPath = getAssetPath();
        File directory = new File(context.getFilesDir(), "bin");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建二进制目录: " + directory);
        }

        File target = new File(directory, "frpc");
        long appUpdateTime = getAppUpdateTime(context);
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        if (target.isFile()
                && target.length() > 0
                && target.canExecute()
                && preferences.getLong(KEY_STAGED_UPDATE_TIME, Long.MIN_VALUE)
                == appUpdateTime) {
            return target;
        }

        File temporary = new File(directory, "frpc.tmp");
        try (InputStream input = openAsset(context, assetPath);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
        }

        if (!temporary.setReadable(true, true)
                || !temporary.setWritable(true, true)
                || !temporary.setExecutable(true, true)
                || !temporary.canExecute()) {
            temporary.delete();
            throw new IOException("无法为 filesDir/bin/frpc 设置执行权限");
        }

        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("无法替换旧 frpc 二进制");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("无法保存 frpc 二进制");
        }
        preferences.edit().putLong(KEY_STAGED_UPDATE_TIME, appUpdateTime).apply();
        return target;
    }

    private static InputStream openAsset(Context context, String assetPath) throws IOException {
        try {
            return context.getAssets().open(assetPath);
        } catch (IOException exception) {
            throw new IOException(
                    "缺少 assets/" + assetPath + "，请先编译并放入 frpc 二进制",
                    exception);
        }
    }

    private static String getAssetPath() throws IOException {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return "bin/frpc-arm64-v8a";
            }
            if ("x86_64".equals(abi)) {
                return "bin/frpc-x86_64";
            }
        }
        throw new IOException(
                "不支持当前 CPU 架构: " + String.join(", ", Build.SUPPORTED_ABIS));
    }

    private static long getAppUpdateTime(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .lastUpdateTime;
        } catch (PackageManager.NameNotFoundException exception) {
            return -1;
        }
    }
}
