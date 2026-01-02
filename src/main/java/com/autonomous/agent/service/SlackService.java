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

            // Ignore bot messages to prevent loops
            if (event.containsKey("bot_id") || event.containsKey("bot_profile")) {
                return;
            }

            // Ignore message subtypes (edits, deletes, etc)
            if (event.containsKey("subtype")) {
                return;
            }

            // Only respond to direct @mentions for now
            // Slash commands are handled separately via /slash-commands endpoint
            if ("app_mention".equals(type)) {
                String text = (String) event.get("text");
                String channel = (String) event.get("channel");
                String threadTs = (String) event.get("thread_ts");

                // Reply in thread if it's a thread message
                if (threadTs != null) {
                    postMessageInThread(channel, threadTs, "I received your mention. Use `/agent-task` to submit tasks.");
                } else {
                    sendMessage(channel, "Hi! Use `/agent-task <description>` to submit a task.");
                }
            }
            // Don't auto-respond to regular messages - only slash commands trigger tasks
        }
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