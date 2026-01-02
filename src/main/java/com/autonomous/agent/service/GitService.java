package com.autonomous.agent.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitService {

    private static final Pattern DIFF_STATS_PATTERN =
        Pattern.compile("(\\d+) files? changed(?:, (\\d+) insertions?\\(\\+\\))?(?:, (\\d+) deletions?\\(-\\))?");

    public String generateBranchName(String channelName, String taskId) {
        return generateBranchName(channelName, taskId, "agent");
    }

    public String generateBranchName(String channelName, String taskId, String prefix) {
        return String.format("%s/%s/%s", prefix, channelName, taskId);
    }

    public boolean ensureRepoCloned(String repoUrl, String clonePath) {
        File repoDir = new File(clonePath);

        // If directory exists and has .git, repo is already cloned
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            // Pull latest changes
            runGitCommand(clonePath, "git", "fetch", "origin");
            runGitCommand(clonePath, "git", "checkout", "main");
            runGitCommand(clonePath, "git", "pull", "origin", "main");
            return true;
        }

        // Create parent directory if needed
        repoDir.getParentFile().mkdirs();

        // Clone the repo
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "clone", repoUrl, clonePath);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                System.err.println("Clone timed out");
                return false;
            }

            if (process.exitValue() != 0) {
                System.err.println("Clone failed: " + output);
                return false;
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean createBranch(String repoPath, String branchName) {
        // Make sure we're on main first
        runGitCommand(repoPath, "git", "checkout", "main");
        return runGitCommand(repoPath, "git", "checkout", "-b", branchName);
    }

    public boolean commitAll(String repoPath, String message) {
        runGitCommand(repoPath, "git", "add", "-A");
        return runGitCommand(repoPath, "git", "commit", "-m", message);
    }

    public boolean push(String repoPath, String branchName) {
        return runGitCommand(repoPath, "git", "push", "-u", "origin", branchName);
    }

    public String createPullRequest(String repoPath, String title, String body, String targetBranch) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "gh", "pr", "create",
                "--title", title,
                "--body", body,
                "--base", targetBranch
            );
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (finished && process.exitValue() == 0) {
                return output.trim();
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDiffStats(String repoPath, String baseBranch) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "git", "diff", "--stat", baseBranch + "..HEAD"
            );
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            process.waitFor(30, TimeUnit.SECONDS);

            return parseDiffStats(output);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String parseDiffStats(String diffOutput) {
        Matcher matcher = DIFF_STATS_PATTERN.matcher(diffOutput);
        if (matcher.find()) {
            int files = Integer.parseInt(matcher.group(1));
            int insertions = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int deletions = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            return String.format("%d files (+%d / -%d)", files, insertions, deletions);
        }
        return "no changes";
    }

    public String runTests(String repoPath, String testCommand) {
        try {
            String[] cmdParts = testCommand.split("\\s+");
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                return "Tests timed out after 5 minutes";
            }

            if (process.exitValue() == 0) {
                return parseTestOutput(output);
            } else {
                return "Tests failed:\n" + output;
            }
        } catch (Exception e) {
            return "Failed to run tests: " + e.getMessage();
        }
    }

    private String parseTestOutput(String output) {
        if (output.contains("BUILD SUCCESSFUL") || output.contains("Tests passed")) {
            return "All tests passed";
        }
        return output.substring(0, Math.min(500, output.length()));
    }

    private boolean runGitCommand(String repoPath, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            readProcessOutput(process);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
