package com.openshift.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Contextual quick prompt chip displayed to the operator")
public class FinAiQuickPromptDto {

    private String id;
    private String category;
    private String icon;
    private String title;
    private String prompt;
    private String badge;
}
