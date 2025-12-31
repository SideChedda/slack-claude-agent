package com.autonomous.agent.controller;

import com.autonomous.agent.service.CostTrackerService;
import com.autonomous.agent.service.SlackService;
import com.autonomous.agent.service.TaskExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/slack")
public class SlackController {

    @Autowired
    private SlackService slackService;

    @Autowired
    private TaskExecutorService taskExecutor;

    @Autowired
    private CostTrackerService costTracker;

    @PostMapping("/events")
    public ResponseEntity<?> handleSlackEvent(@RequestBody Map<String, Object> payload) {
        if (payload.containsKey("challenge")) {
            return ResponseEntity.ok(Map.of("challenge", payload.get("challenge")));
        }

        slackService.processEvent(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/slash-commands")
    public ResponseEntity<?> handleSlashCommand(@RequestParam Map<String, String> params) {
        String command = params.get("command");
        String text = params.getOrDefault("text", "");
        String userId = params.get("user_id");
        String channelId = params.get("channel_id");

        String response = switch (command) {
            case "/agent-task" -> taskExecutor.submitTask(channelId, text, userId);
            case "/agent-stop" -> handleStop(channelId);
            case "/agent-status" -> handleStatus(channelId);
            case "/agent-budget" -> handleBudget();
            default -> "Unknown command: " + command;
        };

        return ResponseEntity.ok(Map.of(
            "response_type", "in_channel",
            "text", response
        ));
    }

    private String handleStop(String channelId) {
        boolean cancelled = taskExecutor.cancelTask(channelId);
        return cancelled ? "Task cancelled." : "No task running to cancel.";
    }

    private String handleStatus(String channelId) {
        return taskExecutor.getRunningTask(channelId)
            .map(task -> String.format("Running: *%s*\nModel: %s\nStarted: %s",
                task.getDescription(),
                task.getModel(),
                task.getStartedAt()))
            .orElse("No task running in this channel.");
    }

    private String handleBudget() {
        return String.format("Monthly budget: %s\n%s",
            costTracker.formatBudgetStatus(),
            costTracker.isOverBudgetThreshold() ? "Warning: Over 80% of budget used!" : "");
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}