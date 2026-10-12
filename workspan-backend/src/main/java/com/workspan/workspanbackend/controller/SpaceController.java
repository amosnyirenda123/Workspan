package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.SpaceRequest;
import com.workspan.workspanbackend.dto.SpaceResponse;
import com.workspan.workspanbackend.service.SpaceService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaces")
@Tag(name = "Spaces", description = "Espaces de travail de l’organisation connectée")
@SecurityRequirement(name = "sessionCookie")
public class SpaceController {
    private final SpaceService spaces;
    private final WorkspaceAccess access;
    public SpaceController(SpaceService spaces, WorkspaceAccess access) { this.spaces = spaces; this.access = access; }

    @GetMapping @Operation(summary = "Lister les espaces")
    public List<SpaceResponse> findAll(HttpSession session) { return spaces.findAll(access.organizationId(session)); }

    @GetMapping("/{id}") @Operation(summary = "Lire un espace")
    public SpaceResponse findById(@PathVariable UUID id, HttpSession session) { return spaces.findById(id, access.organizationId(session)); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Créer un espace")
    public SpaceResponse create(@Valid @RequestBody SpaceRequest request, HttpSession session) { return spaces.create(access.organizationId(session), request); }

    @PutMapping("/{id}") @Operation(summary = "Modifier un espace")
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody SpaceRequest request, HttpSession session) { return spaces.update(id, access.organizationId(session), request); }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Supprimer un espace")
    public void delete(@PathVariable UUID id, HttpSession session) { spaces.delete(id, access.organizationId(session)); }
}
