import { Routes } from '@angular/router';
import { ApplicationListComponent } from './list/application-list.component';
import { CreateApplicationComponent } from './create/create-application.component';
import { ApplicationDetailComponent } from './detail/application-detail.component';

export const APPLICATION_ROUTES: Routes = [
  { path: '', component: ApplicationListComponent },
  { path: 'new', component: CreateApplicationComponent },
  { path: ':id', component: ApplicationDetailComponent }
];
