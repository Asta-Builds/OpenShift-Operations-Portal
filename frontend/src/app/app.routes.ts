import { Routes } from '@angular/router';
import { FleetOverviewComponent } from './pages/fleet-overview/fleet-overview.component';
import { ClusterInventoryComponent } from './pages/clusters/cluster-inventory.component';
import { ClusterDetailComponent } from './pages/clusters/cluster-detail.component';
import { LicensingComponent } from './pages/licensing/licensing.component';
import { ForecastingComponent } from './pages/forecasting/forecasting.component';
import { ReportGeneratorComponent } from './pages/reports/report-generator.component';
import { SimulatorComponent } from './pages/simulator/simulator.component';
import { AttributionComponent } from './pages/attribution/attribution.component';
import { InfrastructureComponent } from './pages/infrastructure/infrastructure.component';
import { HubManagementComponent } from './pages/hubs/hub-management.component';
import { FinOpsComponent } from './pages/finops/finops.component';
import { WhatIfSimulatorComponent } from './pages/finops/what-if-simulator.component';

export const routes: Routes = [
  { path: '', redirectTo: 'overview', pathMatch: 'full' },
  { path: 'overview', component: FleetOverviewComponent },
  { path: 'clusters', component: ClusterInventoryComponent },
  { path: 'clusters/:id', component: ClusterDetailComponent },
  { path: 'infrastructure', component: InfrastructureComponent },
  { path: 'attribution', component: AttributionComponent },
  { path: 'finops', component: FinOpsComponent },
  { path: 'finops/what-if', component: WhatIfSimulatorComponent },
  { path: 'what-if', component: WhatIfSimulatorComponent },
  { path: 'licensing', component: LicensingComponent },
  { path: 'forecasting', component: ForecastingComponent },
  { path: 'reports', component: ReportGeneratorComponent },
  { path: 'hubs', component: HubManagementComponent },
  { path: 'simulator', component: SimulatorComponent },
  { path: '**', redirectTo: 'overview' }
];
