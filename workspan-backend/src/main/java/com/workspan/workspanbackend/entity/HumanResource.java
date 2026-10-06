package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "human_resources")
@PrimaryKeyJoinColumn(name = "resource_id")
public class HumanResource extends Resource {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    public HumanResource() {
        setType(ResourceType.HUMAN);
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount v) {
        user = v;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String v) {
        jobTitle = v;
    }
}
