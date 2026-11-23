package com.workspan.workspanbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Durable event snapshots remain available even after the task itself is deleted. */
@Entity
@Table(name = "task_activity")
public class TaskActivity extends BaseEntity {
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;
    @Column(nullable = false, length = 32)
    private String action;
    @NotBlank @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String summary;
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
