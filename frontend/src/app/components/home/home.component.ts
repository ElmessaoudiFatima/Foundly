import { Component, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

interface LegalForm {
  name: string;
  color: string;
  cost: string;
  featured?: boolean;
  points: { text: string; positive: boolean }[];
}

interface SimulationResult {
  delay: string;
  cost: string;
  steps: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {
  readonly authService = inject(AuthService);

  isScrolled = false;
  menuOpen = false;
  openFaq: number | null = null;
  selectedForm = 'sarlau';
  selectedRegion = 'tanger';
  toastMessage = '';
  simulation: SimulationResult = {
    delay: '9 – 15 jours ouvrés',
    cost: '5 000 – 10 000 DH',
    steps: '8 étapes'
  };

  readonly legalForms: LegalForm[] = [
    {
      name: 'Auto-Entrepreneur', color: 'linear-gradient(135deg,#6366f1,#8b5cf6)', cost: 'Gratuit',
      points: [
        { text: 'Démarche ultra-simplifiée', positive: true },
        { text: 'Pas de capital minimum', positive: true },
        { text: 'Activité individuelle', positive: true },
        { text: 'CA limité par secteur', positive: false }
      ]
    },
    {
      name: 'SARL-AU', color: 'linear-gradient(135deg,#1d4ed8,#2563eb)', cost: '5 000 – 10 000 DH', featured: true,
      points: [
        { text: 'Associé unique', positive: true },
        { text: 'Capital libre (100 DH min.)', positive: true },
        { text: 'Responsabilité limitée', positive: true },
        { text: '64,8 % des créations 2025', positive: true }
      ]
    },
    {
      name: 'SARL', color: 'linear-gradient(135deg,#0891b2,#06b6d4)', cost: '8 000 – 15 000 DH',
      points: [
        { text: 'Plusieurs associés', positive: true },
        { text: 'Capital libre', positive: true },
        { text: 'Responsabilité limitée', positive: true },
        { text: 'Gérance obligatoire', positive: false }
      ]
    },
    {
      name: 'SA', color: 'linear-gradient(135deg,#0f766e,#14b8a6)', cost: '15 000 – 30 000 DH',
      points: [
        { text: 'Capital : 300 000 DH min.', positive: true },
        { text: "Appel public à l'épargne", positive: true },
        { text: '5 actionnaires min.', positive: true },
        { text: 'Formalités plus lourdes', positive: false }
      ]
    }
  ];

  readonly faqs = [
    {
      question: 'Foundly est-elle une plateforme officielle du gouvernement marocain ?',
      answer: "Non. Foundly est une plateforme académique développée dans le cadre du module Frameworks Java / Spring Boot (ENSA Tétouan). Elle simule le guichet unique et n'est pas connectée aux systèmes réels de l'OMPIC, CRI ou DGI. Les documents générés n'ont aucune valeur juridique."
    },
    {
      question: 'Quelles formes juridiques sont prises en charge ?',
      answer: 'Foundly prend en charge quatre formes : Auto-Entrepreneur, SARL-AU, SARL et SA. Chaque forme déclenche un workflow et une checklist de documents adaptés.'
    },
    {
      question: 'Combien coûte le certificat négatif OMPIC ?',
      answer: 'Le certificat négatif OMPIC coûte 230 DH et est valable 6 mois. Ce paiement est simulé dans la plateforme, aucune transaction bancaire réelle n’est effectuée.'
    },
    {
      question: 'Mes données personnelles sont-elles protégées ?',
      answer: 'Foundly applique les principes de la loi n°09-08 relative à la protection des données à caractère personnel (CNDP). Les données simulées sont chiffrées au repos et l’accès est contrôlé par rôle.'
    },
    {
      question: 'Quel est le délai moyen pour créer une SARL-AU ?',
      answer: 'Selon les données OMPIC 2025, le délai moyen via le CRI est de 9 à 15 jours ouvrés. Foundly vise à optimiser ce délai grâce à la vérification préalable de la complétude du dossier.'
    }
  ];

  private toastTimer?: ReturnType<typeof setTimeout>;

  @HostListener('window:scroll')
  updateScrolledState(): void {
    this.isScrolled = window.scrollY > 40;
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  closeMenu(): void {
    this.menuOpen = false;
  }

  toggleFaq(index: number): void {
    this.openFaq = this.openFaq === index ? null : index;
  }

  runSimulation(): void {
    const results: Record<string, SimulationResult> = {
      ae: { delay: '3 – 7 jours', cost: 'Gratuit (inscription en ligne)', steps: '3 étapes' },
      sarlau: { delay: '9 – 15 jours ouvrés', cost: '5 000 – 10 000 DH', steps: '8 étapes' },
      sarl: { delay: '10 – 18 jours ouvrés', cost: '8 000 – 15 000 DH', steps: '8 étapes' },
      sa: { delay: '15 – 30 jours ouvrés', cost: '15 000 – 30 000 DH', steps: '10 étapes' }
    };

    const result = results[this.selectedForm] ?? results['sarlau'];
    this.simulation = {
      ...result,
      delay: this.selectedRegion === 'tanger' ? `${result.delay} (estimation CRI rapide)` : result.delay
    };
  }

  showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => this.toastMessage = '', 3200);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }
}
