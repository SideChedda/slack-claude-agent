package com.autonomous.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    private GitService gitService;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
    }

    @Test
    void shouldGenerateBranchName() {
        String branch = gitService.generateBranchName("slack-agent", "abc123");
        assertEquals("agent/slack-agent/abc123", branch);
    }

    @Test
    void shouldGenerateBranchNameWithCustomPrefix() {
        String branch = gitService.generateBranchName("my-project", "def456", "feature");
        assertEquals("feature/my-project/def456", branch);
    }

    @Test
    void shouldParseGitDiffStats() {
        String diffOutput = " 4 files changed, 234 insertions(+), 12 deletions(-)";
        String stats = gitService.parseDiffStats(diffOutput);
        assertEquals("4 files (+234 / -12)", stats);
    }
}
