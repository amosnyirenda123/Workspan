package com.workspan.workspanbackend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record TaskCommentRequest(@NotBlank @Size(max = 2000) String body) {}
