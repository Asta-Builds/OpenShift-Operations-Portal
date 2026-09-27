package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDto {
    private UUID id;
    private String name;
    private String costCenter;
    private String contactEmail;
    /** Owner label values besides the team's own name that map to it. */
    private List<String> aliases;
    /** Namespaces currently owned by the team, deleted ones excluded. */
    private int namespaceCount;
}
