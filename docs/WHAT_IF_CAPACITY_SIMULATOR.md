# OpenShift Operations Portal — Interactive "What-If" Capacity & FinOps Simulator

## 1. Executive Summary & Purpose

While historical monitoring tools show *what happened* in the past, platform architects and cloud financial officers (FinOps) face critical predictive questions before authorizing infrastructure modifications:

- *"If our application teams apply 50% of the recommended quota rightsizing, how many physical CPU cores and RAM will we recover, and what will our monthly cloud bill look like?"*
- *"We need to onboard a heavy GenAI inference platform (+64 vCPU, +256 GB RAM). Will our existing AI training cluster absorb it, or will it trigger pod scheduling bottlenecks?"*
- *"Can we decommission our aging dev/sandbox cluster, drain its active workloads into Staging, and what are the resulting Red Hat OpenShift licensing and hardware savings?"*
- *"If our enterprise traffic grows by 25% during the holiday peak, which clusters will cross critical saturation limits (85%+), and how many worker nodes must we scale out in advance?"*

The **Interactive "What-If" Capacity & FinOps Simulator** provides a reactive modeling sandbox to evaluate these multi-dimensional scenarios in real time with zero risk to production.

---

## 2. Architectural Blueprint

```mermaid
flowchart LR
    subgraph User Inputs & Scenario Deck
        Slider[Rightsizing Adoption: 0 - 100%]
        NewWorkloads[Hypothetical Workload Onboarding]
        Decom[Cluster Drain & Consolidation]
        StressTest[Global Growth Variance: -20% to +50%]
        Presets[1-Click Enterprise Presets]
    end

    subgraph What-If Simulation Engine Backend
        WhatIfSvc[WhatIfSimulatorService]
        Snapshots[Latest Fleet Snapshots & Node Counts]
        Pricing[Unit Pricing & Red Hat Subscription Costs]
        HeadroomRules[Headroom & Saturation Threshold Matrix]
    end

    subgraph Output Predictions
        FinDelta[Monthly & Annual Savings Delta]
        ResDelta[Freed CPU Cores, RAM GB, Storage]
        FleetShift[Before vs After Utilization Gauges]
        ClusterImpact[Per-Cluster Headroom Status & Node Sizing]
        Roadmap[Automated Strategic Recommendations]
    end

    User Inputs & Scenario Deck -->|POST /finops/simulator/simulate| WhatIfSvc
    Snapshots --> WhatIfSvc
    Pricing --> WhatIfSvc
    HeadroomRules --> WhatIfSvc
    WhatIfSvc --> OutputPredictions
```

---

## 3. The 4 Predictive Simulation Vectors

### Vector 1: Rightsizing Adoption Curve
- **Parameter**: `rightsizingAdoptionPercent` ($0\%$ to $100\%$) and `targetEfficiencyRatings` (e.g. `[SEVERE_WASTE, OVER_PROVISIONED]`).
- **Mathematical Modeling**:
  - For each eligible namespace recommendation, the potential core and memory delta is scaled by the adoption fraction:
  
  $$\text{Freed CPU Cores} = (\text{Avg CPU Request} - \text{Recommended CPU}) \times \left(\frac{\text{Adoption \%}}{100}\right)$$
  
  $$\text{Freed RAM GB} = (\text{Avg Memory Request} - \text{Recommended RAM}) \times \left(\frac{\text{Adoption \%}}{100}\right)$$
  
  $$\text{Rightsizing Savings} = \text{Monthly Potential Savings} \times \left(\frac{\text{Adoption \%}}{100}\right)$$

  - The cluster's simulated allocated capacity is immediately decreased, expanding available headroom.

### Vector 2: Workload Onboarding & Sizing (Capacity Check)
- **Parameter**: `additionalWorkloads` (list of hypothetical applications with CPU cores, RAM GB, and PVC storage).
- **Headroom Stress Test**:
  - Injects new allocation requirements into the selected target cluster.
  - Verifies whether the post-onboarding allocation crosses safe operational boundaries.
  - Automatically calculates the additional monthly run-rate cost using unit pricing.

### Vector 3: Cluster Decommissioning & Fleet Consolidation
- **Parameter**: `clusterDecommissions` (`sourceCluster` $\rightarrow$ `targetCluster`).
- **Consolidation Mechanics**:
  - The source cluster is marked as `decommissioned = true` (allocations drop to 0).
  - All active allocations from the source cluster are migrated into the target cluster.
  - Quantifies two streams of recurring savings:
    1. **Infrastructure / VM Node Savings**: $\sim \$180/\text{month}$ per retired worker node.
    2. **Red Hat OpenShift Licensing Core Savings**: $\sim \$65/\text{month}$ per billable worker core (licensed in 2-core pairs).

### Vector 4: Global Growth Stress-Testing
- **Parameter**: `fleetGrowthPercent` ($-20\%$ to $+50\%$).
- **Fleet-Wide Impact**: Multiplies existing workload allocations across all non-decommissioned clusters to simulate organic growth or contraction.

---

## 4. Headroom Status & Automated Node Scaling Rules

Each cluster's post-simulation status is evaluated against rigorous enterprise saturation thresholds:

$$\text{Simulated Allocation \%} = \max\left(\frac{\text{Simulated CPU Allocated}}{\text{Total Cluster CPU Cores}}, \frac{\text{Simulated RAM Allocated}}{\text{Total Cluster Memory GB}}\right) \times 100$$

| Headroom Status | Allocation Range | UI Indicator | Diagnostic & Automated Recommendation |
| :--- | :--- | :--- | :--- |
| **`OPTIMAL`** | $< 65\%$ | Emerald | High Headroom; cluster has ample spare capacity. If allocation is $< 45\%$ and worker count $> 3$, recommends: **`-1 Worker Node Downsize Safe`** (saves hardware and licensing). |
| **`HEALTHY`** | $65\% - 84.9\%$ | Blue / Primary | Balanced Utilization; standard enterprise operating sweet spot. |
| **`WARNING_TIGHT`** | $85\% - 94.9\%$ | Amber | Tight Headroom; approaches saturation limit. Buffer for burst traffic is minimal. |
| **`CRITICAL_OVERCOMMITTED`** | $\ge 95\%$ | Red / Danger | Critical Over-Commitment; high risk of scheduling failures and pod eviction. Triggers: **`+N Worker Nodes Required`** to bring utilization back below 80%. |

---

## 5. Curated Enterprise Presets (1-Click Execution)

To accelerate decision-making, the platform provides 4 pre-configured enterprise scenarios accessible via `GET /api/v1/finops/simulator/presets`:

| Preset ID | Scenario Name | Category | Target Parameters | Expected Outcome |
| :--- | :--- | :--- | :--- | :--- |
| `preset-full-severe-waste` | **Aggressive Rightsizing** | Cost Optimization | 100% adoption on `SEVERE_WASTE` tier. | Instant recovery of $10,000+/mo in cloud waste; dramatic drop in cluster allocation. |
| `preset-balanced-enterprise` | **Balanced Operations** | Pragmatic FinOps | 50% adoption on `SEVERE_WASTE` and `OVER_PROVISIONED`. | Realistic quarterly operations milestone; 0% risk of production disruption. |
| `preset-onboard-genai-stack` | **Onboard GenAI LLM Pipeline** | Capacity Planning | Injects +64 vCPU, +256 GB RAM onto `ocp-ai-training-prod`. | Verifies whether the AI cluster has sufficient headroom or needs an extra worker node. |
| `preset-consolidate-sandbox` | **Consolidate Dev Sandbox** | Consolidation | Drains `ocp-dev-us-east-sandbox` into Staging. | Retires redundant worker nodes; saves hardware and Red Hat OpenShift core subscriptions. |

---

## 6. REST API Reference

### `POST /api/v1/finops/simulator/simulate`
Executes an interactive simulation run.

#### Request Body (`WhatIfSimulationRequestDto`):
```json
{
  "rightsizingAdoptionPercent": 50,
  "targetEfficiencyRatings": ["SEVERE_WASTE", "OVER_PROVISIONED"],
  "additionalWorkloads": [
    {
      "name": "payment-fraud-mesh",
      "targetClusterName": "ocp-prod-eu-west-01",
      "requestedCpuCores": 32.0,
      "requestedMemoryGb": 128.0,
      "requestedStorageGb": 500.0
    }
  ],
  "clusterDecommissions": [],
  "fleetGrowthPercent": 0
}
```

#### Response Body (`WhatIfSimulationResultDto`):
```json
{
  "baselineMonthlySpend": 41079.65,
  "simulatedMonthlySpend": 34812.30,
  "monthlySavingsDelta": 6267.35,
  "annualizedSavingsDelta": 75208.20,
  "rightsizingMonthlySavings": 7824.10,
  "hardwareAndLicenseMonthlySavings": 0.0,
  "newWorkloadsMonthlyCost": 1556.75,
  "totalFreedCpuCores": 135.5,
  "totalFreedMemoryGb": 439.8,
  "baselineFleetCpuAllocPercent": 79.9,
  "simulatedFleetCpuAllocPercent": 68.2,
  "clusterImpacts": [
    {
      "clusterName": "ocp-prod-eu-west-01",
      "environment": "PRODUCTION",
      "baselineCpuAllocPercent": 78.4,
      "simulatedCpuAllocPercent": 84.1,
      "headroomStatus": "HEALTHY",
      "suggestedWorkerNodeDelta": 0,
      "warnings": []
    }
  ],
  "strategicRecommendations": [
    "Rightsizing at 50% adoption unlocks $7,824/month ($93,889/year) in recoverable cloud waste.",
    "Recovered 136 CPU cores and 440 GB RAM across the fleet, equivalent to ~8.5 standard worker nodes."
  ]
}
```

### `GET /api/v1/finops/simulator/presets`
Returns all pre-configured simulation scenarios with their parameters and metadata.

---

## 7. Frontend User Experience & Interaction Design

The What-If Simulator interface (`/what-if`) is built with modern HeroUI design principles:

1. **Preset Ribbon**: Top banner offering instant 1-click execution of standard corporate scenarios.
2. **Interactive Control Deck**:
   - Continuous reactive slider for adoption (0% to 100%) with instant live recalculation.
   - Dynamic workload injection form with automatic target cluster binding.
   - Cluster consolidation dropdowns with validation against self-drain loops.
   - Stress-test slider for organic fleet growth.
3. **Scoreboard & Visual Gauges**:
   - Glowing emerald cards displaying projected monthly and annualized savings.
   - Recovered CPU/RAM metrics with equivalent physical worker nodes saved.
   - Before vs After progress meters illustrating fleet-wide allocation drop.
4. **Cluster Capacity & Saturation Grid**:
   - Per-cluster progress meters showing the precise shift from baseline to simulated allocation.
   - Dynamic status badges (`OPTIMAL`, `HEALTHY`, `WARNING_TIGHT`, `CRITICAL`).
   - Actionable worker node scaling badges (`+1 Node Required` or `-1 Node Downsize`).
5. **Automated Strategic Roadmap**: Executive summary bullets ready for presentation to infrastructure stakeholders.
