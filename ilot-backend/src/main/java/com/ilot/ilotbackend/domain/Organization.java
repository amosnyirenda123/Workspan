package com.ilot.ilotbackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity @Table(name = "organizations")
public class Organization extends BaseEntity {
    @NotBlank @Size(max=50) @Column(nullable=false, length=50, unique=true)
    private String name;
    public Organization() {}
    public Organization(String name) { this.name = name; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
