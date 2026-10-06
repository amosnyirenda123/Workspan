package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "spaces", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "name"}))
public class Space extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String name;

    public Space() {
    }

    public Space(Organization organization, String name) {
        this.organization = organization;
        this.name = name;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
