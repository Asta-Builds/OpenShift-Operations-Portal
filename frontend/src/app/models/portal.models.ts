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
  environment: 'PRODUCTION' | 'STAGING' | 'DEVELOPMENT' | 'QA';
  ownerTeamName: string;
  infrastructureType: 'BARE_METAL' | 'VMWARE' | 'OPENSTACK' | 'AWS' | 'AZURE' | 'GCP';
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
  ownerTeamName: string;
  costCenter: string;
  cpuRequestCores: number;
  memoryRequestGb: number;
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
  currentCores: number;
  projectedCores: number;
  estimatedGrowthPercent: number;
  currentMemoryGb: number;
  projectedMemoryGb: number;
  dailyGrowthRateCores: number;
  confidenceScore: number;
  totalCapacityCores: number;
  totalCapacityMemoryGb: number;
  runwayDaysCores?: number;
  runwayDaysMemory?: number;
  exhaustionDateCores?: string;
  exhaustionDateMemory?: string;
  capacityAlert: boolean;
  historicalPoints: TrendPoint[];
  projectedPoints: TrendPoint[];
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
