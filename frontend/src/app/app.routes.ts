import { Routes } from '@angular/router';
import { FleetOverviewComponent } from './pages/fleet-overview/fleet-overview.component';
import { ClusterInventoryComponent } from './pages/clusters/cluster-inventory.component';
import { ClusterDetailComponent } from './pages/clusters/cluster-detail.component';
import { LicensingComponent } from './pages/licensing/licensing.component';
import { ForecastingComponent } from './pages/forecasting/forecasting.component';
import { ReportGeneratorComponent } from './pages/reports/report-generator.component';
import { SimulatorComponent } from './pages/simulator/simulator.component';
import { AttributionComponent } from './pages/attribution/attribution.component';

export const routes: Routes = [
  { path: '', redirectTo: 'overview', pathMatch: 'full' },
  { path: 'overview', component: FleetOverviewComponent },
  { path: 'clusters', component: ClusterInventoryComponent },
  { path: 'clusters/:id', component: ClusterDetailComponent },
  { path: 'attribution', component: AttributionComponent },
  { path: 'licensing', component: LicensingComponent },
  { path: 'forecasting', component: ForecastingComponent },
  { path: 'reports', component: ReportGeneratorComponent },
  { path: 'simulator', component: SimulatorComponent },
  { path: '**', redirectTo: 'overview' }
];
