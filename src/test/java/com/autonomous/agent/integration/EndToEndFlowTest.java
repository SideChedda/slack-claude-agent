package com.autonomous.agent.integration;

import com.autonomous.agent.model.ChannelConfig;
import com.autonomous.agent.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class EndToEndFlowTest {

    @Autowired
    private TaskExecutorService taskExecutor;

    @MockBean
    private SlackService slackService;

    @MockBean
    private ConfigLoaderService configLoader;

    @BeforeEach
    void setUp() {
        when(slackService.postMessage(anyString(), anyString())).thenReturn("thread123");
        when(slackService.postMessageInThread(anyString(), anyString(), anyString())).thenReturn("msg123");
    }

    @Test
    void shouldRejectUnconfiguredChannel() {
        when(configLoader.getConfigForChannel("UNKNOWN")).thenReturn(Optional.empty());

        String result = taskExecutor.submitTask("UNKNOWN", "Do something", "U123");

        assertTrue(result.contains("not configured"));
    }

    @Test
    void shouldParseModelFromCommand() {
        assertEquals("opus", taskExecutor.parseModel("--model opus Add feature"));
        assertEquals("haiku", taskExecutor.parseModel("Fix bug --model haiku"));
        assertEquals("sonnet", taskExecutor.parseModel("Simple task"));
    }

    @Test
    void shouldStripModelFlag() {
        assertEquals("Add feature", taskExecutor.stripModelFlag("--model opus Add feature").trim());
        assertEquals("Fix bug", taskExecutor.stripModelFlag("Fix bug --model haiku").trim());
    }
}
