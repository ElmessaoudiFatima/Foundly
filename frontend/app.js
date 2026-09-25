/* ===================================================================
   Foundly — app.js
   Interactions, animations, simulateur, FAQ accordion
   =================================================================== */

'use strict';

// ── NAVBAR SCROLL ────────────────────────────────────────────────
const navbar = document.getElementById('navbar');

window.addEventListener('scroll', () => {
  if (window.scrollY > 40) {
    navbar.classList.add('scrolled');
  } else {
    navbar.classList.remove('scrolled');
  }
}, { passive: true });

// ── HAMBURGER MENU ────────────────────────────────────────────────
const hamburger = document.getElementById('hamburger');
const navLinks  = document.getElementById('nav-links');

hamburger.addEventListener('click', () => {
  navLinks.classList.toggle('open');
  const bars = hamburger.querySelectorAll('span');
  if (navLinks.classList.contains('open')) {
    bars[0].style.transform = 'rotate(45deg) translate(5px, 5px)';
    bars[1].style.opacity   = '0';
    bars[2].style.transform = 'rotate(-45deg) translate(5px, -5px)';
  } else {
    bars[0].style.transform = '';
    bars[1].style.opacity   = '';
    bars[2].style.transform = '';
  }
});

// Fermer le menu si on clique sur un lien
navLinks.querySelectorAll('.nav-link').forEach(link => {
  link.addEventListener('click', () => {
    navLinks.classList.remove('open');
    const bars = hamburger.querySelectorAll('span');
    bars.forEach(b => { b.style.transform = ''; b.style.opacity = ''; });
  });
});

// ── COUNTER ANIMATION (HERO STATS) ───────────────────────────────
function animateCounter(el) {
  const target = parseInt(el.dataset.target, 10);
  const duration = 2000;
  const step = 16;
  const steps = duration / step;
  const increment = target / steps;
  let current = 0;

  const timer = setInterval(() => {
    current += increment;
    if (current >= target) {
      current = target;
      clearInterval(timer);
    }
    el.textContent = Math.floor(current).toLocaleString('fr-FR');
  }, step);
}

// ── INTERSECTION OBSERVER — REVEAL ANIMATIONS ─────────────────────
const revealEls = document.querySelectorAll(
  '.step-card, .forme-card, .faq-item, .sim-result-card'
);

revealEls.forEach((el, i) => {
  el.classList.add('reveal');
  el.style.transitionDelay = `${(i % 4) * 0.1}s`;
});

const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });

revealEls.forEach(el => observer.observe(el));

// ── HERO STATS COUNTER (on scroll into view) ──────────────────────
const counterEls = document.querySelectorAll('.stat-number[data-target]');
let countersStarted = false;

const heroStatsEl = document.getElementById('hero-stats');
const statsObserver = new IntersectionObserver((entries) => {
  if (entries[0].isIntersecting && !countersStarted) {
    countersStarted = true;
    counterEls.forEach(el => animateCounter(el));
    statsObserver.disconnect();
  }
}, { threshold: 0.5 });

if (heroStatsEl) statsObserver.observe(heroStatsEl);

// ── PROGRESS BAR ANIMATION (MOCKUP) ──────────────────────────────
const progressFill = document.getElementById('progress-fill');
const progressPct  = document.getElementById('progress-pct');

const progressObserver = new IntersectionObserver((entries) => {
  if (entries[0].isIntersecting) {
    setTimeout(() => {
      progressFill.style.width = '62%';
    }, 600);
    progressObserver.disconnect();
  }
}, { threshold: 0.3 });

if (progressFill) progressObserver.observe(progressFill);

// ── FAQ ACCORDION ─────────────────────────────────────────────────
const faqItems = document.querySelectorAll('.faq-item');

faqItems.forEach(item => {
  const btn = item.querySelector('.faq-question');
  btn.addEventListener('click', () => {
    const isOpen = item.classList.contains('open');

    // Fermer tous les autres
    faqItems.forEach(i => i.classList.remove('open'));

    // Toggle courant
    if (!isOpen) item.classList.add('open');
  });
});

// ── SIMULATEUR ────────────────────────────────────────────────────
const simData = {
  ae: {
    delai:  '3 – 7 jours',
    cout:   'Gratuit (inscription en ligne)',
    etapes: '3 étapes',
  },
  sarlau: {
    delai:  '9 – 15 jours ouvrés',
    cout:   '5 000 – 10 000 DH',
    etapes: '8 étapes',
  },
  sarl: {
    delai:  '10 – 18 jours ouvrés',
    cout:   '8 000 – 15 000 DH',
    etapes: '8 étapes',
  },
  sa: {
    delai:  '15 – 30 jours ouvrés',
    cout:   '15 000 – 30 000 DH',
    etapes: '10 étapes',
  },
};

// Modificateurs région (symboliques)
const regionMod = {
  casa:      { delai: '', cout: '' },
  rabat:     { delai: '', cout: '' },
  tanger:    { delai: ' (délai CRI estimé : rapide)', cout: '' },
  fes:       { delai: '', cout: '' },
  marrakech: { delai: '', cout: '' },
};

const btnSimulate   = document.getElementById('btn-simulate');
const simForme      = document.getElementById('sim-forme');
const simRegion     = document.getElementById('sim-region');
const resDelaiVal   = document.getElementById('res-delai-val');
const resCoutVal    = document.getElementById('res-cout-val');
const resEtapesVal  = document.getElementById('res-etapes-val');

function runSimulation() {
  const forme  = simForme.value;
  const region = simRegion.value;
  const data   = simData[forme];
  const mod    = regionMod[region] || { delai: '', cout: '' };

  // Petit effet de "chargement"
  [resDelaiVal, resCoutVal, resEtapesVal].forEach(el => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(4px)';
  });

  setTimeout(() => {
    resDelaiVal.textContent  = data.delai  + mod.delai;
    resCoutVal.textContent   = data.cout   + mod.cout;
    resEtapesVal.textContent = data.etapes;

    [resDelaiVal, resCoutVal, resEtapesVal].forEach(el => {
      el.style.transition = 'opacity 0.3s, transform 0.3s';
      el.style.opacity    = '1';
      el.style.transform  = 'translateY(0)';
    });
  }, 250);
}

btnSimulate.addEventListener('click', runSimulation);

// Résultats initiaux au chargement
window.addEventListener('DOMContentLoaded', () => {
  runSimulation();
});

// ── SMOOTH SCROLL pour les ancres internes ────────────────────────
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
  anchor.addEventListener('click', (e) => {
    const targetId = anchor.getAttribute('href');
    if (targetId === '#') return;
    const target = document.querySelector(targetId);
    if (target) {
      e.preventDefault();
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });
});

// ── PARALLAX LÉGER SUR LES ORBES DU HERO ─────────────────────────
const orbs = document.querySelectorAll('.hero-orb');

window.addEventListener('mousemove', (e) => {
  const cx = window.innerWidth  / 2;
  const cy = window.innerHeight / 2;
  const dx = (e.clientX - cx) / cx;
  const dy = (e.clientY - cy) / cy;

  orbs.forEach((orb, i) => {
    const factor = (i + 1) * 10;
    orb.style.transform = `translate(${dx * factor}px, ${dy * factor}px)`;
  });
}, { passive: true });

// ── TYPING EFFECT (optionnel, sur le badge hero) ──────────────────
// Déjà géré par CSS animation

// ── GESTION DES BOUTONS CTA (pour demo) ──────────────────────────
const ctaBtns = document.querySelectorAll(
  '#btn-start, #btn-hero-start, #btn-cta, #btn-ae, #btn-sarlau, #btn-sarl, #btn-sa'
);

ctaBtns.forEach(btn => {
  btn.addEventListener('click', (e) => {
    e.preventDefault();
    showToast('🚀 Redirection vers la page d\'inscription... (API en cours de développement)');
  });
});

document.getElementById('btn-login')?.addEventListener('click', (e) => {
  e.preventDefault();
  showToast('🔐 Redirection vers la page de connexion... (API en cours de développement)');
});

// ── TOAST NOTIFICATION ────────────────────────────────────────────
function showToast(message) {
  // Supprimer toast existant
  const existing = document.querySelector('.foundly-toast');
  if (existing) existing.remove();

  const toast = document.createElement('div');
  toast.className = 'foundly-toast';
  toast.textContent = message;
  toast.style.cssText = `
    position: fixed;
    bottom: 32px;
    left: 50%;
    transform: translateX(-50%) translateY(20px);
    background: #1F3864;
    border: 1px solid rgba(74,159,231,0.4);
    color: #F1F5FF;
    padding: 14px 24px;
    border-radius: 12px;
    font-size: 0.88rem;
    font-weight: 500;
    box-shadow: 0 8px 32px rgba(0,0,0,0.4);
    z-index: 9999;
    max-width: 480px;
    text-align: center;
    opacity: 0;
    transition: all 0.35s cubic-bezier(0.4,0,0.2,1);
    backdrop-filter: blur(12px);
  `;
  document.body.appendChild(toast);

  // Entrée
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      toast.style.opacity = '1';
      toast.style.transform = 'translateX(-50%) translateY(0)';
    });
  });

  // Sortie après 3.5s
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(-50%) translateY(20px)';
    setTimeout(() => toast.remove(), 400);
  }, 3500);
}

// ── LOG DE DÉMARRAGE ──────────────────────────────────────────────
console.log('%cFoundly 🚀', 'color:#4A9FE7;font-size:1.4rem;font-weight:800');
console.log('%cPlateforme de digitalisation de la création d\'entreprise au Maroc',
  'color:#94A3B8;font-size:0.85rem');
console.log('%cENSA Tétouan — Module Frameworks Java / Spring Boot — 2026/2027',
  'color:#64748B;font-size:0.75rem');
