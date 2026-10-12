package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpaceRequest(@NotBlank @Size(max = 50) String name) {}
