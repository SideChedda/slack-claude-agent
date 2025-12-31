package com.autonomous.agent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThreadManagerService {

    @Autowired
    private SlackService slackService;

    private final Map<String, String> taskThreads = new ConcurrentHashMap<>();

    public String createThread(String channelId, String projectName, String taskDescription) {
        String message = String.format("*%s*\n\n%s", projectName, taskDescription);
        return slackService.postMessage(channelId, message);
    }

    public void postUpdate(String channelId, String threadTs, String message) {
        slackService.postMessageInThread(channelId, threadTs, message);
    }

    public void postStarting(String channelId, String threadTs, String model) {
        String message = String.format("*Starting task...*\nModel: %s", model);
        postUpdate(channelId, threadTs, message);
    }

    public void postProgress(String channelId, String threadTs, int step, int total, String description) {
        String message = String.format("*Working on step %d/%d...*\n%s", step, total, description);
        postUpdate(channelId, threadTs, message);
    }

    public void postQuestion(String channelId, String threadTs, String question, String... options) {
        StringBuilder message = new StringBuilder();
        message.append("*Question:*\n").append(question).append("\n\n");
        for (String option : options) {
            message.append("• ").append(option).append("\n");
        }
        postUpdate(channelId, threadTs, message.toString());
    }

    public void postCompletion(String channelId, String threadTs, String summary, String cost,
                                String filesChanged, String testResults, String prUrl) {
        StringBuilder message = new StringBuilder();
        message.append("*Task complete!*\n\n");
        message.append("*Summary:* ").append(summary).append("\n\n");
        message.append("*Cost:* ").append(cost).append("\n");
        message.append("*Changed:* ").append(filesChanged).append("\n");
        message.append("*Tests:* ").append(testResults).append("\n");
        message.append("*PR:* ").append(prUrl);
        postUpdate(channelId, threadTs, message.toString());
    }

    public void postFailure(String channelId, String threadTs, String error, String onFailure) {
        StringBuilder message = new StringBuilder();
        message.append("*Task failed*\n\n");
        message.append("*Error:* ").append(error).append("\n\n");

        if ("ask".equals(onFailure)) {
            message.append("What should I do?\n");
            message.append("• *retry* - Try again\n");
            message.append("• *stop* - Abandon task\n");
            message.append("• *draft* - Create draft PR anyway\n");
        }
        postUpdate(channelId, threadTs, message.toString());
    }

    public void registerThread(String taskId, String threadTs) {
        taskThreads.put(taskId, threadTs);
    }

    public String getThreadForTask(String taskId) {
        return taskThreads.get(taskId);
    }
}
