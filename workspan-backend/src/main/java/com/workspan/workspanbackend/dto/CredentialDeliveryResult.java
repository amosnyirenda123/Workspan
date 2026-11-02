package com.workspan.workspanbackend.dto;

import java.time.Instant;
import java.util.UUID;

public record CredentialDeliveryResult(UUID employeeId, String email, String status, String message, Instant sentAt) {}
