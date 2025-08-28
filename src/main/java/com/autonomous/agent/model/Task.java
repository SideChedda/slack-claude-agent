package com.autonomous.agent.model;

import lombok.Data;
import java.util.Date;

@Data
public class Task {
    private String id;
    private String description;
    private String status; // ASSIGNED, IN_PROGRESS, COMPLETED, FAILED
    private Date assignedAt;
    private Date startedAt;
    private Date completedAt;
    private String result;
    private String errorMessage;
}