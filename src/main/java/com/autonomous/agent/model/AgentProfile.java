package com.autonomous.agent.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AgentProfile {
    private String name;
    private String description;
    private String systemPrompt;
    private List<String> capabilities;
    private List<String> tools;
    private Map<String, Object> configuration;
    private int maxTokens = 4096;
    private double temperature = 0.7;
    private String modelVersion = "claude-3-sonnet";
    
    // Behavior flags
    private boolean autoExecute = false;
    private boolean requireConfirmation = true;
    private boolean verboseLogging = false;
    
    // Resource limits
    private long maxMemoryMB = 2048;
    private long maxExecutionTimeMinutes = 60;
    private int maxConcurrentTasks = 3;
}