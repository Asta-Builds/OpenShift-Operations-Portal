package com.openshift.portal.service;

import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.entity.TeamAlias;
import com.openshift.portal.repository.TeamAliasRepository;
import com.openshift.portal.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps owner label values to teams. A value matches a team when, after normalization, it equals the team's name
 * or one of its aliases. Normalization lower-cases and turns every run of other characters into one hyphen, so
 * the label value {@code payments-platform} matches the team "Payments Platform" (label values cannot hold spaces).
 */
@Service
@RequiredArgsConstructor
public class OwnerResolver {

    private final TeamRepository teamRepository;
    private final TeamAliasRepository aliasRepository;

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** Current teams and aliases, loaded once and used for a whole ingestion or remapping pass. */
    @Transactional(readOnly = true)
    public Directory directory() {
        Map<String, Team> byKey = new HashMap<>();
        for (TeamAlias alias : aliasRepository.findAllWithTeam()) {
            byKey.put(normalize(alias.getAlias()), alias.getTeam());
        }
        // A team's own name wins over an alias spelled the same way
        for (Team team : teamRepository.findAll()) {
            byKey.put(normalize(team.getName()), team);
        }
        return new Directory(byKey);
    }

    public record Directory(Map<String, Team> teamsByKey) {

        /** The team an owner label value refers to; empty for a blank or unknown value. */
        public Optional<Team> resolve(String ownerLabelValue) {
            String key = normalize(ownerLabelValue);
            return key.isEmpty() ? Optional.empty() : Optional.ofNullable(teamsByKey.get(key));
        }
    }
}
