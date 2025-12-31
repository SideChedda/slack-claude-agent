package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskExecutorServiceTest {

    @Mock
    private ConfigLoaderService configLoader;

    @Mock
    private ThreadManagerService threadManager;

    private TaskExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = new TaskExecutorService(configLoader, threadManager);
    }

    @Test
    void shouldRejectTaskWhenChannelNotConfigured() {
        when(configLoader.getConfigForChannel("C123")).thenReturn(Optional.empty());

        String result = executor.submitTask("C123", "Do something", null);

        assertTrue(result.contains("not configured"));
    }

    @Test
    void shouldDetectConcurrentTask() {
        ChannelConfig config = new ChannelConfig();
        config.setChannelId("C123");
        config.setChannelName("test");
        config.setOnConcurrent("ask");

        when(configLoader.getConfigForChannel("C123")).thenReturn(Optional.of(config));
        when(threadManager.createThread(anyString(), anyString(), anyString())).thenReturn("thread123");

        executor.submitTask("C123", "First task", null);

        assertTrue(executor.hasRunningTask("C123"));
    }

    @Test
    void shouldParseModelFromCommand() {
        assertEquals("opus", executor.parseModel("Add feature --model opus"));
        assertEquals("haiku", executor.parseModel("Fix bug --model haiku"));
        assertEquals("sonnet", executor.parseModel("Do something"));
    }

    @Test
    void shouldStripModelFromDescription() {
        assertEquals("Add feature", executor.stripModelFlag("Add feature --model opus"));
        assertEquals("Fix bug quickly", executor.stripModelFlag("Fix bug quickly --model haiku"));
        assertEquals("Do something", executor.stripModelFlag("Do something"));
    }
}
