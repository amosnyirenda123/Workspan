package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.PasswordResetCompleteRequest;
import com.workspan.workspanbackend.dto.PasswordResetRequest;
import com.workspan.workspanbackend.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService resets;
    public PasswordResetController(PasswordResetService resets) { this.resets = resets; }

    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody PasswordResetRequest request) { resets.request(request.email()); }

    @PostMapping("/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@Valid @RequestBody PasswordResetCompleteRequest request) { resets.complete(request); }
}
