package com.ilot.ilotbackend.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="project_role_assignments",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","role_id","project_id"}))
public class ProjectRoleAssignment extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private UserAccount user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="role_id",nullable=false) private Role role;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id",nullable=false) private Project project;
    @Column(name="assigned_at",nullable=false,updatable=false) private Instant assignedAt;
    @PrePersist void assigned(){assignedAt=Instant.now();}
    public UserAccount getUser(){return user;} public void setUser(UserAccount v){user=v;}
    public Role getRole(){return role;} public void setRole(Role v){role=v;}
    public Project getProject(){return project;} public void setProject(Project v){project=v;}
    public Instant getAssignedAt(){return assignedAt;}
}
