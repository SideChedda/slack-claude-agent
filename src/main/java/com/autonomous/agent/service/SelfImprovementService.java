package com.autonomous.agent.service;

import com.autonomous.agent.model.AgentProfile;
import com.autonomous.agent.model.Task;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class SelfImprovementService {
    
    private final Map<String, Double> performanceMetrics = new HashMap<>();
    
    public void analyzeTaskPerformance(Task task) {
        // Analyze task completion time
        if (task.getStartedAt() != null && task.getCompletedAt() != null) {
            long duration = task.getCompletedAt().getTime() - task.getStartedAt().getTime();
            updateMetric("avg_completion_time", duration);
        }
        
        // Track success rate
        if ("COMPLETED".equals(task.getStatus())) {
            updateMetric("success_rate", 1.0);
        } else if ("FAILED".equals(task.getStatus())) {
            updateMetric("success_rate", 0.0);
        }
    }
    
    public void optimizeProfile(AgentProfile profile, List<Task> recentTasks) {
        // Analyze recent task patterns
        double successRate = calculateSuccessRate(recentTasks);
        
        // Adjust temperature based on success rate
        if (successRate < 0.7) {
            // Lower temperature for more consistent outputs
            profile.setTemperature(Math.max(0.3, profile.getTemperature() - 0.1));
        } else if (successRate > 0.9) {
            // Slightly increase temperature for more creativity
            profile.setTemperature(Math.min(0.9, profile.getTemperature() + 0.05));
        }
        
        // Adjust max tokens based on average response length needs
        adjustMaxTokens(profile, recentTasks);
    }
    
    private double calculateSuccessRate(List<Task> tasks) {
        if (tasks.isEmpty()) return 0.5;
        
        long successful = tasks.stream()
            .filter(t -> "COMPLETED".equals(t.getStatus()))
            .count();
            
        return (double) successful / tasks.size();
    }
    
    private void adjustMaxTokens(AgentProfile profile, List<Task> tasks) {
        // Analyze if tasks are getting truncated or if we're using too many tokens
        // This is a simplified implementation
        boolean needsMoreTokens = tasks.stream()
            .anyMatch(t -> t.getResult() != null && 
                          t.getResult().length() > profile.getMaxTokens() * 3);
        
        if (needsMoreTokens) {
            profile.setMaxTokens(Math.min(8192, profile.getMaxTokens() + 512));
        }
    }
    
    private void updateMetric(String metric, double value) {
        performanceMetrics.merge(metric, value, (old, new_val) -> (old + new_val) / 2);
    }
    
    public Map<String, Double> getPerformanceMetrics() {
        return new HashMap<>(performanceMetrics);
    }
}