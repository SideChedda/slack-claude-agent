package com.autonomous.agent.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class AgentInstance {
    private String id;
    private String name;
    private AgentProfile profile;
    private String workspacePath;
    private String slackChannel;
    private String status; // STARTING, RUNNING, STOPPED, FAILED
    private Date startedAt;
    private Date stoppedAt;
    private List<Task> tasks = new ArrayList<>();
}