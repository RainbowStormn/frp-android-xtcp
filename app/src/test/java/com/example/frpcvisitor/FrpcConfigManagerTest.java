package com.example.frpcvisitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class FrpcConfigManagerTest {
    @Test
    public void toTomlWritesExpectedVisitorConfiguration() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.xtcpVisitor(
                "frps.example.com",
                7000,
                "token-value",
                "home_ssh",
                "secret-value",
                6000,
                true);

        assertEquals(
                "serverAddr = \"frps.example.com\"\n"
                        + "serverPort = 7000\n\n"
                        + "auth.method = \"token\"\n"
                        + "auth.token = \"token-value\"\n\n"
                        + "[[visitors]]\n"
                        + "name = \"phone_xtcp_visitor\"\n"
                        + "type = \"xtcp\"\n"
                        + "serverName = \"home_ssh\"\n"
                        + "secretKey = \"secret-value\"\n"
                        + "bindAddr = \"127.0.0.1\"\n"
                        + "bindPort = 6000\n"
                        + "keepTunnelOpen = true\n",
                FrpcConfigManager.toToml(config));
    }

    @Test
    public void toTomlEscapesQuotedValues() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.xtcpVisitor(
                "host\"name",
                7000,
                "token\\value",
                "home\nssh",
                "secret\tvalue",
                6000,
                false);

        String toml = FrpcConfigManager.toToml(config);

        org.junit.Assert.assertTrue(toml.contains("serverAddr = \"host\\\"name\""));
        org.junit.Assert.assertTrue(toml.contains("auth.token = \"token\\\\value\""));
        org.junit.Assert.assertTrue(toml.contains("serverName = \"home\\nssh\""));
        org.junit.Assert.assertTrue(toml.contains("secretKey = \"secret\\tvalue\""));
    }

    @Test
    public void validateRejectsInvalidPort() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.xtcpVisitor(
                "frps.example.com",
                70000,
                "token",
                "home_ssh",
                "secret",
                6000,
                false);

        assertThrows(
                IllegalArgumentException.class,
                () -> FrpcConfigManager.validate(config));
    }

    @Test
    public void validateRejectsBlankSecret() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.xtcpVisitor(
                "frps.example.com",
                7000,
                "token",
                "home_ssh",
                " ",
                6000,
                false);

        assertThrows(
                IllegalArgumentException.class,
                () -> FrpcConfigManager.validate(config));
    }

    @Test
    public void toTomlWritesTcpProxyConfiguration() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.tcpProxy(
                "frps.example.com",
                7000,
                "token-value",
                "phone_web",
                "127.0.0.1",
                8080,
                18080);

        assertEquals(
                "serverAddr = \"frps.example.com\"\n"
                        + "serverPort = 7000\n\n"
                        + "auth.method = \"token\"\n"
                        + "auth.token = \"token-value\"\n\n"
                        + "[[proxies]]\n"
                        + "name = \"phone_web\"\n"
                        + "type = \"tcp\"\n"
                        + "localIP = \"127.0.0.1\"\n"
                        + "localPort = 8080\n"
                        + "remotePort = 18080\n",
                FrpcConfigManager.toToml(config));
    }

    @Test
    public void toTomlWritesXtcpProxyConfiguration() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.xtcpProxy(
                "frps.example.com",
                7000,
                "token-value",
                "phone_ssh",
                "secret-value",
                "127.0.0.1",
                8022);

        assertEquals(
                "serverAddr = \"frps.example.com\"\n"
                        + "serverPort = 7000\n\n"
                        + "auth.method = \"token\"\n"
                        + "auth.token = \"token-value\"\n\n"
                        + "[[proxies]]\n"
                        + "name = \"phone_ssh\"\n"
                        + "type = \"xtcp\"\n"
                        + "secretKey = \"secret-value\"\n"
                        + "localIP = \"127.0.0.1\"\n"
                        + "localPort = 8022\n",
                FrpcConfigManager.toToml(config));
    }

    @Test
    public void validateTcpProxyRejectsInvalidRemotePort() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.tcpProxy(
                "frps.example.com",
                7000,
                "token",
                "phone_web",
                "127.0.0.1",
                8080,
                0);

        assertThrows(
                IllegalArgumentException.class,
                () -> FrpcConfigManager.validate(config));
    }

    @Test
    public void validateXtcpProxyRejectsBlankLocalIp() {
        FrpcConfigManager.Config config = FrpcConfigManager.Config.xtcpProxy(
                "frps.example.com",
                7000,
                "token",
                "phone_ssh",
                "secret",
                " ",
                8022);

        assertThrows(
                IllegalArgumentException.class,
                () -> FrpcConfigManager.validate(config));
    }
}
