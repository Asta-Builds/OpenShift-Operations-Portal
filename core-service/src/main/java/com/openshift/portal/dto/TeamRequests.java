package com.openshift.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request bodies of the team endpoints. */
public final class TeamRequests {

    private TeamRequests() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTeam {
        @NotBlank
        @Size(max = 255)
        private String name;

        @Size(max = 100)
        private String costCenter;

        @Email
        @Size(max = 255)
        private String contactEmail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddAlias {
        /** An owner label value; Kubernetes label values are at most 63 characters. */
        @NotBlank
        @Size(max = 63)
        private String alias;
    }
}
