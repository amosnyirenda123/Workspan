package com.ilot.ilotbackend.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="space_role_assignments",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","role_id","space_id"}))
public class SpaceRoleAssignment extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private UserAccount user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="role_id",nullable=false) private Role role;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="space_id",nullable=false) private Space space;
    @Column(name="assigned_at",nullable=false,updatable=false) private Instant assignedAt;
    @PrePersist void assigned(){assignedAt=Instant.now();}
    public UserAccount getUser(){return user;} public void setUser(UserAccount v){user=v;}
    public Role getRole(){return role;} public void setRole(Role v){role=v;}
    public Space getSpace(){return space;} public void setSpace(Space v){space=v;}
    public Instant getAssignedAt(){return assignedAt;}
}
