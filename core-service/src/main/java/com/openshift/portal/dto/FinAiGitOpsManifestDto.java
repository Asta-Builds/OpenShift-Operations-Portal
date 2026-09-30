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
@Schema(description = "Generated GitOps repository structure and ArgoCD manifest")
public class FinAiGitOpsManifestDto {

    @Schema(description = "Recommended repository path", example = "clusters/ocp-ai-training-prod/namespaces/spark-batch-analytics/")
    private String repoPath;

    @Schema(description = "ResourceQuota YAML manifest")
    private String resourceQuotaYaml;

    @Schema(description = "kustomization.yaml manifest")
    private String kustomizationYaml;

    @Schema(description = "ArgoCD Application CRD manifest")
    private String argocdApplicationYaml;

    @Schema(description = "Branch name recommended for Pull Request", example = "finops/rightsize-spark-batch-analytics")
    private String branchName;

    @Schema(description = "PR commit message title", example = "feat(finops): rightsizing quota for spark-batch-analytics (-$1,691.82/mo)")
    private String commitMessage;
}
