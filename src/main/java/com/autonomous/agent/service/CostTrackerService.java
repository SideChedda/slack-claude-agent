package com.autonomous.agent.service;

import com.autonomous.agent.model.CostEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

@Service
public class CostTrackerService {

    @Value("${agent.data.path:data}")
    private String dataPath;

    @Value("${agent.monthly.budget:500.0}")
    private double monthlyBudget;

    private final ObjectMapper mapper;
    private final List<CostEntry> currentMonthCosts = Collections.synchronizedList(new ArrayList<>());

    private static final Map<String, double[]> MODEL_PRICING = Map.of(
        "haiku", new double[]{0.25, 1.25},
        "sonnet", new double[]{3.0, 15.0},
        "opus", new double[]{15.0, 75.0}
    );

    public CostTrackerService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public void setDataPath(String path) {
        this.dataPath = path;
    }

    public void setMonthlyBudget(double budget) {
        this.monthlyBudget = budget;
    }

    @PostConstruct
    public void init() {
        loadCurrentMonthCosts();
    }

    public double calculateCost(String model, long inputTokens, long outputTokens) {
        double[] pricing = MODEL_PRICING.getOrDefault(model.toLowerCase(), MODEL_PRICING.get("sonnet"));
        double inputCost = (inputTokens * pricing[0]) / 1_000_000.0;
        double outputCost = (outputTokens * pricing[1]) / 1_000_000.0;
        return inputCost + outputCost;
    }

    public CostEntry recordCost(String channelId, String taskId, String model, long inputTokens, long outputTokens) {
        double cost = calculateCost(model, inputTokens, outputTokens);

        CostEntry entry = CostEntry.builder()
            .timestamp(Instant.now())
            .channelId(channelId)
            .taskId(taskId)
            .model(model)
            .inputTokens(inputTokens)
            .outputTokens(outputTokens)
            .costUsd(cost)
            .build();

        currentMonthCosts.add(entry);
        persistEntry(entry);

        return entry;
    }

    public double getMonthlySpend() {
        return currentMonthCosts.stream()
            .mapToDouble(CostEntry::getCostUsd)
            .sum();
    }

    public double getBudgetPercentage() {
        return (getMonthlySpend() / monthlyBudget) * 100.0;
    }

    public boolean isOverBudgetThreshold() {
        return getBudgetPercentage() >= 80.0;
    }

    public String formatCostSummary(CostEntry entry) {
        return String.format("$%.2f (%s, %dK tokens)",
            entry.getCostUsd(),
            entry.getModel(),
            (entry.getInputTokens() + entry.getOutputTokens()) / 1000);
    }

    public String formatBudgetStatus() {
        return String.format("$%.2f / $%.0f (%.0f%%)",
            getMonthlySpend(),
            monthlyBudget,
            getBudgetPercentage());
    }

    private void persistEntry(CostEntry entry) {
        try {
            Path costsFile = Paths.get(dataPath, "costs.jsonl");
            Files.createDirectories(costsFile.getParent());

            String json = mapper.writeValueAsString(entry);
            Files.writeString(costsFile, json + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to persist cost entry: " + e.getMessage());
        }
    }

    private void loadCurrentMonthCosts() {
        try {
            Path costsFile = Paths.get(dataPath, "costs.jsonl");
            if (!Files.exists(costsFile)) return;

            YearMonth currentMonth = YearMonth.now();

            Files.lines(costsFile).forEach(line -> {
                try {
                    CostEntry entry = mapper.readValue(line, CostEntry.class);
                    YearMonth entryMonth = YearMonth.from(entry.getTimestamp().atZone(java.time.ZoneOffset.UTC));
                    if (entryMonth.equals(currentMonth)) {
                        currentMonthCosts.add(entry);
                    }
                } catch (Exception e) {
                    // Skip malformed entries
                }
            });
        } catch (IOException e) {
            System.err.println("Failed to load costs: " + e.getMessage());
        }
    }
}
