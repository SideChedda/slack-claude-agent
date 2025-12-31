package com.autonomous.agent.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostEntry {
    private Instant timestamp;
    private String channelId;
    private String taskId;
    private String model;
    private long inputTokens;
    private long outputTokens;
    private double costUsd;
}
