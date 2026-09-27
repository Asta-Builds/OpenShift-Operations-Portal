package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserDto {
    private String username;
    private String name;
    /** Portal roles including the ones implied by the hierarchy, e.g. an ADMIN also gets OPERATOR and VIEWER. */
    private List<String> roles;
}
