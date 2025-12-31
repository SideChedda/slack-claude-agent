package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderServiceTest {

    private ConfigLoaderService configLoader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        configLoader = new ConfigLoaderService();
        configLoader.setConfigPath(tempDir.toString());
    }

    @Test
    void shouldLoadConfigFromYaml() throws Exception {
        File configFile = tempDir.resolve("C0123ABC.yaml").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("channel_id: C0123ABC\n");
            writer.write("channel_name: test-project\n");
            writer.write("repo: git@github.com:user/test.git\n");
            writer.write("clone_path: /app/workspaces/test\n");
            writer.write("default_model: opus\n");
        }

        configLoader.loadConfigs();
        Optional<ChannelConfig> config = configLoader.getConfigForChannel("C0123ABC");

        assertTrue(config.isPresent());
        assertEquals("test-project", config.get().getChannelName());
        assertEquals("opus", config.get().getDefaultModel());
    }

    @Test
    void shouldReturnEmptyForUnknownChannel() {
        configLoader.loadConfigs();
        Optional<ChannelConfig> config = configLoader.getConfigForChannel("UNKNOWN");

        assertFalse(config.isPresent());
    }
}
