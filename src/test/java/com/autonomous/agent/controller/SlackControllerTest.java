package com.autonomous.agent.controller;

import com.autonomous.agent.service.CostTrackerService;
import com.autonomous.agent.service.SlackService;
import com.autonomous.agent.service.TaskExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SlackController.class)
class SlackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskExecutorService taskExecutor;

    @MockBean
    private CostTrackerService costTracker;

    @MockBean
    private SlackService slackService;

    @Test
    void shouldHandleAgentTaskCommand() throws Exception {
        when(taskExecutor.submitTask(eq("C123"), eq("Add feature X"), eq("U456")))
            .thenReturn("Starting task...");

        mockMvc.perform(post("/slack/slash-commands")
                .param("command", "/agent-task")
                .param("text", "Add feature X")
                .param("user_id", "U456")
                .param("channel_id", "C123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("Starting task..."));
    }

    @Test
    void shouldHandleAgentStopCommand() throws Exception {
        when(taskExecutor.cancelTask("C123")).thenReturn(true);

        mockMvc.perform(post("/slack/slash-commands")
                .param("command", "/agent-stop")
                .param("text", "")
                .param("user_id", "U456")
                .param("channel_id", "C123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("Task cancelled."));
    }

    @Test
    void shouldHandleAgentStatusCommand() throws Exception {
        when(taskExecutor.hasRunningTask("C123")).thenReturn(false);
        when(taskExecutor.getRunningTask("C123")).thenReturn(Optional.empty());

        mockMvc.perform(post("/slack/slash-commands")
                .param("command", "/agent-status")
                .param("text", "")
                .param("user_id", "U456")
                .param("channel_id", "C123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("No task running in this channel."));
    }
}
