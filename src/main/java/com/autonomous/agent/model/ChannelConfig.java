package com.autonomous.agent.model;

import lombok.Data;
import java.util.List;

@Data
public class ChannelConfig {
    private String channelId;
    private String channelName;
    private String repo;
    private String clonePath;

    // Git settings
    private String prTarget = "main";
    private String branchPrefix;

    // Behavior
    private String defaultModel = "sonnet";
    private String onFailure = "ask";      // ask | stop | retry | draft_pr
    private String onConcurrent = "ask";   // ask | queue | parallel | reject

    // Optional
    private List<String> setupCommands;
    private String profile;
}
