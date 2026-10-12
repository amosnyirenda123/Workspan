package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.OrganizationResponse;
import com.workspan.workspanbackend.service.AuthService;
import com.workspan.workspanbackend.service.OrganizationService;
import com.workspan.workspanbackend.dto.AuthResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService service;
    private final AuthService auth;

    public OrganizationController(OrganizationService service, AuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping
    public List<OrganizationResponse> findAll(HttpSession session) {
        AuthResponse user = currentUser(session);
        return service.findForOrganization(user.organizationId());
    }

    @GetMapping("/{id}")
    public OrganizationResponse findById(@PathVariable UUID id, HttpSession session) {
        AuthResponse user = currentUser(session);
        return service.findById(id, user.organizationId());
    }

    private AuthResponse currentUser(HttpSession session) {
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof UUID userId)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return auth.currentUser(userId);
    }
}
