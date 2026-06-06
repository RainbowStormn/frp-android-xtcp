package com.example.frpcvisitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class FrpcConfigManagerTest {
    @Test
    public void toTomlWritesExpectedVisitorConfiguration() {
        FrpcConfigManager.Config config = new FrpcConfigManager.Config(
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
        FrpcConfigManager.Config config = new FrpcConfigManager.Config(
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
        FrpcConfigManager.Config config = new FrpcConfigManager.Config(
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
        FrpcConfigManager.Config config = new FrpcConfigManager.Config(
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
}
