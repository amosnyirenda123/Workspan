package com.workspan.workspanbackend.dto;
import java.util.UUID;
public record AccessMemberResponse(UUID userId, String displayName, String email) {}
