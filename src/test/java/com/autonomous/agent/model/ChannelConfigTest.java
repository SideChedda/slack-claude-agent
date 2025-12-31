package com.autonomous.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChannelConfigTest {

    @Test
    void shouldHaveRequiredFields() {
        ChannelConfig config = new ChannelConfig();
        config.setChannelId("C0123ABCDEF");
        config.setChannelName("slack-agent");
        config.setRepo("git@github.com:user/repo.git");
        config.setClonePath("/app/workspaces/repo");

        assertEquals("C0123ABCDEF", config.getChannelId());
        assertEquals("slack-agent", config.getChannelName());
        assertEquals("git@github.com:user/repo.git", config.getRepo());
        assertEquals("/app/workspaces/repo", config.getClonePath());
    }

    @Test
    void shouldHaveDefaultValues() {
        ChannelConfig config = new ChannelConfig();

        assertEquals("main", config.getPrTarget());
        assertEquals("sonnet", config.getDefaultModel());
        assertEquals("ask", config.getOnFailure());
        assertEquals("ask", config.getOnConcurrent());
    }
}
