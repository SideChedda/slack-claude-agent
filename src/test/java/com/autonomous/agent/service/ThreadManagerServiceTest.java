package com.autonomous.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadManagerServiceTest {

    @Mock
    private SlackService slackService;

    @InjectMocks
    private ThreadManagerService threadManager;

    @Test
    void shouldCreateThreadAndReturnTimestamp() {
        when(slackService.postMessage(eq("C123"), anyString())).thenReturn("1234567890.123456");

        String threadTs = threadManager.createThread("C123", "test-project", "Add feature X");

        assertNotNull(threadTs);
        assertEquals("1234567890.123456", threadTs);
    }

    @Test
    void shouldPostUpdateToThread() {
        threadManager.postUpdate("C123", "1234567890.123456", "Working on step 1...");

        verify(slackService).postMessageInThread(eq("C123"), eq("1234567890.123456"), contains("Working on step 1"));
    }
}
