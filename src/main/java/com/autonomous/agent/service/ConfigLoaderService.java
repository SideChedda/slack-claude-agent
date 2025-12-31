package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfigLoaderService {

    @Value("${agent.config.path:config/channels}")
    private String configPath;

    private final Map<String, ChannelConfig> configs = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;

    public ConfigLoaderService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public void setConfigPath(String path) {
        this.configPath = path;
    }

    @PostConstruct
    public void loadConfigs() {
        configs.clear();
        File configDir = new File(configPath);

        if (!configDir.exists() || !configDir.isDirectory()) {
            System.out.println("Config directory not found: " + configPath);
            return;
        }

        File[] yamlFiles = configDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (yamlFiles == null) return;

        for (File file : yamlFiles) {
            try {
                ChannelConfig config = yamlMapper.readValue(file, ChannelConfig.class);
                if (config.getChannelId() != null) {
                    configs.put(config.getChannelId(), config);
                    System.out.println("Loaded config for channel: " + config.getChannelId());
                }
            } catch (Exception e) {
                System.err.println("Failed to load config from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public Optional<ChannelConfig> getConfigForChannel(String channelId) {
        return Optional.ofNullable(configs.get(channelId));
    }

    public Map<String, ChannelConfig> getAllConfigs() {
        return Map.copyOf(configs);
    }
}
