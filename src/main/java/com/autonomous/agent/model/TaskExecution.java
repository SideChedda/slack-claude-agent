package com.autonomous.agent.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecution {
    private String taskId;
    private String channelId;
    private String description;
    private String model;
    private String threadTs;
    private String branchName;
    private Instant startedAt;
    private String status;  // PENDING, RUNNING, WAITING_RESPONSE, COMPLETED, FAILED, CANCELLED
    private transient Process process;
    private transient CompletableFuture<String> future;
}
