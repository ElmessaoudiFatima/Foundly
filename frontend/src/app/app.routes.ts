import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { authGuard, guestGuard } from './guards/auth.guard';

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
    path: '**',
    redirectTo: ''
  }
];
