package com.openshift.portal.controller;

import com.openshift.portal.dto.TeamDto;
import com.openshift.portal.dto.TeamRequests;
import com.openshift.portal.service.TeamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Teams and their owner label aliases. Reading is open to viewers; changes are admin only (the default rule). */
@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Team management and namespace alias mapping")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<List<TeamDto>> listTeams() {
        return ResponseEntity.ok(teamService.listTeams());
    }

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody TeamRequests.CreateTeam request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(request));
    }

    /** Maps another owner label value to the team; namespaces carrying it move to the team immediately. */
    @PostMapping("/{id}/aliases")
    public ResponseEntity<TeamDto> addAlias(@PathVariable UUID id, @Valid @RequestBody TeamRequests.AddAlias request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.addAlias(id, request.getAlias()));
    }

    @DeleteMapping("/{id}/aliases/{alias}")
    public ResponseEntity<Void> removeAlias(@PathVariable UUID id, @PathVariable String alias) {
        teamService.removeAlias(id, alias);
        return ResponseEntity.noContent().build();
    }
}
