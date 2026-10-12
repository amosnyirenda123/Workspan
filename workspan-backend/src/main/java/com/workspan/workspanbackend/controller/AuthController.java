package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.AuthResponse;
import com.workspan.workspanbackend.dto.LoginRequest;
import com.workspan.workspanbackend.dto.RegisterRequest;
import com.workspan.workspanbackend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String USER_ID = "authenticatedUserId";
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        AuthResponse user = auth.register(request);
        servletRequest.getSession(true).setAttribute(USER_ID, user.userId());
        return user;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthResponse user = auth.authenticate(request);
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute(USER_ID, user.userId());
        return user;
    }

    @GetMapping("/me")
    public AuthResponse me(HttpSession session) {
        Object id = session.getAttribute(USER_ID);
        if (!(id instanceof UUID userId)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return auth.currentUser(userId);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) { session.invalidate(); }
}
