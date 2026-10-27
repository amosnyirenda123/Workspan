package com.workspan.workspanbackend.dto;

import java.util.List;

public record CredentialBatchResponse(int sent, int skipped, int failed, List<CredentialDeliveryResult> results) {}
