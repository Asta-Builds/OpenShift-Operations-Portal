# OpenShift Operations Portal — FinAI Copilot Engine

## 1. Présentation Exécutive & Problématique

Dans les environnements multi-clusters OpenShift d'entreprise gérés par **Red Hat Advanced Cluster Management (ACM)**, les administrateurs et ingénieurs de plateformes font face à des défis majeurs :

1. **Surdimensionnement et Gaspillage Silencieux** : Des centaines de namespaces consomment des quotas excessifs par simple mesure de précaution. Les équipes manquent d'outils intelligents pour synthétiser en langage clair les surallocations et les gains financiers potentiels.
2. **Complexité des Actions Correctives OpenShift** : Identifier une dérive est une chose, mais générer sans erreur les commandes CLI `oc patch`, les manifestes de `ResourceQuota`, de `LimitRange` ou de désactivation de pods zombies exige une expertise fine de la syntaxe Kubernetes.
3. **Contraintes Strictes de Sécurité (Air-Gapped & Zero Data Leaks)** : Les clusters de production des banques, assurances et organismes régulés interdisent formellement l'envoi de métriques, de topologies ou de noms de pods vers des APIs LLM publiques externes (OpenAI, Anthropic, Google Cloud).
4. **Gouvernance GitOps & Diffusion d'Équipe** : Les préconisations d'optimisation doivent pouvoir s'intégrer immédiatement dans la chaîne CI/CD (ArgoCD / Kustomize) et être partagées sur les canaux de communication d'équipe (Slack / Microsoft Teams) sans ressaisie manuelle.

**FinAI Copilot** a été conçu spécifiquement pour répondre à ces exigences. Il combine un **moteur heuristique d'inférence déterministe autonome** (fonctionnant à 100% en environnement déconnecté / air-gapped sans dépendance externe) avec une passerelle optionnelle vers un modèle de langage d'entreprise privé (LLM local ou hébergé on-premise).

---

## 2. Architecture & Flux Opérationnel

```mermaid
flowchart TD
    subgraph Frontend [Angular 18 Enterprise UI - Air-Gapped]
        TopNav[Global Topbar FinAI Button / Ctrl+K] --> CopilotDrawer[FinAiCopilotComponent Slide-over]
        FinOpsTable[FinOps Recommendations Table] -->|Context: Cluster & Namespace| CopilotDrawer
        QuickChips[Quick Suggestion Chips] --> CopilotDrawer
        
        CopilotDrawer --> FilterBar[View Filter Pills: Tout / CLI / Plan / Métriques]
        CopilotDrawer --> DryRunBtn[🧪 Valider en Dry-Run]
        CopilotDrawer --> GitOpsBtn[🐙 GitOps ArgoCD]
        CopilotDrawer --> NotifyBtn[📢 Diffuser Slack / Teams]
        CopilotDrawer --> ExportPdfBtn[🔴 Export PDF Exécutif]
        CopilotDrawer --> ExportMdBtn[📄 Audit Markdown .MD]
    end

    subgraph CoreBackend [Spring Boot Core Service]
        CopilotDrawer -->|POST /api/v1/finops/ai/query| Controller[FinOpsController]
        Controller --> FinAiService[FinAiCopilotService]
        
        FinAiService --> FinOpsEngine[FinOpsService (Metrics & Recommendations)]
        FinAiService --> LicEngine[LicenseAuditService (Core Caps & Watermarks)]
        FinAiService --> MetricEngine[MetricsService (CPU, RAM, Fleet Health)]
        
        FinAiService --> IntentRouter{Intent Classifier & Heuristic Engine}
        
        IntentRouter -->|Sizing & Waste| SizingProc[Rightsizing & Overprovisioning Diagnosis]
        IntentRouter -->|Remediation CLI| CliGen[oc CLI Script & YAML Generator]
        IntentRouter -->|License & Cap| LicProc[License Breach & Cost Avoidance Audit]
        IntentRouter -->|Runway & Capacity| CapProc[Capacity Runway & Headroom Analyzer]
        IntentRouter -->|Strategic Roadmap| RoadProc[Multi-Horizon Action Plan Synthesis]
        
        DryRunBtn -->|POST /api/v1/finops/ai/dry-run| DryRunEndpoint[Dry-Run Simulation Engine]
        GitOpsBtn -->|POST /api/v1/finops/ai/gitops-manifest| GitOpsEndpoint[ArgoCD / Kustomize Generator]
        NotifyBtn -->|POST /api/v1/finops/ai/notify| NotifyEndpoint[Slack / Teams Webhook Dispatcher]
        ExportPdfBtn -->|POST /api/v1/finops/ai/export-pdf| PdfService[PdfReportGeneratorService - OpenPDF A4]
    end

    IntentRouter --> OutputSynth[FinAiResponseDto: Summary, Metrics, CLI, Steps, Follow-ups]
    OutputSynth --> CopilotDrawer
```

---

## 3. Capacités du Moteur Heuristique & Détection d'Intentions

Le moteur d'analyse classifie dynamiquement les requêtes de l'utilisateur selon les grandes catégories d'intentions suivantes :

| Intention Détectée | Mots-Clés Analysés | Analyse Effectuée & Données Produits |
| :--- | :--- | :--- |
| **`LICENSE_AUDIT`** | `licence`, `license`, `cap`, `dépassement`, `breach`, `conformité` | Détection des dépassements de licence Red Hat OpenShift (ex: 104 cœurs utilisés pour 100 cœurs souscrits), calcul du surcoût pénalitaire, et génération des commandes `oc cordon` / `oc adm drain` pour désactiver les nœuds non autorisés. |
| **`CLI_REMEDIATION`** | `oc patch`, `recommandation`, `corriger`, `script`, `quota`, `manifeste` | Génération de snippets CLI `oc` avec drapeaux de sécurité (`SAFE`, `MEDIUM`, `DESTRUCTIVE`), patchs de quotas JSON inline et commandes de validation. |
| **`RIGHTSIZING_ANALYSIS`** | `surprovisionné`, `gaspillage`, `optimiser`, `cpu`, `ram`, `économies` | Calcul global du gaspillage financier ($/mois), dénombrement des cœurs CPU et Go de RAM dormants, et ciblage des namespaces les plus prioritaires. |
| **`CAPACITY_FORECAST`** | `saturation`, `croissance`, `prévision`, `runway`, `épuisement` | Modélisation des tendances de consommation, identification du cluster le plus proche de la saturation et projection à 30/90 jours. |
| **`EMERGENCY_SAVINGS`** | `immédiat`, `urgence`, `quick win`, `réduire les coûts` | Plan d'action d'urgence ciblant les 20% de namespaces générant 80% du gaspillage financier, réduction immédiate de 20-30% des buffers dev/staging. |
| **`GENERAL_FINOPS_INQUIRY`** | Requêtes ouvertes | Diagnostic exécutif consolidé de la flotte avec plan par étapes et questions de suivi. |

---

## 4. Fonctionnalités Phares & Expérience Utilisateur

### 4.1. Défilement Automatique Intelligent (Auto-Scroll) & UX Polie
- **Double détente de scroll** : Défilement fluide automatique (`smooth scroll`) activé immédiatement à la soumission d'une question, à l'affichage du voyant de réflexion du Copilot, et dès l'arrivée du résultat complet.
- **Pill flottant de retour en bas** : Si l'utilisateur remonte manuellement pour relire un échange antérieur (>150px au-dessus du fond), un bouton rebondissant `↓ Défiler vers le bas` apparaît avec micro-animation d'attention.
- **Moteur de rendu Markdown enrichi** : Parseur sécurisé avec mise en valeur des titres (`###`), balises de code inline, listes à puces et conteneurs d'alertes visuels (`> [!WARNING]` en ambre, `> [!NOTE]` en bleu/cyan).

### 4.2. Barre de Filtres de Vue ("Pills Filter")
Située sous l'en-tête du chat dès qu'une conversation est active, elle permet de basculer la vue d'un clic :
- **Tout afficher** : Vue complète combinant diagnostic textuel, cartes métriques, snippets CLI et plan d'exécution.
- **Commandes oc CLI** : Isole uniquement les blocs de code exécutables pour les administrateurs voulant copier rapidement les scripts.
- **Plan d'Action** : Masque le détail technique pour afficher uniquement les étapes méthodologiques numérotées.
- **Métriques Clés** : Focus exclusif sur les KPIs financiers (économies mensuelles, cœurs récupérables, mémoire dormante).

### 4.3. Validation One-Click Dry-Run (`--dry-run=server`)
Sur chaque extrait de commande CLI `oc` généré par FinAI Copilot, un bouton **"🧪 Valider en Dry-Run"** déclenche une vérification immédiate auprès du backend :
- Test de syntaxe et de conformité Kubernetes.
- Simulation d'impact sur les pods actifs du namespace (ex: 14 pods évalués, 0 violation, 0% de perturbation).
- Affichage d'un badge de certification vert garantissant l'absence de coupure de service.

### 4.4. Générateur GitOps Manifest & ArgoCD Application
Pour appliquer les préconisations selon les standards d'Infrastructure-as-Code :
- Bouton **"🐙 GitOps (ArgoCD)"** présent sur chaque recommandation.
- Fenêtre modale tabulée affichant le manifeste `kustomization.yaml`, la ressource `resource-quota.yaml` dimensionnée, et la CRD ArgoCD `Application` configurée avec `Prune=true` et `SelfHeal=true`.
- **Téléchargement en 1-clic** du bundle complet (`finops-gitops-bundle.yaml`) avec proposition de nom de branche Git (`feature/finops-rightsizing-...`) et message de commit conventionnel.

### 4.5. Diffusion Webhook Slack & Microsoft Teams
- Bouton **"📢 Diffuser sur Slack/Teams"** permettant de notifier les équipes de développement.
- Formulaire de sélection de cible (Slack Incoming Webhook ou Microsoft Teams Connector).
- Payload structuré :
  - **Slack Block Kit** : En-tête coloré, blocs de champs avec KPIs financiers, et plan d'action formaté.
  - **Teams MessageCard / AdaptiveCard** : Titre d'alerte, sections de faits et lien direct vers le portail.
- Confirmation par toast visuel avec horodatage d'envoi.

### 4.6. Double Export : Rapport Exécutif PDF vs. Journal d'Audit Markdown
L'en-tête du chat propose deux boutons distincts pour répondre aux deux besoins de l'entreprise :

| Format | Bouton UI | Public Cible | Contenu & Caractéristiques |
| :--- | :--- | :--- | :--- |
| **PDF** | <span style="background:#EF4444; color:white; padding:2px 8px; border-radius:6px; font-weight:bold; font-size:11px;">Export PDF</span> | **Management & Comités C-Level** | Document A4 généré côté serveur via **OpenPDF**, respectant la charte **Red Hat OpenShift** (en-tête rouge `#CC0000`, titre du diagnostic, score de confiance IA, tableau des métriques financières, blocs CLI et plan d'exécution officiel). Prêt pour archivage et signature managériale. |
| **Markdown** | <span style="background:#374151; color:#D1D5DB; padding:2px 8px; border-radius:6px; font-size:11px;">Audit .MD</span> | **SRE, DevOps & Développeurs** | Fichier `.md` brut chronologique idéal pour copier/coller dans des **tickets Jira**, des documentations GitHub/GitLab, ou pour alimenter des pipelines de scripting. |

---

## 5. Spécification Détaillée des APIs REST

### 5.1. Traiter une Requête FinAI
```http
POST /api/v1/finops/ai/query
Content-Type: application/json
```
#### Payload de Requête :
```json
{
  "prompt": "Comment optimiser les quotas de notre namespace payment-gateway ?",
  "clusterId": "cluster-prod-01",
  "namespace": "payment-gateway",
  "environment": "PRODUCTION"
}
```
#### Payload de Réponse (`200 OK`) :
```json
{
  "query": "Comment optimiser les quotas de notre namespace payment-gateway ?",
  "intent": "RIGHTSIZING_ANALYSIS",
  "headline": "Optimisation Quota & Réduction de Gaspillage : payment-gateway",
  "summary": "FinAI a identifié $1,840.50/mois d'économies potentielles...",
  "analysisMarkdown": "L'analyse télémétrique révèle **86 cœurs CPU** et **240 Go de RAM** alloués mais non consommés...",
  "confidenceScore": 94,
  "estimatedSavings": "1 840,50 $/mois",
  "metrics": [
    { "label": "Économies Annuelles", "value": "22 086 $", "change": "+18%", "sentiment": "POSITIVE" },
    { "label": "CPU Gaspillé", "value": "86 Cores", "change": "-42%", "sentiment": "POSITIVE" }
  ],
  "cliCommands": [
    {
      "title": "Application du ResourceQuota optimisé",
      "command": "oc apply -f - <<EOF\napiVersion: v1\nkind: ResourceQuota\nmetadata:\n  name: finops-quota\n  namespace: payment-gateway\nspec:\n  hard:\n    requests.cpu: \"2200m\"\n    requests.memory: \"4800Mi\"\nEOF",
      "description": "Ajuste les requêtes sans interruption des 14 pods actifs",
      "safetyLevel": "SAFE"
    }
  ],
  "executionPlan": [
    "Vérifier la consommation réelle via oc adm top pods",
    "Appliquer le manifeste ResourceQuota optimisé",
    "Surveiller les métriques Prometheus pendant 48 heures"
  ],
  "followUpQuestions": [
    "Comment automatiser cette configuration via GitOps ?",
    "Quel est le risque de saturation mémoire ?"
  ]
}
```

---

### 5.2. Simulation Server-Side Dry-Run
```http
POST /api/v1/finops/ai/dry-run
Content-Type: application/json
```
#### Payload de Requête :
```json
{
  "command": "oc apply -f - <<EOF ... EOF",
  "clusterId": "cluster-prod-01",
  "namespace": "payment-gateway"
}
```
#### Payload de Réponse (`200 OK`) :
```json
{
  "success": true,
  "validationOutput": "Dry-run execution passed: ResourceQuota 'finops-quota' validated successfully against Kubernetes API Server.",
  "clusterId": "cluster-prod-01",
  "namespace": "payment-gateway",
  "podsEvaluated": 14,
  "violationsCount": 0,
  "estimatedDisruptionPercent": 0.0,
  "simulatedAt": "2026-09-30T12:00:00Z"
}
```

---

### 5.3. Génération de Manifeste GitOps (ArgoCD / Kustomize)
```http
POST /api/v1/finops/ai/gitops-manifest
Content-Type: application/json
```
#### Payload de Requête :
```json
{
  "clusterId": "cluster-prod-01",
  "namespace": "payment-gateway",
  "suggestedCpuRequest": "2200m",
  "suggestedMemoryRequest": "4800Mi",
  "targetRepoUrl": "https://github.com/my-org/openshift-fleet-gitops.git"
}
```
#### Payload de Réponse (`200 OK`) :
```json
{
  "kustomizationYaml": "apiVersion: kustomize.config.k8s.io/v1beta1\nkind: Kustomization\nresources:\n  - resource-quota.yaml\n",
  "resourceQuotaYaml": "apiVersion: v1\nkind: ResourceQuota\nmetadata:\n  name: finops-optimized-quota\n  namespace: payment-gateway\nspec:\n  hard:\n    requests.cpu: \"2200m\"\n    requests.memory: \"4800Mi\"\n",
  "argoAppYaml": "apiVersion: argoproj.io/v1alpha1\nkind: Application\nmetadata:\n  name: finops-quota-payment-gateway\n  namespace: openshift-gitops\nspec:\n  syncPolicy:\n    automated:\n      prune: true\n      selfHeal: true\n",
  "branchName": "feature/finops-rightsizing-payment-gateway",
  "commitMessage": "feat(finops): rightsizing ResourceQuota for namespace payment-gateway"
}
```

---

### 5.4. Diffusion Webhook (Slack / Microsoft Teams)
```http
POST /api/v1/finops/ai/notify
Content-Type: application/json
```
#### Payload de Requête :
```json
{
  "targetPlatform": "SLACK",
  "webhookUrl": "https://hooks.slack.com/services/T00/B00/XXXX",
  "headline": "Alerte Économie FinOps — payment-gateway",
  "summary": "Surallocation détectée : 86 cœurs dormants. Économie estimée : 1 840 $/mois.",
  "estimatedSavings": "1 840,50 $/mois",
  "clusterId": "cluster-prod-01",
  "namespace": "payment-gateway"
}
```
#### Payload de Réponse (`200 OK`) :
```json
{
  "success": true,
  "message": "Notification envoyée avec succès sur SLACK",
  "dispatchedAt": "2026-09-30T12:00:00Z"
}
```

---

### 5.5. Export Rapport PDF Exécutif
```http
POST /api/v1/finops/ai/export-pdf
Content-Type: application/json
Accept: application/pdf
```
Envoie le DTO `FinAiResponseDto` et retourne directement le flux binaire PDF formaté en A4 avec les polices Helvetica, tableau des métriques financières et en-tête Red Hat officiel.

---

## 6. Sécurité & Conformité Air-Gapped

1. **Zéro fuite de données** : FinAI Copilot ne contacte aucun service LLM public externe (OpenAI, Claude, Gemini). Les métriques de consommation, les identifiants de clusters et les noms d'applications restent confinés dans le réseau de l'entreprise.
2. **Génération Déterministe & Auditée** : Toutes les recommandations reposent sur des règles mathématiques d'allocation de ressources basées sur les enregistrements Prometheus du portail.
3. **Contrôle d'Accès RBAC** : Les endpoints de validation, de notification et de génération GitOps respectent les permissions associées aux rôles Keycloak de l'utilisateur (`OPERATOR`, `ADMIN`).
4. **Non-Intrusivité** : Le Copilot ne modifie jamais directement l'état du cluster de production. Il propose des commandes `oc` validées ou des manifests GitOps soumis à la revue de code humaine.
