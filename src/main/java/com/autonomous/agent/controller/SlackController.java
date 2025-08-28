package com.autonomous.agent.controller;

import com.autonomous.agent.service.ClaudeAgentService;
import com.autonomous.agent.service.SlackService;
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
    private ClaudeAgentService claudeAgentService;

    @PostMapping("/events")
    public ResponseEntity<?> handleSlackEvent(@RequestBody Map<String, Object> payload) {
        // Handle URL verification challenge
        if (payload.containsKey("challenge")) {
            return ResponseEntity.ok(Map.of("challenge", payload.get("challenge")));
        }
        
        // Process Slack events
        slackService.processEvent(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/slash-commands")
    public ResponseEntity<?> handleSlashCommand(@RequestParam Map<String, String> params) {
        String command = params.get("command");
        String text = params.get("text");
        String userId = params.get("user_id");
        String channelId = params.get("channel_id");
        
        String response = claudeAgentService.processCommand(command, text, userId, channelId);
        
        return ResponseEntity.ok(Map.of(
            "response_type", "in_channel",
            "text", response
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}