package com.example.frpcvisitor;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class FrpcConfigManager {
    public enum Mode {
        XTCP_VISITOR,
        TCP_PROXY,
        XTCP_PROXY
    }

    public static final class Config {
        public final Mode mode;
        public final String serverAddr;
        public final int serverPort;
        public final String token;
        public final String name;
        public final String secretKey;
        public final String localIP;
        public final int localPort;
        public final int bindPort;
        public final int remotePort;
        public final boolean keepTunnelOpen;

        private Config(
                Mode mode,
                String serverAddr,
                int serverPort,
                String token,
                String name,
                String secretKey,
                String localIP,
                int localPort,
                int bindPort,
                int remotePort,
                boolean keepTunnelOpen) {
            this.mode = mode;
            this.serverAddr = serverAddr;
            this.serverPort = serverPort;
            this.token = token;
            this.name = name;
            this.secretKey = secretKey;
            this.localIP = localIP;
            this.localPort = localPort;
            this.bindPort = bindPort;
            this.remotePort = remotePort;
            this.keepTunnelOpen = keepTunnelOpen;
        }

        public static Config xtcpVisitor(
                String serverAddr,
                int serverPort,
                String token,
                String serverName,
                String secretKey,
                int bindPort,
                boolean keepTunnelOpen) {
            return new Config(
                    Mode.XTCP_VISITOR,
                    serverAddr,
                    serverPort,
                    token,
                    serverName,
                    secretKey,
                    "",
                    0,
                    bindPort,
                    0,
                    keepTunnelOpen);
        }

        public static Config tcpProxy(
                String serverAddr,
                int serverPort,
                String token,
                String proxyName,
                String localIP,
                int localPort,
                int remotePort) {
            return new Config(
                    Mode.TCP_PROXY,
                    serverAddr,
                    serverPort,
                    token,
                    proxyName,
                    "",
                    localIP,
                    localPort,
                    0,
                    remotePort,
                    false);
        }

        public static Config xtcpProxy(
                String serverAddr,
                int serverPort,
                String token,
                String proxyName,
                String secretKey,
                String localIP,
                int localPort) {
            return new Config(
                    Mode.XTCP_PROXY,
                    serverAddr,
                    serverPort,
                    token,
                    proxyName,
                    secretKey,
                    localIP,
                    localPort,
                    0,
                    0,
                    false);
        }
    }

    private FrpcConfigManager() {
    }

    public static void validate(Config config) {
        requireText(config.serverAddr, "serverAddr");
        requirePort(config.serverPort, "serverPort");
        requireText(config.token, "auth.token");
        requireText(config.name, config.mode == Mode.XTCP_VISITOR ? "serverName" : "proxyName");

        switch (config.mode) {
            case XTCP_VISITOR:
                requireText(config.secretKey, "secretKey");
                requirePort(config.bindPort, "bindPort");
                break;
            case TCP_PROXY:
                requireLocalService(config);
                requirePort(config.remotePort, "remotePort");
                break;
            case XTCP_PROXY:
                requireLocalService(config);
                requireText(config.secretKey, "secretKey");
                break;
            default:
                throw new IllegalArgumentException("不支持的运行模式");
        }
    }

    public static File write(Context context, Config config) throws IOException {
        validate(config);

        File directory = new File(context.getFilesDir(), "frp");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建配置目录: " + directory);
        }

        File target = new File(directory, "frpc.toml");
        File temporary = new File(directory, "frpc.toml.tmp");
        byte[] data = toToml(config, getEmulatorDnsServer()).getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        }

        if (target.exists() && !target.delete()) {
            throw new IOException("无法替换旧配置文件");
        }
        if (!temporary.renameTo(target)) {
            throw new IOException("无法保存配置文件");
        }
        return target;
    }

    public static File getConfigFile(Context context) {
        return new File(new File(context.getFilesDir(), "frp"), "frpc.toml");
    }

    static String toToml(Config config) {
        return toToml(config, null);
    }

    private static String toToml(Config config, String dnsServer) {
        String dnsLine = dnsServer == null ? "" : "dnsServer = " + quote(dnsServer) + "\n";
        String common = "serverAddr = " + quote(config.serverAddr) + "\n"
                + "serverPort = " + config.serverPort + "\n"
                + dnsLine + "\n"
                + "auth.method = \"token\"\n"
                + "auth.token = " + quote(config.token) + "\n\n";

        switch (config.mode) {
            case XTCP_VISITOR:
                return common
                        + "[[visitors]]\n"
                        + "name = \"phone_xtcp_visitor\"\n"
                        + "type = \"xtcp\"\n"
                        + "serverName = " + quote(config.name) + "\n"
                        + "secretKey = " + quote(config.secretKey) + "\n"
                        + "bindAddr = \"127.0.0.1\"\n"
                        + "bindPort = " + config.bindPort + "\n"
                        + "keepTunnelOpen = " + config.keepTunnelOpen + "\n";
            case TCP_PROXY:
                return common
                        + "[[proxies]]\n"
                        + "name = " + quote(config.name) + "\n"
                        + "type = \"tcp\"\n"
                        + "localIP = " + quote(config.localIP) + "\n"
                        + "localPort = " + config.localPort + "\n"
                        + "remotePort = " + config.remotePort + "\n";
            case XTCP_PROXY:
                return common
                        + "[[proxies]]\n"
                        + "name = " + quote(config.name) + "\n"
                        + "type = \"xtcp\"\n"
                        + "secretKey = " + quote(config.secretKey) + "\n"
                        + "localIP = " + quote(config.localIP) + "\n"
                        + "localPort = " + config.localPort + "\n";
            default:
                throw new IllegalArgumentException("不支持的运行模式");
        }
    }

    private static String getEmulatorDnsServer() {
        boolean x8664 = Build.SUPPORTED_ABIS.length > 0
                && "x86_64".equals(Build.SUPPORTED_ABIS[0]);
        boolean emulator = Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.PRODUCT.contains("sdk");
        return x8664 && emulator ? "10.0.2.3" : null;
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\':
                    result.append("\\\\");
                    break;
                case '"':
                    result.append("\\\"");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
            }
        }
        return result.append('"').toString();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private static void requirePort(int port, String fieldName) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(fieldName + " 必须在 1 到 65535 之间");
        }
    }

    private static void requireLocalService(Config config) {
        requireText(config.localIP, "localIP");
        requirePort(config.localPort, "localPort");
    }
}
