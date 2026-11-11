import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { AdminComponent } from './components/admin/admin.component';
import { AgentComponent } from './components/agent/agent.component';
import { authGuard, guestGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';
import { agentGuard } from './guards/agent.guard';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
    title: "Foundly — Création d'entreprise au Maroc"
  },
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [guestGuard],
    title: 'Connexion — Foundly'
  },
  {
    path: 'register',
    component: RegisterComponent,
    canActivate: [guestGuard],
    title: 'Inscription Entrepreneur — Foundly'
  },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard],
    title: 'Mon Espace Entrepreneur — Foundly'
  },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [adminGuard],
    title: 'Administration centrale — Foundly'
  },
  {
    path: 'agent',
    component: AgentComponent,
    canActivate: [agentGuard],
    title: 'Espace Agent Administratif — Foundly'
  },
  {
    path: '**',
    redirectTo: ''
  }
];
