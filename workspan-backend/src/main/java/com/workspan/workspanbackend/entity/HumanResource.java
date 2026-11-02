package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "human_resources", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@PrimaryKeyJoinColumn(name = "resource_id")
public class HumanResource extends Resource {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;
    @Column(name = "job_title", length = 100)
    private String jobTitle;
    @Column(length = 254)
    private String email;

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

    public String getEmail() { return email; }

    public void setEmail(String v) { email = v; }
}
