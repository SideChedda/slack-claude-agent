package com.autonomous.agent.service;

import com.autonomous.agent.model.AgentInstance;
import com.autonomous.agent.model.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class ClaudeAgentService {
    
    @Value("${claude.code.path:/usr/local/bin/claude}")
    private String claudeCodePath;
    
    @Value("${claude.code.workspace:/tmp/claude-workspace}")
    private String workspacePath;
    
    @Autowired
    private ProfileService profileService;
    
    private final Map<String, AgentInstance> activeAgents = new ConcurrentHashMap<>();
    private final Map<String, Process> agentProcesses = new ConcurrentHashMap<>();
    
    public String processCommand(String command, String text, String userId, String channelId) {
        switch (command) {
            case "/agent-start":
                return startAgent(text, channelId);
            case "/agent-stop":
                return stopAgent(channelId);
            case "/agent-status":
                return getAgentStatus(channelId);
            case "/agent-task":
                return assignTask(channelId, text);
            default:
                return "Unknown command: " + command;
        }
    }
    
    private String startAgent(String profileName, String channelId) {
        if (activeAgents.containsKey(channelId)) {
            return "An agent is already running in this channel";
        }
        
        AgentInstance agent = new AgentInstance();
        agent.setId(UUID.randomUUID().toString());
        agent.setName("Agent-" + profileName);
        agent.setProfile(profileService.loadProfile(profileName));
        agent.setSlackChannel(channelId);
        agent.setStatus("RUNNING");
        agent.setStartedAt(new Date());
        
        activeAgents.put(channelId, agent);
        
        return "Started " + agent.getName() + " with profile: " + profileName;
    }
    
    private String stopAgent(String channelId) {
        AgentInstance agent = activeAgents.remove(channelId);
        if (agent == null) {
            return "No agent is running in this channel";
        }
        
        agent.setStatus("STOPPED");
        agent.setStoppedAt(new Date());
        
        return "Stopped " + agent.getName();
    }
    
    private String getAgentStatus(String channelId) {
        AgentInstance agent = activeAgents.get(channelId);
        if (agent == null) {
            return "No agent is running in this channel";
        }
        
        StringBuilder status = new StringBuilder();
        status.append("Agent: ").append(agent.getName()).append("\n");
        status.append("Status: ").append(agent.getStatus()).append("\n");
        status.append("Profile: ").append(agent.getProfile().getName()).append("\n");
        status.append("Active Tasks: ").append(agent.getTasks().size());
        
        return status.toString();
    }
    
    private String assignTask(String channelId, String taskDescription) {
        AgentInstance agent = activeAgents.get(channelId);
        if (agent == null) {
            return "No agent is running in this channel. Start an agent first with /agent-start";
        }
        
        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setDescription(taskDescription);
        task.setStatus("IN_PROGRESS");
        task.setAssignedAt(new Date());
        task.setStartedAt(new Date());
        
        agent.getTasks().add(task);
        
        // Process with Claude Code CLI
        String claudeResponse = callClaudeCode(taskDescription, agent);
        
        if (claudeResponse != null && !claudeResponse.isEmpty()) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(new Date());
            task.setResult(claudeResponse);
        } else {
            task.setStatus("FAILED");
            task.setCompletedAt(new Date());
            task.setErrorMessage("Failed to get response from Claude Code");
            return "Task failed: Could not get response from Claude Code";
        }
        
        return claudeResponse;
    }
    
    private String callClaudeCode(String prompt, AgentInstance agent) {
        try {
            // Create workspace directory for this agent if it doesn't exist
            File agentWorkspace = new File(workspacePath, agent.getId());
            if (!agentWorkspace.exists()) {
                agentWorkspace.mkdirs();
            }
            
            // Write prompt to a temporary file
            File promptFile = new File(agentWorkspace, "prompt.txt");
            try (FileWriter writer = new FileWriter(promptFile)) {
                // Include system prompt if available
                if (agent.getProfile().getSystemPrompt() != null) {
                    writer.write("System: " + agent.getProfile().getSystemPrompt() + "\n\n");
                }
                writer.write(prompt);
            }
            
            // Build Claude Code command
            List<String> command = new ArrayList<>();
            command.add(claudeCodePath);
            command.add("--model");
            command.add("claude-3-sonnet");
            command.add("--max-tokens");
            command.add(String.valueOf(agent.getProfile().getMaxTokens()));
            
            // Execute Claude Code
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(agentWorkspace);
            pb.redirectInput(promptFile);
            
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for completion with timeout
            boolean finished = process.waitFor(2, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return "Task timed out after 2 minutes";
            }
            
            return output.toString().trim();
            
        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing Claude Code: " + e.getMessage();
        }
    }
}