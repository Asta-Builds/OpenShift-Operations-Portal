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
  lastSnapshotTime: string;
}

export interface NodeMetric {
  id: number;
  nodeName: string;
  role: 'WORKER' | 'MASTER' | 'INFRA';
  hostType: string;
  cpuCores: number;
  memoryGb: number;
  underlyingHostId: string;
  providerId?: string;
  hypervisorHost?: string;
  sockets?: number;
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

export interface LicenseAudit {
  totalLicenseCores: number;
  licensedCapCores: number;
  highWatermarkCores: number;
  complianceBreach: boolean;
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

export interface HubSyncRun {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'SKIPPED_CIRCUIT_OPEN';
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
  status: 'ACTIVE' | 'UNREACHABLE' | 'DEGRADED' | 'ERROR';
  lastSyncTimestamp: string | null;
  consecutiveFailures: number;
  /** Held in memory by the backend instance that answered. */
  circuitBreakerState: string;
  latestSyncRun: HubSyncRun | null;
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

export interface ReportDefinition {
  id: string;
  title: string;
  reportType: 'FLEET_CAPACITY' | 'LICENSE_AUDIT' | 'COST_ATTRIBUTION' | 'GROWTH_FORECAST';
  cronSchedule?: string;
  recipients?: string;
  isEnabled: boolean;
  createdAt: string;
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
