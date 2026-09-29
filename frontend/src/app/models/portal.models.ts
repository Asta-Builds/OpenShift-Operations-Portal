export interface FleetOverview {
  totalClusters: number;
  activeAcmHubs: number;
  totalCpuCores: number;
  allocatedCpuCores: number;
  cpuUtilizationPercent: number;
  totalMemoryGb: number;
  allocatedMemoryGb: number;
  memoryUtilizationPercent: number;
  totalStorageGb: number;
  allocatedStorageGb: number;
  storageUtilizationPercent: number;
  totalLicenseCores: number;
  /** Clusters left out of totalLicenseCores because their nodes are unknown; above 0 the total is a lower bound. */
  clustersWithoutNodeData: number;
  clustersByEnvironment: Record<string, number>;
  clustersByInfrastructure: Record<string, number>;
}

export interface ClusterSummary {
  id: string;
  clusterName: string;
  acmHubName: string;
  environment: 'PRODUCTION' | 'STAGING' | 'DEVELOPMENT' | 'QA' | 'UNKNOWN';
  ownerTeamName: string;
  infrastructureType: 'BARE_METAL' | 'VMWARE' | 'OPENSTACK' | 'AWS' | 'AZURE' | 'GCP' | 'OTHER';
  openshiftVersion: string;
  status: string;
  totalCores: number;
  allocatedCores: number;
  totalMemoryGb: number;
  allocatedMemoryGb: number;
  licenseCores: number;
  /** False when the latest snapshot has no nodes: licenseCores is then unknown, not 0. */
  nodeDataAvailable: boolean;
  lastSnapshotTime: string;
}

export type ProviderType = 'VSPHERE' | 'AWS' | 'AZURE' | 'GCP' | 'OPENSTACK' | 'BAREMETAL' | 'OVIRT' | 'KUBEVIRT' | 'KIND' | 'OTHER' | 'UNKNOWN';

/** MATCHED: an inventory row describes the machine. CLOUD: provider-owned hardware. */
export type CorrelationStatus = 'MATCHED' | 'CLOUD' | 'NOT_IN_INVENTORY' | 'NO_PROVIDER_ID';

export interface NodeMetric {
  id: number;
  nodeName: string;
  role: 'WORKER' | 'MASTER' | 'INFRA';
  hostType: string;
  cpuCores: number;
  memoryGb: number;
  /** Instance key parsed from the providerID. */
  underlyingHostId: string | null;
  providerId?: string | null;
  providerType: ProviderType | null;
  providerZone: string | null;
  correlationStatus: CorrelationStatus;
  /** The fields below come only from a matched inventory row. */
  inventorySource: string | null;
  hypervisorHost?: string | null;
  hypervisorCluster: string | null;
  datacenter: string | null;
  sockets?: number | null;
  physicalCores: number | null;
  threadsPerCore: number | null;
}

export interface SnapshotDetail {
  id: number;
  snapshotTimestamp: string;
  totalCpuCores: number;
  allocatedCpuCores: number;
  totalMemoryGb: number;
  allocatedMemoryGb: number;
  totalStorageGb?: number;
  allocatedStorageGb?: number;
  licenseCoresCount: number;
  totalNodes: number;
  workerNodes: number;
  rawPayload: string;
}

export interface NamespaceSummary {
  id: string;
  namespaceName: string;
  /** The owning team, or "Unattributed"; never the cluster's owner by default. */
  ownerTeamName: string;
  attributed: boolean;
  /** Raw owner label value, kept even when it matches no team. */
  ownerLabelValue: string | null;
  /** False until the namespace's labels have been read (no ACM Search endpoint yet). */
  labelsCollected: boolean;
  costCenter: string | null;
  /** LABEL: the namespace's own cost-center label; TEAM: the owning team's cost center. */
  costCenterSource: 'LABEL' | 'TEAM' | null;
  cpuRequestCores: number;
  memoryRequestGb: number;
  cpuUsageCores: number | null;
  memoryUsageGb: number | null;
  pvcRequestGb: number | null;
  lastSeenAt: string | null;
  deletedAt: string | null;
}

export interface ClusterDetail {
  id: string;
  clusterName: string;
  acmHubName: string;
  environment: string;
  ownerTeamName: string;
  costCenter: string;
  infrastructureType: string;
  openshiftVersion: string;
  region: string;
  status: string;
  latestSnapshot?: SnapshotDetail;
  nodeMetrics: NodeMetric[];
  /** The namespace label that names the owning team. */
  ownerLabelKey: string;
  namespaces: NamespaceSummary[];
  recentSnapshots: SnapshotDetail[];
}

/** BREACH as soon as the known cores exceed the cap; INCOMPLETE when they do not but some clusters lack node data. */
export type ComplianceStatus = 'COMPLIANT' | 'BREACH' | 'INCOMPLETE';

export type NodeDataGapReason = 'NOT_COLLECTED' | 'NO_AGENT_REPORT' | 'STALE_AGENT_REPORT' | 'AWAITING_COLLECTION';

export interface NodeDataGap {
  clusterName: string;
  environment: string;
  reason: NodeDataGapReason;
  /** When the portal received the cluster's latest node agent report, if any. */
  lastAgentReportAt: string | null;
}

/** Latest report of one cluster's node agent (GET /node-reports). */
export interface NodeAgentStatus {
  clusterName: string;
  agentVersion: string | null;
  collectedAt: string;
  receivedAt: string;
  nodeCount: number;
  /** Whether an ACM hub has reported a cluster of this name. */
  registered: boolean;
  /** Whether collections still use the report. */
  fresh: boolean;
}

export interface LicenseAudit {
  /** Cores of the clusters whose nodes are known; a lower bound while clustersWithoutNodeData is not empty. */
  totalLicenseCores: number;
  licensedCapCores: number;
  highWatermarkCores: number;
  complianceBreach: boolean;
  complianceStatus: ComplianceStatus;
  clustersCounted: number;
  clustersWithoutNodeData: NodeDataGap[];
  workerNodesCount: number;
  masterNodesCount: number;
  bareMetalCores: number;
  virtualCores: number;
  coresByEnvironment: Record<string, number>;
  coresByOwnerTeam: Record<string, number>;
  coresByInfrastructure: Record<string, number>;
}

export interface TrendPoint {
  date: string;
  cores: number;
  memoryGb: number;
}

export interface ForecastingProjection {
  horizonDays: number;
  /** Fewer than two daily data points: no projection or runway is computed. */
  insufficientData: boolean;
  dataPoints: number;
  currentCores: number;
  projectedCores: number;
  estimatedGrowthPercent: number;
  currentMemoryGb: number;
  projectedMemoryGb: number;
  dailyGrowthRateCores: number;
  coresRSquared: number | null;
  memoryRSquared: number | null;
  totalCapacityCores: number;
  totalCapacityMemoryGb: number;
  /** 0 = capacity already reached, null = no projected exhaustion (or capacity unknown). */
  runwayDaysCores: number | null;
  runwayDaysMemory: number | null;
  exhaustionDateCores: string | null;
  exhaustionDateMemory: string | null;
  capacityAlert: boolean;
  historicalPoints: TrendPoint[];
  projectedPoints: TrendPoint[];
}

/** SKIPPED_CIRCUIT_OPEN: the hub failed repeatedly, so its circuit breaker paused calls to it. */
export type SyncStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'SKIPPED_CIRCUIT_OPEN';
/** DEGRADED: some clusters could not be collected; ERROR: the hub refused the request (token or permissions). */
export type HubStatus = 'ACTIVE' | 'UNREACHABLE' | 'DEGRADED' | 'ERROR';

/** One collection of one hub. */
export interface HubSyncRun {
  id: number;
  status: SyncStatus;
  startedAt: string;
  finishedAt: string | null;
  attempts: number;
  clustersOk: number;
  clustersFailed: number;
  errorMessage: string | null;
}

export interface AcmHubSummary {
  id: string;
  name: string;
  apiUrl: string;
  credentialsSecretRef: string | null;
  observabilityUrl: string | null;
  /** ACM Search endpoint; namespace ownership is only read when it is set. */
  searchUrl: string | null;
  status: HubStatus;
  /** Last collection that got data from the hub. */
  lastSyncTimestamp: string | null;
  consecutiveFailures: number;
  /** CLOSED, OPEN or HALF_OPEN; held in memory by the backend instance that answered. */
  circuitBreakerState: string;
  clusterCount: number;
  /** Whether the hub's token Secret is mounted; null for simulated hubs, which read none. */
  credentialsMounted: boolean | null;
  latestSyncRun: HubSyncRun | null;
}

/** What an admin enters to register or change a hub; empty optional URLs mean "not configured". */
export interface HubSettings {
  name: string;
  apiUrl: string;
  credentialsSecretRef: string;
  observabilityUrl: string;
  searchUrl: string;
}

export type HubCheckTarget = 'CREDENTIALS' | 'API' | 'OBSERVABILITY' | 'SEARCH';
/** SKIPPED: not configured, or not tried because an earlier check failed. */
export type HubCheckStatus = 'OK' | 'WARNING' | 'FAILED' | 'SKIPPED';

export interface HubConnectionTest {
  /** False when any check failed; warnings do not count. */
  ok: boolean;
  checks: { target: HubCheckTarget; status: HubCheckStatus; message: string; durationMs: number }[];
}

export type PortalRole = 'ADMIN' | 'OPERATOR' | 'VIEWER';

export interface PortalAuthConfig {
  enabled: boolean;
  /** OpenID Connect issuer as the browser reaches it; null when security is disabled. */
  issuer: string | null;
  clientId: string | null;
}

export interface CurrentUser {
  username: string;
  name: string | null;
  /** Includes roles implied by the hierarchy: an ADMIN also has OPERATOR and VIEWER. */
  roles: PortalRole[];
}

export interface SimulatorStatus {
  simulateFailureActive: boolean;
  hubOutages: string[];
  failingClusters: string[];
}

export interface SnapshotTriggerResult {
  clustersProcessed: number;
  snapshotsCreated: number;
  durationMs: number;
  status: string;
  message: string;
}

export type ReportType = 'FLEET_CAPACITY' | 'LICENSE_AUDIT' | 'COST_ATTRIBUTION' | 'GROWTH_FORECAST';
export type ReportFormat = 'CSV' | 'PDF';
/** NOT_SENT: no SMTP server is configured. */
export type NotificationStatus = 'QUEUED' | 'SENT' | 'NOT_SENT' | 'FAILED';
export type NotificationKind = 'SCHEDULED_REPORT' | 'LICENSE_BREACH' | 'CAPACITY_RUNWAY';

/** A report emailed to its recipients on a cron schedule. */
export interface ReportSchedule {
  id: string;
  title: string;
  reportType: ReportType;
  format: ReportFormat;
  /** Six-field Spring cron (seconds first), in the portal's time zone. */
  cronSchedule: string;
  recipients: string;
  enabled: boolean;
  createdAt: string;
  /** When the schedule last came due; "send now" leaves it alone. */
  lastRunAt: string | null;
  nextRunAt: string | null;
  lastDelivery: { status: NotificationStatus; at: string; detail: string | null } | null;
}

export interface NewReportSchedule {
  title: string;
  reportType: ReportType;
  format: ReportFormat;
  /** Five-field Unix cron, e.g. "0 7 * * MON". */
  cronSchedule: string;
  /** Addresses separated by commas, semicolons or spaces. */
  recipients: string;
}

/** One email the portal sent or could not send: a scheduled report or an alert. */
export interface PortalNotification {
  id: number;
  kind: NotificationKind;
  reportId: string | null;
  subject: string;
  recipients: string;
  status: NotificationStatus;
  detail: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface SavedReport {
  id: string;
  title: string;
  userId: string;
  reportType: string;
  parametersJson?: string;
  createdAt: string;
}

export type Environment = ClusterSummary['environment'];

export interface AttributionRow {
  /** Null for the Unattributed and total rows. */
  teamId: string | null;
  teamName: string;
  costCenter: string | null;
  namespaceCount: number;
  clusterCount: number;
  cpuRequestCores: number;
  memoryRequestGb: number;
  /** Null when no namespace in the row has usage data. */
  cpuUsageCores: number | null;
  memoryUsageGb: number | null;
  pvcRequestGb: number | null;
  cpuSharePercent: number;
}

/** Averages over the period's collections, by team and cost center. */
export interface AttributionReport {
  from: string;
  to: string;
  environment: Environment | null;
  ownerLabelKey: string;
  costCenterLabelKey: string;
  teams: AttributionRow[];
  unattributed: AttributionRow;
  total: AttributionRow;
  clusterCpuRequestCores: number;
  clusterMemoryRequestGb: number;
  /** Namespace CPU requests as a share of cluster requests over the same collections; 100 when they reconcile. */
  cpuCoveragePercent: number | null;
  /** Cluster snapshots in the period, and how many carried namespace data (the ones averaged over). */
  collections: number;
  collectionsWithNamespaceData: number;
  unmappedOwners: { ownerLabelValue: string; namespaceCount: number }[];
  namespacesWithoutOwnerLabel: number;
  namespacesWithoutLabels: number;
}

export interface Team {
  id: string;
  name: string;
  costCenter: string | null;
  contactEmail: string | null;
  aliases: string[];
  namespaceCount: number;
}

export interface TopologyNode {
  clusterId: string;
  clusterName: string;
  nodeName: string;
  role: 'WORKER' | 'MASTER' | 'INFRA';
  cpuCores: number;
  memoryGb: number;
  providerType: ProviderType | null;
  instanceKey: string | null;
  zone: string | null;
  status: CorrelationStatus;
  inventorySource: string | null;
  sockets: number | null;
  physicalCores: number | null;
  threadsPerCore: number | null;
}

export interface InfrastructureTopology {
  summary: { nodes: number; matched: number; cloud: number; notInInventory: number; noProviderId: number };
  hypervisorClusters: {
    datacenter: string | null;
    hypervisorCluster: string | null;
    hosts: {
      hypervisorHost: string;
      sockets: number | null;
      physicalCores: number | null;
      threadsPerCore: number | null;
      inventorySource: string | null;
      nodes: TopologyNode[];
    }[];
  }[];
  bareMetal: { datacenter: string | null; nodes: TopologyNode[] }[];
  cloud: { providerType: ProviderType; zone: string | null; nodes: TopologyNode[] }[];
  notInInventory: TopologyNode[];
  noProviderId: TopologyNode[];
}

export interface InventoryRow {
  id: number;
  source: string;
  providerType: ProviderType;
  instanceKey: string;
  hypervisorHost: string | null;
  hypervisorCluster: string | null;
  datacenter: string | null;
  physicalSockets: number | null;
  physicalCores: number | null;
  threadsPerCore: number | null;
  syncedAt: string;
}

export interface InventoryImportResult {
  source: string;
  rows: number;
  inserted: number;
  updated: number;
  removed: number;
  /** Nodes of the latest snapshots now matched to an inventory row, from any source. */
  matchedNodes: number;
}

export type FinOpsEfficiencyRating = 'OPTIMAL' | 'ACCEPTABLE' | 'OVER_PROVISIONED' | 'SEVERE_WASTE' | 'UNDER_PROVISIONED';
export type FinOpsRecommendationAction = 'DOWNSIZE_CPU_AND_RAM' | 'DOWNSIZE_CPU' | 'DOWNSIZE_RAM' | 'MAINTAIN_SIZING' | 'UPSIZE_RESOURCES';

export interface FinOpsPricingConfig {
  cpuHourlyRate: number;
  memoryHourlyRate: number;
  storageMonthlyRate: number;
  currency: string;
}

export interface FinOpsNamespaceRecommendation {
  namespaceId: string;
  namespaceName: string;
  clusterId: string;
  clusterName: string;
  environment: Environment;
  teamName: string;
  costCenter: string;
  avgCpuRequestCores: number;
  avgCpuUsageCores: number;
  cpuEfficiencyPercent: number;
  avgMemoryRequestGb: number;
  avgMemoryUsageGb: number;
  memoryEfficiencyPercent: number;
  pvcRequestGb: number;
  monthlyAllocatedCost: number;
  monthlyActualCost: number;
  monthlyWastedCost: number;
  overallEfficiencyPercent: number;
  rating: FinOpsEfficiencyRating;
  action: FinOpsRecommendationAction;
  recommendedCpuRequestCores: number;
  recommendedMemoryRequestGb: number;
  monthlyPotentialSavings: number;
  suggestedResourceQuotaYaml: string;
}

export interface FinOpsTeamBreakdown {
  teamName: string;
  costCenter: string;
  namespaceCount: number;
  monthlyAllocatedCost: number;
  monthlyActualCost: number;
  monthlyWastedCost: number;
  monthlyPotentialSavings: number;
  costSharePercent: number;
  efficiencyScorePercent: number;
}

export interface FinOpsOverview {
  from: string;
  to: string;
  environment: Environment | null;
  currency: string;
  totalMonthlyAllocatedCost: number;
  totalMonthlyActualCost: number;
  totalMonthlyWastedCost: number;
  totalAnnualizedSavingsPotential: number;
  overallFleetEfficiencyPercent: number;
  totalNamespacesAnalyzed: number;
  severeWasteNamespacesCount: number;
  overProvisionedNamespacesCount: number;
  acceptableNamespacesCount: number;
  optimalNamespacesCount: number;
  underProvisionedNamespacesCount: number;
  teamBreakdowns: FinOpsTeamBreakdown[];
  costByEnvironment: Record<string, number>;
  topWastefulNamespaces: FinOpsNamespaceRecommendation[];
  pricing: FinOpsPricingConfig;
}

export type HeadroomStatus = 'OPTIMAL' | 'HEALTHY' | 'WARNING_TIGHT' | 'CRITICAL_OVERCOMMITTED';
export type InfrastructureType = 'BARE_METAL' | 'VMWARE' | 'OPENSTACK' | 'AWS' | 'AZURE' | 'GCP' | 'OTHER' | string;

export interface WhatIfWorkload {
  name: string;
  targetClusterId?: string;
  targetClusterName?: string;
  requestedCpuCores: number;
  requestedMemoryGb: number;
  requestedStorageGb: number;
  environment?: Environment;
  ownerTeam?: string;
}

export interface WhatIfDecommission {
  sourceClusterId?: string;
  sourceClusterName?: string;
  targetClusterId?: string;
  targetClusterName?: string;
}

export interface WhatIfSimulationRequest {
  rightsizingAdoptionPercent: number;
  targetEfficiencyRatings?: FinOpsEfficiencyRating[];
  additionalWorkloads?: WhatIfWorkload[];
  clusterDecommissions?: WhatIfDecommission[];
  fleetGrowthPercent?: number;
}

export interface WhatIfClusterImpact {
  clusterId: string;
  clusterName: string;
  environment: Environment;
  infrastructureType: InfrastructureType;
  decommissioned: boolean;
  totalCores: number;
  baselineAllocatedCores: number;
  simulatedAllocatedCores: number;
  baselineCpuAllocPercent: number;
  simulatedCpuAllocPercent: number;
  totalMemoryGb: number;
  baselineAllocatedMemoryGb: number;
  simulatedAllocatedMemoryGb: number;
  baselineMemoryAllocPercent: number;
  simulatedMemoryAllocPercent: number;
  totalStorageGb: number;
  baselineAllocatedStorageGb: number;
  simulatedAllocatedStorageGb: number;
  headroomStatus: HeadroomStatus;
  statusDescription: string;
  suggestedWorkerNodeDelta: number;
  estimatedLicenseCoreDelta: number;
  monthlyCostDelta: number;
  warnings: string[];
}

export interface WhatIfSimulationResult {
  baselineMonthlySpend: number;
  simulatedMonthlySpend: number;
  monthlySavingsDelta: number;
  annualizedSavingsDelta: number;
  rightsizingMonthlySavings: number;
  hardwareAndLicenseMonthlySavings: number;
  newWorkloadsMonthlyCost: number;
  totalFreedCpuCores: number;
  totalFreedMemoryGb: number;
  totalFreedStorageGb: number;
  baselineFleetCpuAllocPercent: number;
  simulatedFleetCpuAllocPercent: number;
  baselineFleetMemoryAllocPercent: number;
  simulatedFleetMemoryAllocPercent: number;
  totalWorkerNodesDelta: number;
  totalLicenseCoresDelta: number;
  clusterImpacts: WhatIfClusterImpact[];
  globalWarnings: string[];
  strategicRecommendations: string[];
}
export interface WhatIfPreset {
  id: string;
  title: string;
  category: string;
  description: string;
  icon: string;
  badge: string;
  request: WhatIfSimulationRequest;
}

export type TopologyNodeType = 'HUB' | 'CLUSTER' | 'NODE' | 'NAMESPACE' | 'HOST';

export interface GraphNode {
  id: string;
  label: string;
  type: TopologyNodeType;
  parentId?: string | null;
  status?: string | null;
  environment?: string | null;
  team?: string | null;
  role?: string | null;
  cpuCores?: number | null;
  memoryGb?: number | null;
  efficiencyPercent?: number | null;
  rating?: string | null;
  monthlyCost?: number | null;
  metadata?: Record<string, any>;
  x?: number;
  y?: number;
  vx?: number;
  vy?: number;
  fx?: number | null;
  fy?: number | null;
}

export interface GraphLink {
  source: any;
  target: any;
  type: string;
  value: number;
}

export interface GraphSummary {
  totalHubs: number;
  totalClusters: number;
  totalNodes: number;
  totalNamespaces: number;
  totalPhysicalHosts: number;
  totalCores: number;
  totalMemoryGb: number;
}

export interface TopologyGraph {
  nodes: GraphNode[];
  links: GraphLink[];
  summary: GraphSummary;
}
