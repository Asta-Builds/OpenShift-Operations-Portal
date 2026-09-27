package com.openshift.portal.service;

import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.entity.TeamAlias;
import com.openshift.portal.dto.TeamDto;
import com.openshift.portal.dto.TeamRequests;
import com.openshift.portal.exception.ResourceNotFoundException;
import com.openshift.portal.repository.NamespaceRepository;
import com.openshift.portal.repository.TeamAliasRepository;
import com.openshift.portal.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Teams and the owner label values that map to them. Every change re-resolves namespace ownership right away, so
 * attribution reflects it without waiting for the next collection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamAliasRepository aliasRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceIngestionService namespaceIngestionService;

    @Transactional(readOnly = true)
    public List<TeamDto> listTeams() {
        return teamRepository.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Transactional
    public TeamDto createTeam(TeamRequests.CreateTeam request) {
        String key = OwnerResolver.normalize(request.getName());
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The team name needs at least one letter or digit");
        }
        ensureKeyIsFree(key);
        Team team = teamRepository.save(Team.builder()
                .name(request.getName().trim())
                .costCenter(blankToNull(request.getCostCenter()))
                .contactEmail(blankToNull(request.getContactEmail()))
                .build());
        remap();
        return toDto(team);
    }

    @Transactional
    public TeamDto addAlias(UUID teamId, String alias) {
        Team team = findTeam(teamId);
        String key = OwnerResolver.normalize(alias);
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The alias needs at least one letter or digit");
        }
        ensureKeyIsFree(key);
        aliasRepository.save(TeamAlias.builder().team(team).alias(key).build());
        remap();
        return toDto(team);
    }

    @Transactional
    public void removeAlias(UUID teamId, String alias) {
        Team team = findTeam(teamId);
        TeamAlias existing = aliasRepository.findByAlias(OwnerResolver.normalize(alias))
                .filter(a -> a.getTeam().getId().equals(team.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Team " + team.getName() + " has no alias " + alias));
        aliasRepository.delete(existing);
        aliasRepository.flush();
        remap();
    }

    /** A value may name only one team, whether as a team name or an alias. */
    private void ensureKeyIsFree(String key) {
        boolean takenByTeam = teamRepository.findAll().stream()
                .anyMatch(team -> OwnerResolver.normalize(team.getName()).equals(key));
        if (takenByTeam || aliasRepository.findByAlias(key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The owner value " + key + " already maps to a team");
        }
    }

    private void remap() {
        int changed = namespaceIngestionService.remapOwners();
        if (changed > 0) {
            log.info("Reassigned {} namespace(s) after a team change", changed);
        }
    }

    private Team findTeam(UUID id) {
        return teamRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + id));
    }

    private TeamDto toDto(Team team) {
        return TeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .costCenter(team.getCostCenter())
                .contactEmail(team.getContactEmail())
                .aliases(aliasRepository.findByTeamIdOrderByAlias(team.getId()).stream().map(TeamAlias::getAlias).toList())
                .namespaceCount((int) namespaceRepository.countByOwnerTeamIdAndDeletedAtIsNull(team.getId()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
