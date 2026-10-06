package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "resources")
@Inheritance(strategy = InheritanceType.JOINED)
public class Resource extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ResourceType type;
    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 24)
    private String status = "AVAILABLE";

    public Resource() {
    }

    public Resource(Space space, ResourceType type, String name) {
        this.space = space;
        this.type = type;
        this.name = name;
    }

    public Space getSpace() {
        return space;
    }

    public void setSpace(Space v) {
        space = v;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType v) {
        type = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }
}
