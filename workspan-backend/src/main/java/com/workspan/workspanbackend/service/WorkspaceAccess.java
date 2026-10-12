package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.AuthResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class WorkspaceAccess {
    private final AuthService auth;

    public WorkspaceAccess(AuthService auth) { this.auth = auth; }

    public UUID organizationId(HttpSession session) {
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof UUID userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        AuthResponse user = auth.currentUser(userId);
        return user.organizationId();
    }
}
