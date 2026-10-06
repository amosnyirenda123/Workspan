package com.ilot.ilotbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "email"}))
public class UserAccount extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @Email
    @NotBlank
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false, length = 24)
    private String status = "INVITED";
    private String invitationToken;
    private Instant invitationExpiresAt;

    public UserAccount() {
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization v) {
        organization = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        email = v;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String v) {
        passwordHash = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public String getInvitationToken() {
        return invitationToken;
    }

    public void setInvitationToken(String v) {
        invitationToken = v;
    }

    public Instant getInvitationExpiresAt() {
        return invitationExpiresAt;
    }

    public void setInvitationExpiresAt(Instant v) {
        invitationExpiresAt = v;
    }
}
