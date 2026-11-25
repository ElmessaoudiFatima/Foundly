import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Associe, Gerant, ModeNominationGerant } from '../../models/entrepreneur.models';

@Component({
  selector: 'app-gerants-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="gerants-container">
      <!-- En-tête -->
      <div class="manager-header">
        <div>
          <h4>Gérance de la société</h4>
          <p class="manager-subtitle">
            Désignez au moins <strong>un gérant</strong> (associé ou tiers non-associé) pour diriger la société.
          </p>
        </div>
        @if (!readonly) {
          <button type="button" class="btn btn-outline-primary btn-sm" (click)="openAddModal()">
            + Désigner un gérant
          </button>
        }
      </div>

      <!-- Statut / Compteur -->
      <div class="stats-bar">
        <div class="stat-pill" [class.valid]="activeGerants.length >= 1" [class.invalid]="activeGerants.length === 0">
          <span class="stat-label">Gérants actifs</span>
          <strong>{{ activeGerants.length }} désigné{{ activeGerants.length > 1 ? 's' : '' }}</strong>
          @if (activeGerants.length === 0) {
            <small>(minimum 1 obligatoire)</small>
          }
        </div>
        <div class="stat-pill neutral">
          <span class="stat-label">Répartition</span>
          <span><strong>{{ associesGerantsCount }}</strong> associé(s) · <strong>{{ tiersGerantsCount }}</strong> tiers</span>
        </div>
      </div>

      <!-- Avertissements -->
      @if (warningMessages.length > 0) {
        <div class="alerts-container">
          @for (msg of warningMessages; track msg) {
            <div class="alert-item warning">
              <span class="alert-icon">⚠️</span>
              <span>{{ msg }}</span>
            </div>
          }
        </div>
      }

      <!-- Tableau des gérants actifs -->
      @if (activeGerants.length === 0) {
        <div class="empty-state">
          <p>Aucun gérant actif désigné.</p>
          @if (!readonly) {
            <button type="button" class="btn btn-primary btn-sm" (click)="openAddModal()">Désigner le premier gérant</button>
          }
        </div>
      } @else {
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>Gérant</th>
                <th>Qualité</th>
                <th>CIN / Passeport</th>
                <th>Nomination</th>
                <th>Durée mandat</th>
                @if (!readonly) { <th>Actions</th> }
              </tr>
            </thead>
            <tbody>
              @for (gerant of activeGerants; track gerant.cinOuPasseport || $index) {
                <tr>
                  <td>
                    <strong>{{ gerant.prenom }} {{ gerant.nom }}</strong>
                    <small class="text-muted d-block">{{ gerant.adresse }}</small>
                  </td>
                  <td>
                    @if (gerant.estAssocie) {
                      <span class="badge-role associe">Gérant associé</span>
                    } @else {
                      <span class="badge-role tiers">Gérant non-associé (tiers)</span>
                    }
                  </td>
                  <td><code>{{ gerant.cinOuPasseport }}</code></td>
                  <td>
                    <span class="nomination-badge" [class.statutaire]="gerant.modeNomination === 'STATUTAIRE'">
                      {{ gerant.modeNomination === 'STATUTAIRE' ? 'Dans les statuts' : 'Acte séparé' }}
                    </span>
                  </td>
                  <td>{{ gerant.dureeMandat || 'Indéterminée' }}</td>
                  @if (!readonly) {
                    <td class="actions-cell">
                      <button type="button" class="icon-btn edit-btn" (click)="openEditModal(gerant)" title="Modifier">
                        ✏️
                      </button>
                      <button type="button" class="icon-btn delete-btn" (click)="confirmDeleteGerant(gerant)" title="Supprimer">
                        🗑️
                      </button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Section des gérants archivés / inactifs (RG12 Traçabilité) -->
      @if (inactiveGerants.length > 0) {
        <div class="inactive-section">
          <details>
            <summary>Gérants archivés / inactifs ({{ inactiveGerants.length }})</summary>
            <table class="data-table inactive-table">
              <thead>
                <tr>
                  <th>Nom & Prénom</th>
                  <th>CIN</th>
                  <th>Qualité</th>
                  @if (!readonly) { <th>Action</th> }
                </tr>
              </thead>
              <tbody>
                @for (gerant of inactiveGerants; track gerant.id || $index) {
                  <tr>
                    <td>{{ gerant.prenom }} {{ gerant.nom }}</td>
                    <td><code>{{ gerant.cinOuPasseport }}</code></td>
                    <td>{{ gerant.estAssocie ? 'Gérant associé' : 'Gérant tiers' }}</td>
                    @if (!readonly) {
                      <td>
                        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="reactivateGerant(gerant)">
                          Réactiver
                        </button>
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </details>
        </div>
      }

      <!-- Modal Ajout / Édition Gérant -->
      @if (isModalOpen) {
        <div class="modal-backdrop" (click)="closeModal()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h5>{{ isEditing ? 'Modifier le gérant' : 'Désigner un gérant' }}</h5>
              <button type="button" class="close-btn" (click)="closeModal()">×</button>
            </div>
            <div class="modal-body">
              <!-- Choix Gérant Associé vs Gérant Tiers -->
              <div class="form-group role-selector">
                <label class="section-sublabel">Qualité du gérant <b>*</b></label>
                <div class="radio-toggle-group">
                  <label class="radio-card" [class.selected]="formModel.estAssocie">
                    <input type="radio" [value]="true" [(ngModel)]="formModel.estAssocie" (change)="onRoleTypeChanged()" [disabled]="activeAssocies.length === 0" />
                    <strong>Associé de la société</strong>
                    <small>Sélectionnez un associé fondateur déjà déclaré</small>
                    @if (activeAssocies.length === 0) {
                      <span class="warning-chip">Aucun associé déclaré</span>
                    }
                  </label>
                  <label class="radio-card" [class.selected]="!formModel.estAssocie">
                    <input type="radio" [value]="false" [(ngModel)]="formModel.estAssocie" (change)="onRoleTypeChanged()" />
                    <strong>Tiers non-associé</strong>
                    <small>Personne extérieure à l'actionnariat</small>
                  </label>
                </div>
              </div>

              <!-- Si Gérant Associé : Sélection de l'associé -->
              @if (formModel.estAssocie) {
                <div class="form-group">
                  <label>Sélectionner l'associé <b>*</b></label>
                  <select class="form-control" [(ngModel)]="selectedAssocieKey" (change)="onAssocieSelected()">
                    <option value="">-- Choisir un associé --</option>
                    @for (associe of activeAssocies; track associe.cinOuPasseport || associe.id) {
                      <option [value]="getAssocieKey(associe)">
                        {{ associe.prenom }} {{ associe.nom }} (CIN : {{ associe.cinOuPasseport }})
                      </option>
                    }
                  </select>
                </div>
              }

              <!-- Informations d'identité -->
              <div class="form-row">
                <div class="form-group col">
                  <label>Prénom <b>*</b></label>
                  <input type="text" class="form-control" [(ngModel)]="formModel.prenom" [disabled]="formModel.estAssocie" placeholder="Ex. Youssef" />
                </div>
                <div class="form-group col">
                  <label>Nom <b>*</b></label>
                  <input type="text" class="form-control" [(ngModel)]="formModel.nom" [disabled]="formModel.estAssocie" placeholder="Ex. Bennani" />
                </div>
              </div>

              <div class="form-row">
                <div class="form-group col">
                  <label>CIN ou Passeport <b>*</b></label>
                  <input type="text" class="form-control" [(ngModel)]="formModel.cinOuPasseport" [disabled]="formModel.estAssocie" placeholder="Ex. AB123456" />
                </div>
                <div class="form-group col">
                  <label>Nationalité <b>*</b></label>
                  <input type="text" class="form-control" [(ngModel)]="formModel.nationalite" [disabled]="formModel.estAssocie" placeholder="Ex. Marocaine" />
                </div>
              </div>

              <div class="form-group">
                <label>Adresse du domicile <b>*</b></label>
                <input type="text" class="form-control" [(ngModel)]="formModel.adresse" [disabled]="formModel.estAssocie" placeholder="Ex. 12 rue de la Liberté, Casablanca" />
              </div>

              <!-- Paramètres de gérance -->
              <div class="form-row">
                <div class="form-group col">
                  <label>Mode de nomination <b>*</b></label>
                  <select class="form-control" [(ngModel)]="formModel.modeNomination">
                    <option value="STATUTAIRE">Nommé dans les statuts (statutaire)</option>
                    <option value="ACTE_SEPARE">Par acte séparé ultérieur</option>
                  </select>
                </div>
                <div class="form-group col">
                  <label>Durée du mandat</label>
                  <input type="text" class="form-control" [(ngModel)]="formModel.dureeMandat" maxlength="50" placeholder="Ex. Indéterminée, ou 3 ans" />
                </div>
              </div>

              <div class="form-group">
                <label>Étendue des pouvoirs</label>
                <textarea class="form-control" rows="2" [(ngModel)]="formModel.pouvoirs" maxlength="500" placeholder="Ex. Tous pouvoirs pour agir au nom de la société dans la limite de l'objet social."></textarea>
              </div>

              @if (modalError) {
                <div class="modal-error-alert">{{ modalError }}</div>
              }
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="closeModal()">Annuler</button>
              <button type="button" class="btn btn-primary" (click)="saveGerantModal()">
                {{ isEditing ? 'Enregistrer les modifications' : 'Ajouter le gérant' }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .gerants-container {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .manager-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 1rem;
    }
    .manager-header h4 {
      margin: 0 0 0.25rem 0;
      color: #1F3864;
      font-size: 1.15rem;
      font-weight: 700;
    }
    .manager-subtitle {
      margin: 0;
      font-size: 0.875rem;
      color: #64748b;
    }
    .stats-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .stat-pill {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.875rem;
      border-radius: 8px;
      font-size: 0.875rem;
      border: 1px solid #cbd5e1;
      background: #f8fafc;
    }
    .stat-pill.valid {
      background: #ecfdf5;
      border-color: #a7f3d0;
      color: #065f46;
    }
    .stat-pill.invalid {
      background: #fef2f2;
      border-color: #fecaca;
      color: #991b1b;
    }
    .stat-pill.neutral {
      background: #f1f5f9;
      border-color: #cbd5e1;
      color: #334155;
    }
    .stat-label {
      color: #64748b;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 600;
    }
    .alerts-container {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .alert-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 0.875rem;
      border-radius: 6px;
      font-size: 0.875rem;
    }
    .alert-item.warning {
      background: #fffbeb;
      border: 1px solid #fde68a;
      color: #92400e;
    }
    .empty-state {
      text-align: center;
      padding: 2.5rem 1rem;
      background: #f8fafc;
      border: 2px dashed #cbd5e1;
      border-radius: 8px;
      color: #64748b;
    }
    .empty-state p {
      margin: 0 0 1rem 0;
    }
    .table-responsive {
      overflow-x: auto;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
    }
    .data-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
      text-align: left;
    }
    .data-table th {
      background: #f8fafc;
      color: #475569;
      font-weight: 600;
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #e2e8f0;
    }
    .data-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      vertical-align: middle;
    }
    .data-table tr:last-child td {
      border-bottom: none;
    }
    .badge-role {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .badge-role.associe {
      background: #e0e7ff;
      color: #3730a3;
    }
    .badge-role.tiers {
      background: #f3e8ff;
      color: #6b21a8;
    }
    .nomination-badge {
      display: inline-block;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      background: #f1f5f9;
      color: #475569;
    }
    .nomination-badge.statutaire {
      background: #dbeafe;
      color: #1e40af;
    }
    .actions-cell {
      display: flex;
      gap: 0.5rem;
    }
    .icon-btn {
      background: none;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      cursor: pointer;
      padding: 0.25rem 0.5rem;
      font-size: 0.875rem;
      transition: background 0.15s;
    }
    .icon-btn:hover {
      background: #f1f5f9;
    }
    .icon-btn.delete-btn:hover {
      background: #fee2e2;
      border-color: #fca5a5;
    }
    .inactive-section {
      margin-top: 0.5rem;
    }
    .inactive-section details summary {
      cursor: pointer;
      color: #64748b;
      font-size: 0.85rem;
      user-select: none;
    }
    .inactive-table {
      margin-top: 0.5rem;
      opacity: 0.7;
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.55);
      backdrop-filter: blur(2px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1050;
      padding: 1rem;
    }
    .modal-dialog {
      background: #ffffff;
      border-radius: 12px;
      width: 100%;
      max-width: 580px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
      overflow: hidden;
      display: flex;
      flex-direction: column;
      max-height: 90vh;
    }
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      background: #f8fafc;
    }
    .modal-header h5 {
      margin: 0;
      font-size: 1.1rem;
      color: #1F3864;
      font-weight: 700;
    }
    .close-btn {
      background: none;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
      color: #64748b;
      line-height: 1;
    }
    .modal-body {
      padding: 1.25rem;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1rem 1.25rem;
      border-top: 1px solid #e2e8f0;
      background: #f8fafc;
    }
    .role-selector {
      margin-bottom: 0.25rem;
    }
    .section-sublabel {
      display: block;
      margin-bottom: 0.5rem;
      font-size: 0.85rem;
      font-weight: 600;
      color: #334155;
    }
    .radio-toggle-group {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.75rem;
    }
    .radio-card {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
      padding: 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      cursor: pointer;
      background: #f8fafc;
      transition: all 0.15s;
    }
    .radio-card input {
      margin-bottom: 0.25rem;
    }
    .radio-card.selected {
      border-color: #2563eb;
      background: #eff6ff;
    }
    .radio-card small {
      color: #64748b;
      font-size: 0.75rem;
    }
    .warning-chip {
      display: inline-block;
      margin-top: 0.25rem;
      color: #d97706;
      font-size: 0.7rem;
      font-weight: 600;
    }
    .form-row {
      display: flex;
      gap: 0.75rem;
    }
    .form-group.col {
      flex: 1;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .form-group label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #334155;
    }
    .form-group label b {
      color: #dc2626;
    }
    .form-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.875rem;
      outline: none;
      transition: border-color 0.15s;
    }
    .form-control:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
    }
    .form-control:disabled {
      background: #f1f5f9;
      color: #64748b;
      cursor: not-allowed;
    }
    .modal-error-alert {
      padding: 0.625rem 0.875rem;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 6px;
      color: #991b1b;
      font-size: 0.85rem;
    }
    .btn {
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      border: 1px solid transparent;
      transition: all 0.15s;
    }
    .btn-sm {
      padding: 0.35rem 0.65rem;
      font-size: 0.8rem;
    }
    .btn-primary {
      background: #1F3864;
      color: #ffffff;
    }
    .btn-primary:hover {
      background: #152644;
    }
    .btn-secondary {
      background: #f1f5f9;
      border-color: #cbd5e1;
      color: #334155;
    }
    .btn-secondary:hover {
      background: #e2e8f0;
    }
    .btn-outline-primary {
      background: transparent;
      border-color: #1F3864;
      color: #1F3864;
    }
    .btn-outline-primary:hover {
      background: #1F3864;
      color: #ffffff;
    }
    .text-muted {
      color: #64748b;
    }
    .d-block {
      display: block;
    }
  `]
})
export class GerantsManagerComponent {
  @Input() gerants: Gerant[] | undefined = [];
  @Input() activeAssocies: Associe[] = [];
  @Input() readonly: boolean = false;
  @Output() gerantsChange = new EventEmitter<Gerant[]>();

  isModalOpen = false;
  isEditing = false;
  editingIndex = -1;
  modalError = '';
  selectedAssocieKey = '';

  formModel: Gerant = this.emptyGerantForm();

  get activeGerants(): Gerant[] {
    return (this.gerants || []).filter(g => g.actif !== false);
  }

  get inactiveGerants(): Gerant[] {
    return (this.gerants || []).filter(g => g.actif === false);
  }

  get associesGerantsCount(): number {
    return this.activeGerants.filter(g => g.estAssocie).length;
  }

  get tiersGerantsCount(): number {
    return this.activeGerants.filter(g => !g.estAssocie).length;
  }

  get warningMessages(): string[] {
    const warnings: string[] = [];
    if (this.activeGerants.length === 0) {
      warnings.push('Au moins un gérant actif doit être désigné.');
    }
    // Vérifier si un gérant associé n'a plus d'associé correspondant
    for (const g of this.activeGerants) {
      if (g.estAssocie) {
        const found = this.activeAssocies.some(a => 
          (g.associeId && a.id && g.associeId === a.id) ||
          (g.cinOuPasseport && a.cinOuPasseport && g.cinOuPasseport.trim().toUpperCase() === a.cinOuPasseport.trim().toUpperCase())
        );
        if (!found) {
          warnings.push(`Le gérant associé ${g.prenom} ${g.nom} (${g.cinOuPasseport}) ne correspond à aucun associé actif.`);
        }
      }
    }
    return warnings;
  }

  openAddModal(): void {
    this.isEditing = false;
    this.editingIndex = -1;
    this.modalError = '';
    this.formModel = this.emptyGerantForm();

    // Par défaut, si des associés existent, pré-cocher associé et pré-sélectionner le premier
    if (this.activeAssocies.length > 0) {
      this.formModel.estAssocie = true;
      const first = this.activeAssocies[0];
      this.selectedAssocieKey = this.getAssocieKey(first);
      this.applyAssocieData(first);
    } else {
      this.formModel.estAssocie = false;
      this.selectedAssocieKey = '';
    }

    this.isModalOpen = true;
  }

  openEditModal(gerant: Gerant): void {
    this.isEditing = true;
    this.editingIndex = (this.gerants || []).indexOf(gerant);
    this.modalError = '';
    this.formModel = { ...gerant };

    if (this.formModel.estAssocie) {
      const match = this.activeAssocies.find(a => 
        (this.formModel.associeId && a.id && this.formModel.associeId === a.id) ||
        (this.formModel.cinOuPasseport && a.cinOuPasseport && this.formModel.cinOuPasseport.trim().toUpperCase() === a.cinOuPasseport.trim().toUpperCase())
      );
      if (match) {
        this.selectedAssocieKey = this.getAssocieKey(match);
      } else {
        this.selectedAssocieKey = '';
      }
    } else {
      this.selectedAssocieKey = '';
    }

    this.isModalOpen = true;
  }

  closeModal(): void {
    this.isModalOpen = false;
    this.modalError = '';
  }

  onRoleTypeChanged(): void {
    if (this.formModel.estAssocie) {
      if (this.activeAssocies.length > 0) {
        const first = this.activeAssocies[0];
        this.selectedAssocieKey = this.getAssocieKey(first);
        this.applyAssocieData(first);
      }
    } else {
      this.formModel.associeId = null;
      this.selectedAssocieKey = '';
    }
  }

  getAssocieKey(associe: Associe): string {
    return associe.id || associe.cinOuPasseport;
  }

  onAssocieSelected(): void {
    if (!this.selectedAssocieKey) return;
    const associe = this.activeAssocies.find(a => this.getAssocieKey(a) === this.selectedAssocieKey);
    if (associe) {
      this.applyAssocieData(associe);
    }
  }

  private applyAssocieData(associe: Associe): void {
    this.formModel.nom = associe.nom;
    this.formModel.prenom = associe.prenom;
    this.formModel.cinOuPasseport = associe.cinOuPasseport;
    this.formModel.nationalite = associe.nationalite;
    this.formModel.adresse = associe.adresse;
    this.formModel.associeId = associe.id ?? null;
  }

  saveGerantModal(): void {
    this.modalError = '';

    if (!this.formModel.nom.trim()) {
      this.modalError = 'Le nom du gérant est obligatoire.';
      return;
    }
    if (!this.formModel.prenom.trim()) {
      this.modalError = 'Le prénom du gérant est obligatoire.';
      return;
    }
    if (!this.formModel.cinOuPasseport.trim()) {
      this.modalError = 'Le CIN ou passeport du gérant est obligatoire.';
      return;
    }
    if (!this.formModel.nationalite.trim()) {
      this.modalError = 'La nationalité du gérant est obligatoire.';
      return;
    }
    if (!this.formModel.adresse.trim()) {
      this.modalError = 'L’adresse du gérant est obligatoire.';
      return;
    }

    const cinNormalise = this.formModel.cinOuPasseport.trim().toUpperCase();

    // Vérifier les doublons de gérants actifs (même CIN)
    const duplicate = this.activeGerants.find((g, idx) => {
      const realIdx = (this.gerants || []).indexOf(g);
      return realIdx !== this.editingIndex && g.cinOuPasseport.trim().toUpperCase() === cinNormalise;
    });

    if (duplicate) {
      this.modalError = `Un gérant actif avec le CIN/Passeport ${cinNormalise} est déjà désigné.`;
      return;
    }

    const updatedGerant: Gerant = {
      ...this.formModel,
      nom: this.formModel.nom.trim(),
      prenom: this.formModel.prenom.trim(),
      cinOuPasseport: cinNormalise,
      nationalite: this.formModel.nationalite.trim(),
      adresse: this.formModel.adresse.trim(),
      dureeMandat: this.formModel.dureeMandat?.trim() || 'Indéterminée',
      pouvoirs: this.formModel.pouvoirs?.trim() || 'Tous les pouvoirs conférés par la loi et les statuts.',
      actif: true
    };

    const currentList = [...(this.gerants || [])];

    if (this.isEditing && this.editingIndex >= 0) {
      currentList[this.editingIndex] = updatedGerant;
    } else {
      currentList.push(updatedGerant);
    }

    this.gerants = currentList;
    this.gerantsChange.emit(this.gerants);
    this.closeModal();
  }

  confirmDeleteGerant(gerant: Gerant): void {
    if (!this.gerants) return;
    const index = this.gerants.indexOf(gerant);
    if (index < 0) return;

    const currentList = [...this.gerants];
    if (gerant.id) {
      // Soft-delete RG12
      currentList[index] = { ...gerant, actif: false };
    } else {
      // Non persisté en DB, suppression de l'array
      currentList.splice(index, 1);
    }

    this.gerants = currentList;
    this.gerantsChange.emit(this.gerants);
  }

  reactivateGerant(gerant: Gerant): void {
    if (!this.gerants) return;
    const index = this.gerants.indexOf(gerant);
    if (index >= 0) {
      const currentList = [...this.gerants];
      currentList[index] = { ...gerant, actif: true };
      this.gerants = currentList;
      this.gerantsChange.emit(this.gerants);
    }
  }

  private emptyGerantForm(): Gerant {
    return {
      nom: '',
      prenom: '',
      cinOuPasseport: '',
      nationalite: 'Marocaine',
      adresse: '',
      estAssocie: true,
      associeId: null,
      modeNomination: 'STATUTAIRE',
      dureeMandat: 'Indéterminée',
      pouvoirs: 'Tous les pouvoirs pour agir au nom de la société dans la limite de l’objet social.',
      actif: true
    };
  }
}
