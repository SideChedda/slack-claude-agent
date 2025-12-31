package com.autonomous.agent.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlackService {
    
    @Value("${slack.bot.token}")
    private String slackBotToken;
    
    private final Slack slack = Slack.getInstance();
    
    public void processEvent(Map<String, Object> payload) {
        Map<String, Object> event = (Map<String, Object>) payload.get("event");
        
        if (event != null) {
            String type = (String) event.get("type");
            
            if ("app_mention".equals(type) || "message".equals(type)) {
                String text = (String) event.get("text");
                String channel = (String) event.get("channel");
                String user = (String) event.get("user");
                
                // Process message through Claude
                handleMessage(text, channel, user);
            }
        }
    }
    
    private void handleMessage(String text, String channel, String user) {
        // This will be integrated with ClaudeAgentService
        sendMessage(channel, "Processing your request...");
    }
    
    public void sendMessage(String channel, String message) {
        try {
            MethodsClient methods = slack.methods(slackBotToken);

            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .text(message)
                .build();

            ChatPostMessageResponse response = methods.chatPostMessage(request);

            if (!response.isOk()) {
                System.err.println("Failed to send message: " + response.getError());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Posts a message to a channel and returns the message timestamp.
     * The timestamp can be used to create a thread.
     */
    public String postMessage(String channel, String message) {
        try {
            MethodsClient methods = slack.methods(slackBotToken);

            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .text(message)
                .build();

            ChatPostMessageResponse response = methods.chatPostMessage(request);

            if (response.isOk()) {
                return response.getTs();
            } else {
                System.err.println("Failed to post message: " + response.getError());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Posts a message as a reply in an existing thread.
     */
    public String postMessageInThread(String channel, String threadTs, String message) {
        try {
            MethodsClient methods = slack.methods(slackBotToken);

            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .threadTs(threadTs)
                .text(message)
                .build();

            ChatPostMessageResponse response = methods.chatPostMessage(request);

            if (response.isOk()) {
                return response.getTs();
            } else {
                System.err.println("Failed to post message in thread: " + response.getError());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}