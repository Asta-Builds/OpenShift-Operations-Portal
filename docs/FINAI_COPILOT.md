# OpenShift Operations Portal — FinAI Copilot Engine

## 1. Présentation Exécutive & Problématique

Dans les environnements multi-clusters OpenShift d'entreprise gérés par **Red Hat Advanced Cluster Management (ACM)**, les administrateurs et ingénieurs de plateformes font face à trois défis majeurs :

1. **Surdimensionnement et Gaspillage Silencieux** : Des centaines de namespaces consomment des quotas excessifs par simple mesure de précaution. Les équipes manquent d'outils intelligents pour synthétiser en langage clair les surallocations et les gains financiers potentiels.
2. **Complexité des Actions Correctives OpenShift** : Identifier une dérive est une chose, mais générer sans erreur les commandes CLI `oc patch`, les manifestes de `ResourceQuota`, de `LimitRange` ou de désactivation de pods zombies exige une expertise fine de la syntaxe Kubernetes.
3. **Contraintes Strictes de Sécurité (Air-Gapped & Zero Data Leaks)** : Les clusters de production des banques, assurances et organismes régulés interdisent formellement l'envoi de métriques, de topologies ou de noms de pods vers des APIs LLM publiques externes (OpenAI, Anthropic, Google Cloud).

**FinAI Copilot** a été conçu spécifiquement pour répondre à ces exigences. Il combine un **moteur heuristique d'inférence déterministe autonome** (fonctionnant à 100% en environnement déconnecté / air-gapped sans dépendance externe) avec une passerelle optionnelle vers un modèle de langage d'entreprise privé (LLM local ou hébergé on-premise).

---

## 2. Architecture & Flux Opérationnel

```mermaid
flowchart TD
    subgraph Frontend [Angular 18 Enterprise UI]
        TopNav[Global Topbar AI Button] --> CopilotDrawer[FinAiCopilotComponent Slide-over]
        FinOpsTable[FinOps Recommendations Table] -->|Context: Cluster & Namespace| CopilotDrawer
        QuickChips[Quick Suggestion Chips] --> CopilotDrawer
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
        
        IntentRouter -.->|Optional LLM Gateway| ExtLLM[On-Premise Private LLM / Ollama]
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
| **`RIGHTSIZING_ANALYSIS`** | `surprovisionné`, `gaspillage`, `optimiser`, `cpu`, `ram`, `économies` | Calcul global du gaspillage financier (\$/mois), dénombrement des cœurs CPU et Go de RAM dormants, et ciblage des namespaces les plus prioritaires. |
| **`CAPACITY_FORECAST`** | `saturation`, `croissance`, `prévision`, `runway`, `épuisement` | Modélisation des tendances de consommation, identification du cluster le plus proche de la saturation et projection à 30/90 jours. |
| **`EMERGENCY_SAVINGS`** | `immédiat`, `urgence`, `quick win`, `réduire les coûts` | Plan d'action d'urgence ciblant les 20% de namespaces générant 80% du gaspillage financier, réduction immédiate de 20-30% des buffers dev/staging. |
| **`GENERAL_FINOPS_INQUIRY`** | Requêtes ouvertes | Diagnostic exécutif consolidé de la flotte avec plan par étapes et questions de suivi. |

---

## 4. Génération de Commandes CLI `oc` Prêtes à l'Emploi

FinAI Copilot génère des commandes OpenShift conformes aux bonnes pratiques de production :

### Exemple 1 : Application d'un ResourceQuota de Rightsizing
```bash
# FinAI Patch Quota pour namespace critique (Safety: SAFE)
oc apply -f - <<EOF
apiVersion: v1
kind: ResourceQuota
metadata:
  name: finops-optimized-quota
  namespace: payment-gateway
spec:
  hard:
    requests.cpu: "2200m"
    requests.memory: "4800Mi"
    limits.cpu: "4000m"
    limits.memory: "8000Mi"
EOF
```

### Exemple 2 : Audit en temps réel des surallocations
```bash
# Vérification des pods dont l'usage réel est inférieur à 25% de la requête
oc adm top pods -A --sort-by=cpu | head -n 25
```

### Exemple 3 : Résolution d'un dépassement de licence (Cap Exceeded)
```bash
# Identifier les nœuds worker hors souscription pour bascule en maintenance
oc get nodes -l node-role.kubernetes.io/worker -o wide
# Marquer un nœud surnuméraire comme non planifiable (Safety: MEDIUM)
oc cordon <worker-node-name>
```

---

## 5. Spécification des APIs REST

### `POST /api/v1/finops/ai/query`
Traite une requête en langage naturel avec contexte optionnel.

#### Requête :
```json
{
  "prompt": "Quelles sont les opportunités d'économies immédiates sur notre flotte OpenShift ?",
  "clusterId": "cluster-prod-01",
  "namespace": "finance-api",
  "environment": "PRODUCTION"
}
```

#### Réponse :
```json
{
  "query": "Quelles sont les opportunités d'économies immédiates sur notre flotte OpenShift ?",
  "intent": "RIGHTSIZING_ANALYSIS",
  "summary": "FinAI a identifié $1,840.50/mois d'économies potentielles à l'échelle de la flotte OpenShift...",
  "detailedAnalysis": "L'analyse des métriques télémétriques révèle 86 cœurs CPU alloués mais non consommés...",
  "confidenceScore": 0.94,
  "monthlySavingsUsd": 1840.50,
  "overprovisionedCores": 86.0,
  "idleMemoryGb": 240.0,
  "executionSteps": [
    "Audit des pods sous-utilisés via oc adm top",
    "Appliquer le patch ResourceQuota préconisé",
    "Surveiller les métriques Prometheus pendant 48h"
  ],
  "cliSnippets": [
    {
      "title": "Application du patch ResourceQuota",
      "description": "Applique le quota optimisé sans interruption de service",
      "command": "oc apply -f - <<EOF\n...\nEOF",
      "targetResource": "ResourceQuota",
      "safetyLevel": "SAFE"
    }
  ],
  "metrics": [
    { "label": "Économies Annuelles", "value": "$22,086", "unit": "/an", "changeType": "POSITIVE" },
    { "label": "Efficacité Actuelle", "value": "64.2", "unit": "%", "changeType": "NEUTRAL" }
  ],
  "followUpQuestions": [
    "Comment appliquer automatiquement ce patch avec GitOps (ArgoCD) ?",
    "Quels sont les namespaces les plus gaspilleurs ?"
  ]
}
```

### `GET /api/v1/finops/ai/quick-prompts`
Retourne les questions types prêtes à l'emploi réparties par catégorie (`OPTIMIZATION`, `LICENSING`, `REMEDIATION`, `FORECASTING`).

---

## 6. Expérience Utilisateur & Intégrations Frontend

1. **Slide-over Drawer Global** : Accessible depuis n'importe quelle page du portail via le bouton **"FinAI Copilot"** du bandeau supérieur ou la palette de commande (`Ctrl + K`).
2. **Intégration Contextuelle FinOps** : Dans le tableau des recommandations de rightsizing, chaque ligne dispose d'un bouton **"FinAI"** qui ouvre le Copilot pré-rempli avec le nom du cluster et du namespace.
3. **Copie en 1-Clic avec Feedback Visuel** : Chaque extrait CLI dispose d'un bouton `Copier` avec badge de confirmation dynamique (`Copié !`) et indication du niveau de risque (`SAFE`, `MEDIUM`, `DESTRUCTIVE`).
4. **Support Parfait Dark / Light Mode** : Contraste WCAG AA respecté sur tous les éléments, palette de code terminal dark-mode GitHub (`#0d1117`) et puces métriques colorées.

---

## 7. Sécurité & Conformité Air-Gapped

- **Zéro fuite de données** : Aucun appel réseau sortant vers l'extérieur. Toutes les analyses tournent localement au sein du microservice Spring Boot.
- **Autorisations Basées sur les Rôles (RBAC)** : Les recommandations respectent les droits de l'utilisateur (`OPERATOR`, `ADMIN`).
- **Isolation Sandbox** : La génération de scripts n'exécute aucune action destructrice directe sans intervention explicite de l'opérateur humain.
