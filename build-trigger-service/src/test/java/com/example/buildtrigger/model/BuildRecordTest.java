package com.example.buildtrigger.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class BuildRecordTest {

    @Test
    void testBuildRecordFields() {
        BuildRecord r = new BuildRecord();
        r.setPipelineId(42L);
        r.setAppName("test-app");
        r.setEnvironment("dev");
        r.setAppType("service");
        r.setGitlabProjectId(2L);
        r.setStatus("running");
        r.setTriggeredAt(LocalDateTime.of(2025, 1, 15, 10, 30));
        r.setTriggeredBy("user1");
        r.setWebUrl("https://gitlab.example.com/pipeline/42");

        assertEquals(42L, r.getPipelineId());
        assertEquals("test-app", r.getAppName());
        assertEquals("dev", r.getEnvironment());
        assertEquals("service", r.getAppType());
        assertEquals(2L, r.getGitlabProjectId());
        assertEquals("running", r.getStatus());
        assertEquals("user1", r.getTriggeredBy());
        assertNotNull(r.getTriggeredAt());
    }

    @Test
    void testBuildResponseFromRecord() {
        BuildRecord r = new BuildRecord();
        r.setPipelineId(100L);
        r.setAppName("my-app");
        r.setEnvironment("staging");
        r.setStatus("success");
        r.setWebUrl("https://example.com/pipeline/100");
        r.setTriggeredAt(LocalDateTime.now());
        r.setDuration(120.5);

        BuildResponse resp = new BuildResponse(r);
        assertEquals(100L, resp.getPipelineId());
        assertEquals("my-app", resp.getAppName());
        assertEquals("staging", resp.getEnvironment());
        assertEquals("success", resp.getStatus());
        assertEquals(120.5, resp.getDuration());
    }
}
