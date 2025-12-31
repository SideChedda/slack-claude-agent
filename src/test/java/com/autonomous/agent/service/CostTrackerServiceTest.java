package com.autonomous.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CostTrackerServiceTest {

    private CostTrackerService costTracker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        costTracker = new CostTrackerService();
        costTracker.setDataPath(tempDir.toString());
        costTracker.setMonthlyBudget(500.0);
    }

    @Test
    void shouldCalculateCostForSonnet() {
        double cost = costTracker.calculateCost("sonnet", 10000, 5000);
        // Sonnet: $3/M input, $15/M output
        assertEquals(0.105, cost, 0.001);
    }

    @Test
    void shouldCalculateCostForOpus() {
        double cost = costTracker.calculateCost("opus", 10000, 5000);
        // Opus: $15/M input, $75/M output
        assertEquals(0.525, cost, 0.001);
    }

    @Test
    void shouldCalculateCostForHaiku() {
        double cost = costTracker.calculateCost("haiku", 10000, 5000);
        // Haiku: $0.25/M input, $1.25/M output
        assertEquals(0.00875, cost, 0.0001);
    }

    @Test
    void shouldTrackMonthlySpend() {
        costTracker.recordCost("C123", "task1", "sonnet", 10000, 5000);
        costTracker.recordCost("C123", "task2", "sonnet", 10000, 5000);

        double monthlySpend = costTracker.getMonthlySpend();
        assertEquals(0.21, monthlySpend, 0.001);
    }

    @Test
    void shouldCalculateBudgetPercentage() {
        costTracker.recordCost("C123", "task1", "opus", 100000, 50000);
        double percentage = costTracker.getBudgetPercentage();
        assertEquals(1.05, percentage, 0.01);
    }

    @Test
    void shouldAlertAt80Percent() {
        costTracker.setMonthlyBudget(10.0);
        costTracker.recordCost("C123", "task1", "opus", 100000, 50000);
        assertFalse(costTracker.isOverBudgetThreshold());

        costTracker.recordCost("C123", "task2", "opus", 100000, 50000);
        assertTrue(costTracker.isOverBudgetThreshold());
    }
}
