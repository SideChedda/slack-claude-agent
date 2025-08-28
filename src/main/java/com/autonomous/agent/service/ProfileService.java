package com.autonomous.agent.service;

import com.autonomous.agent.model.AgentProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProfileService {
    
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, AgentProfile> profileCache = new HashMap<>();
    
    public AgentProfile loadProfile(String profileName) {
        if (profileCache.containsKey(profileName)) {
            return profileCache.get(profileName);
        }
        
        try {
            File profileFile = new File("agent-profiles/" + profileName + ".yaml");
            if (!profileFile.exists()) {
                profileFile = new File("agent-profiles/default.yaml");
            }
            
            AgentProfile profile = yamlMapper.readValue(profileFile, AgentProfile.class);
            profileCache.put(profileName, profile);
            return profile;
            
        } catch (IOException e) {
            e.printStackTrace();
            return getDefaultProfile();
        }
    }
    
    private AgentProfile getDefaultProfile() {
        AgentProfile profile = new AgentProfile();
        profile.setName("default");
        profile.setDescription("Default agent profile");
        profile.setSystemPrompt("You are a helpful assistant.");
        profile.setMaxTokens(4096);
        profile.setTemperature(0.7);
        return profile;
    }
}