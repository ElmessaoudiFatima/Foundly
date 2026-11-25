import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Associe, LegalFormCode } from '../../models/entrepreneur.models';
import {
  isCapitalDivisibleByNominal,
  computeTotalParts,
  computeApport,
  computePourcentageDetention,
  computeCapitalRestant,
  summarizeCapitalDistribution,
  CapitalDistributionSummary
} from '../../utils/capital-math.utils';

@Component({
  selector: 'app-associes-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="associes-container">
      <!-- 1. En-tête de section -->
      <div class="manager-header">
        <div>
          <h4>Actionnariat & Répartition du Capital Social</h4>
          <p class="manager-subtitle">
            @if (legalFormCode === 'SARL_AU') {
              SARL à associé unique : <strong>1 seul associé actif</strong> détenant <strong>100 % des parts</strong>.
            } @else {
              SARL pluripersonnelle : entre <strong>2 et 50 associés fondateurs</strong>.
            }
          </p>
        </div>
        <div class="header-actions">
          @if (!readonly) {
            <button
              type="button"
              class="btn btn-outline-primary btn-sm"
              (click)="openAddModal()"
              [disabled]="isMaxReached"
            >
              + Ajouter un associé
            </button>
          }
        </div>
      </div>

      <!-- 2. Cadre de Répartition du Capital Social -->
      <div class="capital-dashboard-card">
        <div class="capital-metric-grid">
          <div class="metric-item">
            <span class="metric-label">Capital social total</span>
            <strong class="metric-value primary">{{ formatMoney(capital) }}</strong>
            <small class="metric-hint">Déclaré à l'étape précédente</small>
          </div>

          <div class="metric-item">
            <div class="metric-label-row">
              <span class="metric-label">Valeur nominale / part</span>
              @if (!readonly) {
                <button
                  type="button"
                  class="edit-vn-toggle"
                  (click)="isEditingVn = !isEditingVn"
                  title="Modifier la valeur nominale"
                >
                  {{ isEditingVn ? 'Fermer' : 'Modifier' }}
                </button>
              }
            </div>

            @if (isEditingVn && !readonly) {
              <div class="vn-edit-container">
                <input
                  type="number"
                  min="0.01"
                  step="any"
                  [(ngModel)]="valeurNominaleLocal"
                  (ngModelChange)="onValeurNominaleInputChange()"
                  class="vn-input"
                  placeholder="Ex: 100"
                />
                <span class="vn-unit">MAD</span>
              </div>
              <div class="vn-quick-chips">
                <button type="button" (click)="setValeurNominaleQuick(10)">10</button>
                <button type="button" (click)="setValeurNominaleQuick(50)">50</button>
                <button type="button" (click)="setValeurNominaleQuick(100)">100</button>
                <button type="button" (click)="setValeurNominaleQuick(500)">500</button>
                <button type="button" (click)="setValeurNominaleQuick(1000)">1 000</button>
              </div>
            } @else {
              <strong class="metric-value">{{ formatMoney(valeurNominaleEff) }}</strong>
              <small class="metric-hint">100 MAD par défaut</small>
            }
          </div>

          <div class="metric-item">
            <span class="metric-label">Nombre total de parts</span>
            <strong class="metric-value">{{ totalPartsAttendu }}</strong>
            <small class="metric-hint">
              {{ formatMoney(capital) }} / {{ formatMoney(valeurNominaleEff) }}
            </small>
          </div>

          <div class="metric-item" [class.highlight-warning]="partsRestantes > 0" [class.highlight-error]="partsRestantes < 0" [class.highlight-success]="summary.isComplete">
            <span class="metric-label">Reste à attribuer</span>
            <strong class="metric-value">
              @if (partsRestantes === 0) {
                0 part (0 MAD)
              } @else if (partsRestantes > 0) {
                {{ partsRestantes }} part{{ partsRestantes > 1 ? 's' : '' }} ({{ formatMoney(capitalRestant) }})
              } @else {
                {{ -partsRestantes }} part{{ -partsRestantes > 1 ? 's' : '' }} en trop
              }
            </strong>
            <small class="metric-hint">
              {{ summary.isComplete ? 'Capital intégralement réparti' : partsRestantes > 0 ? 'Répartition incomplète' : 'Dépassement du capital' }}
            </small>
          </div>
        </div>

        <!-- 3. Indicateur Visuel de Répartition (Progress bar) -->
        <div class="allocation-progress-wrapper">
          <div class="progress-labels">
            <span>Progression de la répartition : <strong>{{ summary.pourcentageTotalAttribue }}%</strong></span>
            <span class="progress-counts">
              {{ summary.sumPartsAttribuees }} / {{ totalPartsAttendu }} parts attribuées
              ({{ formatMoney(summary.sumApportsAttribues) }} / {{ formatMoney(capital) }})
            </span>
          </div>

          <div class="progress-bar-track" [class.overallocated]="summary.isExceeded" [class.completed]="summary.isComplete">
            <div
              class="progress-bar-fill"
              [style.width.%]="summary.pourcentageTotalAttribue"
              [class.complete]="summary.isComplete"
              [class.incomplete]="partsRestantes > 0"
              [class.exceeded]="summary.isExceeded"
            ></div>
          </div>
        </div>

        <!-- Raccourcis d'attribution pour SARL-AU et SARL -->
        @if (!readonly && activeAssocies.length > 0) {
          <div class="quick-allocation-bar">
            @if (legalFormCode === 'SARL_AU' && partsRestantes !== 0) {
              <button
                type="button"
                class="btn btn-sm btn-action-quick"
                (click)="autoAttribuerSarlAu()"
              >
                ⚡ Attribuer 100 % des parts à l’associé unique ({{ totalPartsAttendu }} parts)
              </button>
            }
            @if (legalFormCode === 'SARL' && partsRestantes > 0 && activeAssocies.length >= 2) {
              <button
                type="button"
                class="btn btn-sm btn-action-quick"
                (click)="repartirEquitablement()"
              >
                ⚖️ Répartir les {{ partsRestantes }} parts restantes équitablement
              </button>
            }
          </div>
        }
      </div>

      <!-- 4. Alertes d'incohérence en direct -->
      @if (summary.errors.length > 0 || summary.warnings.length > 0) {
        <div class="alerts-container">
          @for (err of summary.errors; track err) {
            <div class="alert-item error">
              <span class="alert-icon">🚫</span>
              <span>{{ err }}</span>
            </div>
          }
          @for (warn of summary.warnings; track warn) {
            <div class="alert-item warning">
              <span class="alert-icon">⚠️</span>
              <span>{{ warn }}</span>
            </div>
          }
        </div>
      }

      <!-- 5. Tableau dynamique des associés et détention -->
      @if (activeAssocies.length === 0) {
        <div class="empty-state">
          <p>Aucun associé actif déclaré pour le moment.</p>
          @if (!readonly) {
            <button type="button" class="btn btn-primary btn-sm" (click)="openAddModal()">
              Ajouter le premier associé
            </button>
          }
        </div>
      } @else {
        <div class="table-card">
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Associé fondateur</th>
                  <th>Pièce d’identité</th>
                  <th>Nationalité</th>
                  <th class="text-right">Parts</th>
                  <th class="text-right">Apport en numéraire</th>
                  <th class="text-center">Détention</th>
                  <th class="text-center">Répartition</th>
                  @if (!readonly) {
                    <th class="text-center">Actions</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (associe of activeAssocies; track associe.cinOuPasseport || associe.id || $index) {
                  <tr>
                    <td>
                      <strong class="partner-name">{{ associe.prenom }} {{ associe.nom }}</strong>
                      <small class="partner-address">{{ associe.adresse }}</small>
                    </td>
                    <td><code>{{ associe.cinOuPasseport }}</code></td>
                    <td>{{ associe.nationalite }}</td>
                    <td class="text-right">
                      @if (!readonly && !isModalOpen) {
                        <div class="parts-stepper">
                          <button
                            type="button"
                            class="stepper-btn"
                            (click)="adjustParts(associe, -1)"
                            [disabled]="(associe.nombreParts || 1) <= 1"
                            title="Retirer 1 part"
                          >−</button>
                          <strong class="parts-val">{{ associe.nombreParts }}</strong>
                          <button
                            type="button"
                            class="stepper-btn"
                            (click)="adjustParts(associe, 1)"
                            [disabled]="partsRestantes <= 0"
                            title="Ajouter 1 part"
                          >+</button>
                        </div>
                      } @else {
                        <strong>{{ associe.nombreParts }}</strong>
                      }
                    </td>
                    <td class="text-right">
                      <strong>{{ formatMoney(associe.montantApport) }}</strong>
                    </td>
                    <td class="text-center">
                      <span class="pct-badge" [class.full]="associe.pourcentageDetention === 100 || getPct(associe.nombreParts) === '100.00'">
                        {{ getPct(associe.nombreParts) }} %
                      </span>
                    </td>
                    <td class="text-center visual-col">
                      <div class="mini-bar-track" [title]="getPct(associe.nombreParts) + '%'">
                        <div class="mini-bar-fill" [style.width.%]="getPct(associe.nombreParts)"></div>
                      </div>
                    </td>
                    @if (!readonly) {
                      <td class="text-center">
                        <div class="table-actions">
                          <button type="button" class="action-btn edit" title="Modifier" (click)="openEditModal(associe)">
                            ✏️
                          </button>
                          <button type="button" class="action-btn delete" title="Désactiver" (click)="deactivateAssocie(associe)">
                            🗑️
                          </button>
                        </div>
                      </td>
                    }
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr class="table-total-row">
                  <td colspan="3"><strong>Total des parts et apports attribués</strong></td>
                  <td class="text-right"><strong>{{ summary.sumPartsAttribuees }} / {{ totalPartsAttendu }}</strong></td>
                  <td class="text-right"><strong>{{ formatMoney(summary.sumApportsAttribues) }} / {{ formatMoney(capital) }}</strong></td>
                  <td class="text-center">
                    <span class="pct-badge" [class.full]="summary.isComplete">
                      {{ summary.pourcentageTotalAttribue }} %
                    </span>
                  </td>
                  <td colspan="2"></td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      }

      <!-- 6. Associés désactivés (RG12 - Traçabilité sans suppression physique) -->
      @if (inactiveAssocies.length > 0) {
        <div class="inactive-section">
          <details>
            <summary class="inactive-summary">
              Associés désactivés / retirés ({{ inactiveAssocies.length }})
              <small class="text-muted">— historique préservé conformément à la règle RG12</small>
            </summary>
            <div class="inactive-list">
              @for (associe of inactiveAssocies; track associe.cinOuPasseport || associe.id || $index) {
                <div class="inactive-card">
                  <div class="inactive-info">
                    <span class="inactive-name">{{ associe.prenom }} {{ associe.nom }}</span>
                    <code>{{ associe.cinOuPasseport }}</code>
                    <small>Dernière détention : {{ associe.nombreParts }} part(s)</small>
                  </div>
                  @if (!readonly) {
                    <button type="button" class="btn btn-sm btn-outline-secondary" (click)="reactivateAssocie(associe)">
                      Réactiver cet associé
                    </button>
                  }
                </div>
              }
            </div>
          </details>
        </div>
      }

      <!-- 7. Modale d'ajout / modification d'un associé -->
      @if (isModalOpen) {
        <div class="modal-backdrop" (click)="closeModal()">
          <div class="modal-card" (click)="$event.stopPropagation()">
            <header class="modal-header">
              <h3>{{ editingIndex !== null ? 'Modifier l’associé' : 'Ajouter un associé' }}</h3>
              <button type="button" class="close-btn" (click)="closeModal()">✕</button>
            </header>
            <div class="modal-body">
              <div class="form-row">
                <label class="form-group">
                  <span>Prénom <b>*</b></span>
                  <input type="text" [(ngModel)]="currentForm.prenom" placeholder="Prénom" maxlength="100" />
                </label>
                <label class="form-group">
                  <span>Nom <b>*</b></span>
                  <input type="text" [(ngModel)]="currentForm.nom" placeholder="Nom de famille" maxlength="100" />
                </label>
              </div>

              <div class="form-row">
                <label class="form-group">
                  <span>CIN ou Passeport <b>*</b></span>
                  <input type="text" [(ngModel)]="currentForm.cinOuPasseport" placeholder="Ex: AB123456" maxlength="30" />
                </label>
                <label class="form-group">
                  <span>Nationalité <b>*</b></span>
                  <input type="text" [(ngModel)]="currentForm.nationalite" placeholder="Ex: Marocaine" maxlength="50" />
                </label>
              </div>

              <label class="form-group full">
                <span>Adresse de domicile <b>*</b></span>
                <input type="text" [(ngModel)]="currentForm.adresse" placeholder="Adresse complète" maxlength="255" />
              </label>

              <div class="form-row">
                <label class="form-group">
                  <span>Nombre de parts sociales <b>*</b></span>
                  <input
                    type="number"
                    min="1"
                    [(ngModel)]="currentForm.nombreParts"
                    (ngModelChange)="onPartsChange()"
                  />
                  <small class="hint">Minimum 1 part requise</small>
                </label>
                <label class="form-group">
                  <span>Apport en numéraire (MAD)</span>
                  <input
                    type="text"
                    [value]="formatMoney(currentForm.montantApport)"
                    readonly
                    class="readonly-input"
                  />
                  <small class="hint">Calculé automatiquement : {{ currentForm.nombreParts || 0 }} × {{ formatMoney(valeurNominaleEff) }}</small>
                </label>
              </div>

              @if (modalError) {
                <div class="form-error-msg">{{ modalError }}</div>
              }
            </div>
            <footer class="modal-footer">
              <button type="button" class="btn btn-outline" (click)="closeModal()">Annuler</button>
              <button type="button" class="btn btn-primary" (click)="saveAssocie()">
                {{ editingIndex !== null ? 'Valider les modifications' : 'Ajouter l’associé' }}
              </button>
            </footer>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .associes-container {
      display: grid;
      gap: 1.25rem;
      margin: 0.5rem 0;
    }
    .manager-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 1rem;
    }
    .manager-header h4 {
      margin: 0 0 0.25rem;
      font-size: 1.15rem;
      color: #1F3864;
      font-weight: 700;
    }
    .manager-subtitle {
      margin: 0;
      font-size: 0.85rem;
      color: #64748B;
    }

    /* Cadre de Répartition du Capital Social */
    .capital-dashboard-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      padding: 1.25rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      display: grid;
      gap: 1rem;
    }
    .capital-metric-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1rem;
    }
    .metric-item {
      padding: 0.875rem 1rem;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      background: #F8FAFC;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      transition: all 0.15s ease;
    }
    .metric-item.highlight-success {
      border-color: #A7F3D0;
      background: #ECFDF5;
    }
    .metric-item.highlight-warning {
      border-color: #FDE68A;
      background: #FFFBEB;
    }
    .metric-item.highlight-error {
      border-color: #FECACA;
      background: #FEF2F2;
    }
    .metric-label-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .metric-label {
      font-size: 0.72rem;
      color: #64748B;
      text-transform: uppercase;
      font-weight: 700;
      letter-spacing: 0.025em;
    }
    .metric-value {
      font-size: 1.1rem;
      color: #1E293B;
      font-weight: 700;
    }
    .metric-value.primary {
      color: #1F3864;
    }
    .metric-hint {
      font-size: 0.72rem;
      color: #94A3B8;
    }

    /* Édition Valeur Nominale */
    .edit-vn-toggle {
      background: none;
      border: none;
      color: #2563EB;
      font-size: 0.7rem;
      cursor: pointer;
      font-weight: 600;
      padding: 0;
      text-decoration: underline;
    }
    .vn-edit-container {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      margin: 0.25rem 0;
    }
    .vn-input {
      width: 85px;
      padding: 0.3rem 0.5rem;
      border: 1px solid #CBD5E1;
      border-radius: 4px;
      font-size: 0.85rem;
      font-weight: 700;
    }
    .vn-unit {
      font-size: 0.75rem;
      color: #64748B;
      font-weight: 600;
    }
    .vn-quick-chips {
      display: flex;
      gap: 0.25rem;
      flex-wrap: wrap;
    }
    .vn-quick-chips button {
      background: #FFFFFF;
      border: 1px solid #CBD5E1;
      border-radius: 3px;
      padding: 0.15rem 0.4rem;
      font-size: 0.65rem;
      cursor: pointer;
      font-weight: 600;
      color: #334155;
    }
    .vn-quick-chips button:hover {
      background: #E2E8F0;
    }

    /* Progress Bar */
    .allocation-progress-wrapper {
      display: grid;
      gap: 0.4rem;
      padding-top: 0.25rem;
    }
    .progress-labels {
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
      color: #334155;
    }
    .progress-counts {
      color: #64748B;
      font-size: 0.76rem;
    }
    .progress-bar-track {
      height: 10px;
      background: #E2E8F0;
      border-radius: 9999px;
      overflow: hidden;
      position: relative;
    }
    .progress-bar-fill {
      height: 100%;
      border-radius: 9999px;
      transition: width 0.3s ease;
    }
    .progress-bar-fill.incomplete {
      background: #3B82F6;
    }
    .progress-bar-fill.complete {
      background: #10B981;
    }
    .progress-bar-fill.exceeded {
      background: #EF4444;
    }

    /* Raccourcis */
    .quick-allocation-bar {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
      padding-top: 0.25rem;
    }
    .btn-action-quick {
      background: #F1F5F9;
      border: 1px dashed #3B82F6;
      color: #1E40AF;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-action-quick:hover {
      background: #EFF6FF;
      border-style: solid;
    }

    /* Alertes */
    .alerts-container {
      display: grid;
      gap: 0.5rem;
    }
    .alert-item {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.6rem 0.875rem;
      border-radius: 6px;
      font-size: 0.82rem;
    }
    .alert-item.warning {
      background: #FFFBEB;
      border: 1px solid #FDE68A;
      color: #92400E;
    }
    .alert-item.error {
      background: #FEF2F2;
      border: 1px solid #FECACA;
      color: #B91C1C;
    }

    /* Tableau */
    .table-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      overflow: hidden;
    }
    .table-responsive {
      overflow-x: auto;
    }
    .data-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;
      text-align: left;
    }
    .data-table th {
      background: #F8FAFC;
      padding: 0.75rem 1rem;
      color: #475569;
      font-weight: 700;
      border-bottom: 1px solid #E2E8F0;
      font-size: 0.76rem;
      text-transform: uppercase;
      letter-spacing: 0.025em;
    }
    .data-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #F1F5F9;
      vertical-align: middle;
      color: #1E293B;
    }
    .table-total-row {
      background: #F8FAFC;
      font-weight: 700;
      border-top: 2px solid #E2E8F0;
    }
    .table-total-row td {
      color: #1F3864;
    }
    .partner-name {
      display: block;
      color: #1E293B;
    }
    .partner-address {
      display: block;
      color: #64748B;
      font-size: 0.72rem;
    }
    .text-right { text-align: right; }
    .text-center { text-align: center; }

    /* Parts Stepper */
    .parts-stepper {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background: #F8FAFC;
      border: 1px solid #CBD5E1;
      border-radius: 6px;
      padding: 0.15rem 0.35rem;
    }
    .stepper-btn {
      background: #FFFFFF;
      border: 1px solid #CBD5E1;
      border-radius: 4px;
      width: 22px;
      height: 22px;
      font-size: 0.9rem;
      line-height: 1;
      display: grid;
      place-items: center;
      cursor: pointer;
      color: #1E293B;
    }
    .stepper-btn:hover:not(:disabled) {
      background: #E2E8F0;
    }
    .stepper-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .parts-val {
      min-width: 28px;
      text-align: center;
      font-size: 0.85rem;
    }

    /* Badges & Visual mini-bar */
    .pct-badge {
      display: inline-block;
      padding: 0.2rem 0.5rem;
      background: #EFF6FF;
      color: #1D4ED8;
      border-radius: 9999px;
      font-weight: 700;
      font-size: 0.74rem;
    }
    .pct-badge.full {
      background: #ECFDF5;
      color: #047857;
    }
    .visual-col {
      min-width: 80px;
    }
    .mini-bar-track {
      height: 6px;
      background: #E2E8F0;
      border-radius: 9999px;
      overflow: hidden;
      width: 100%;
    }
    .mini-bar-fill {
      height: 100%;
      background: #3B82F6;
      border-radius: 9999px;
    }

    .table-actions {
      display: inline-flex;
      gap: 0.4rem;
    }
    .action-btn {
      background: none;
      border: 1px solid #CBD5E1;
      border-radius: 5px;
      padding: 0.25rem 0.4rem;
      cursor: pointer;
      font-size: 0.75rem;
    }
    .action-btn:hover { background: #F1F5F9; }
    .action-btn.delete:hover { background: #FEF2F2; border-color: #FECACA; }

    /* Inactive Section */
    .inactive-section {
      padding: 0.75rem 1rem;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
    }
    .inactive-summary {
      font-size: 0.82rem;
      color: #64748B;
      cursor: pointer;
      font-weight: 600;
    }
    .inactive-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }
    .inactive-card {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.5rem 0.75rem;
      background: #FFFFFF;
      border: 1px solid #CBD5E1;
      border-radius: 6px;
    }
    .inactive-info {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: #64748B;
    }
    .inactive-name {
      text-decoration: line-through;
      font-weight: 600;
    }

    /* Modal */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 28, 53, 0.45);
      backdrop-filter: blur(2px);
      z-index: 500;
      display: grid;
      place-items: center;
      padding: 1rem;
    }
    .modal-card {
      width: min(100%, 540px);
      background: white;
      border-radius: 12px;
      box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
      overflow: hidden;
      display: grid;
    }
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #EDF2F7;
    }
    .modal-header h3 {
      margin: 0;
      font-size: 1.05rem;
      color: #1F3864;
      font-weight: 700;
    }
    .close-btn {
      background: none;
      border: none;
      font-size: 1.25rem;
      color: #94A3B8;
      cursor: pointer;
    }
    .modal-body {
      padding: 1.25rem;
      display: grid;
      gap: 1rem;
    }
    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    .form-group {
      display: grid;
      gap: 0.35rem;
    }
    .form-group.full { grid-column: 1 / -1; }
    .form-group span {
      font-size: 0.76rem;
      color: #475569;
      font-weight: 700;
    }
    .form-group b { color: #DC2626; }
    .form-group input {
      padding: 0.55rem 0.75rem;
      border: 1px solid #CBD5E1;
      border-radius: 6px;
      font-size: 0.85rem;
      outline: none;
    }
    .form-group input:focus {
      border-color: #2563EB;
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
    }
    .readonly-input {
      background: #F8FAFC;
      color: #475569;
    }
    .hint {
      font-size: 0.7rem;
      color: #94A3B8;
    }
    .form-error-msg {
      padding: 0.6rem 0.875rem;
      background: #FEF2F2;
      border: 1px solid #FECACA;
      color: #DC2626;
      border-radius: 6px;
      font-size: 0.8rem;
    }
    .modal-footer {
      padding: 1rem 1.25rem;
      border-top: 1px solid #EDF2F7;
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
    }

    .btn {
      padding: 0.55rem 1rem;
      border-radius: 6px;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s;
    }
    .btn-sm {
      padding: 0.35rem 0.65rem;
      font-size: 0.75rem;
    }
    .btn-primary {
      background: #1F3864;
      color: white;
      border: 1px solid #1F3864;
    }
    .btn-primary:hover:not(:disabled) {
      background: #16294A;
    }
    .btn-outline {
      background: white;
      border: 1px solid #CBD5E1;
      color: #475569;
    }
    .btn-outline-primary {
      background: white;
      border: 1px solid #1F3864;
      color: #1F3864;
    }
    .btn-outline-primary:hover:not(:disabled) {
      background: #F0F4F9;
    }
    .btn-outline-secondary {
      background: white;
      border: 1px solid #CBD5E1;
      color: #475569;
    }

    .empty-state {
      text-align: center;
      padding: 2rem;
      border: 1px dashed #CBD5E1;
      border-radius: 8px;
      background: #F8FAFC;
      color: #64748B;
      font-size: 0.85rem;
    }
    .empty-state p { margin: 0 0 0.75rem; }

    @media (max-width: 768px) {
      .capital-metric-grid { grid-template-columns: 1fr 1fr; }
      .form-row { grid-template-columns: 1fr; }
    }
    @media (max-width: 480px) {
      .capital-metric-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class AssociesManagerComponent {
  @Input() associes: Associe[] | undefined = [];
  @Input() capital: number = 0;
  @Input() valeurNominale: number | undefined = 100;
  @Input() legalFormCode: LegalFormCode = 'SARL';
  @Input() readonly: boolean = false;

  @Output() associesChange = new EventEmitter<Associe[]>();
  @Output() valeurNominaleChange = new EventEmitter<number | undefined>();

  isModalOpen = false;
  isEditingVn = false;
  valeurNominaleLocal: number = 100;
  editingIndex: number | null = null;
  modalError = '';

  currentForm: Associe = this.emptyAssocie();

  get valeurNominaleEff(): number {
    return Number(this.valeurNominale) > 0 ? Number(this.valeurNominale) : 100;
  }

  get activeAssocies(): Associe[] {
    return (this.associes || []).filter(a => a.actif !== false);
  }

  get inactiveAssocies(): Associe[] {
    return (this.associes || []).filter(a => a.actif === false);
  }

  get totalPartsAttendu(): number {
    return computeTotalParts(this.capital, this.valeurNominaleEff);
  }

  get summary(): CapitalDistributionSummary {
    return summarizeCapitalDistribution(
      this.legalFormCode,
      this.capital,
      this.valeurNominaleEff,
      this.activeAssocies
    );
  }

  get partsRestantes(): number {
    return this.summary.partsRestantes;
  }

  get capitalRestant(): number {
    return this.summary.capitalRestant;
  }

  get isMaxReached(): boolean {
    if (this.legalFormCode === 'SARL_AU') return this.activeAssocies.length >= 1;
    return this.activeAssocies.length >= 50;
  }

  formatMoney(amount?: number): string {
    return (Number(amount) || 0).toLocaleString('fr-FR') + ' MAD';
  }

  getPct(parts?: number): string {
    return computePourcentageDetention(parts, this.totalPartsAttendu);
  }

  onValeurNominaleInputChange(): void {
    const vn = Number(this.valeurNominaleLocal);
    if (!isNaN(vn) && vn > 0) {
      this.setValeurNominale(vn);
    }
  }

  setValeurNominaleQuick(val: number): void {
    this.valeurNominaleLocal = val;
    this.setValeurNominale(val);
    this.isEditingVn = false;
  }

  private setValeurNominale(vn: number): void {
    this.valeurNominale = vn;
    this.valeurNominaleChange.emit(vn);
    // Recalculer automatiquement les apports individuels de chaque associé
    this.recalculerApports();
  }

  private recalculerApports(): void {
    if (!this.associes || this.associes.length === 0) return;
    const vn = this.valeurNominaleEff;
    const totalParts = this.totalPartsAttendu;

    const updated = this.associes.map(a => {
      if (a.actif === false) return a;
      const parts = Number(a.nombreParts) || 1;
      const apport = computeApport(parts, vn);
      const pct = totalParts > 0 ? Number(computePourcentageDetention(parts, totalParts)) : 0;
      return {
        ...a,
        montantApport: apport,
        pourcentageDetention: pct
      };
    });

    this.associes = updated;
    this.associesChange.emit(this.associes);
  }

  autoAttribuerSarlAu(): void {
    if (this.readonly || this.activeAssocies.length !== 1) return;
    const singleAssocie = this.activeAssocies[0];
    const totalParts = this.totalPartsAttendu;
    if (totalParts <= 0) return;

    const idx = (this.associes || []).indexOf(singleAssocie);
    if (idx === -1) return;

    const apport = computeApport(totalParts, this.valeurNominaleEff);
    const updated = [...(this.associes || [])];
    updated[idx] = {
      ...singleAssocie,
      nombreParts: totalParts,
      montantApport: apport,
      pourcentageDetention: 100
    };

    this.associes = updated;
    this.associesChange.emit(this.associes);
  }

  repartirEquitablement(): void {
    if (this.readonly || this.activeAssocies.length < 2) return;
    const count = this.activeAssocies.length;
    const totalParts = this.totalPartsAttendu;
    if (totalParts < count) return;

    const baseParts = Math.floor(totalParts / count);
    const remainder = totalParts % count;
    const vn = this.valeurNominaleEff;

    let remainderGiven = 0;
    const updated = (this.associes || []).map(a => {
      if (a.actif === false) return a;
      const parts = baseParts + (remainderGiven < remainder ? 1 : 0);
      remainderGiven++;
      const apport = computeApport(parts, vn);
      const pct = Number(computePourcentageDetention(parts, totalParts));
      return {
        ...a,
        nombreParts: parts,
        montantApport: apport,
        pourcentageDetention: pct
      };
    });

    this.associes = updated;
    this.associesChange.emit(this.associes);
  }

  adjustParts(associe: Associe, delta: number): void {
    if (this.readonly) return;
    const idx = (this.associes || []).indexOf(associe);
    if (idx === -1) return;

    const currentParts = Number(associe.nombreParts) || 1;
    const newParts = currentParts + delta;
    if (newParts < 1) return;

    const vn = this.valeurNominaleEff;
    const apport = computeApport(newParts, vn);
    const totalParts = this.totalPartsAttendu;
    const pct = totalParts > 0 ? Number(computePourcentageDetention(newParts, totalParts)) : 0;

    const updated = [...(this.associes || [])];
    updated[idx] = {
      ...associe,
      nombreParts: newParts,
      montantApport: apport,
      pourcentageDetention: pct
    };

    this.associes = updated;
    this.associesChange.emit(this.associes);
  }

  openAddModal(): void {
    if (this.readonly || this.isMaxReached) return;
    this.editingIndex = null;
    this.modalError = '';
    const defParts = this.partsRestantes > 0 ? this.partsRestantes : 1;
    const vn = this.valeurNominaleEff;
    this.currentForm = {
      ...this.emptyAssocie(),
      nombreParts: defParts,
      montantApport: computeApport(defParts, vn)
    };
    this.isModalOpen = true;
  }

  openEditModal(associe: Associe): void {
    if (this.readonly) return;
    const idx = (this.associes || []).indexOf(associe);
    if (idx === -1) return;
    this.editingIndex = idx;
    this.modalError = '';
    this.currentForm = { ...associe };
    this.isModalOpen = true;
  }

  closeModal(): void {
    this.isModalOpen = false;
    this.editingIndex = null;
    this.modalError = '';
  }

  onPartsChange(): void {
    const parts = Number(this.currentForm.nombreParts) || 0;
    this.currentForm.montantApport = computeApport(parts, this.valeurNominaleEff);
  }

  saveAssocie(): void {
    this.modalError = '';
    const form = this.currentForm;
    if (!form.nom?.trim() || !form.prenom?.trim()) {
      this.modalError = 'Le nom et le prénom sont obligatoires.';
      return;
    }
    if (!form.cinOuPasseport?.trim()) {
      this.modalError = 'Le numéro de CIN ou Passeport est obligatoire.';
      return;
    }
    if (!form.nationalite?.trim()) {
      this.modalError = 'La nationalité est obligatoire.';
      return;
    }
    if (!form.adresse?.trim()) {
      this.modalError = 'L’adresse de domicile est obligatoire.';
      return;
    }
    const parts = Number(form.nombreParts);
    if (!parts || parts < 1) {
      this.modalError = 'Chaque associé doit détenir au moins une part sociale.';
      return;
    }

    // Unicité CIN
    const cinNorm = form.cinOuPasseport.trim().toUpperCase();
    const duplicate = (this.associes || []).some((a, idx) =>
      idx !== this.editingIndex &&
      a.actif !== false &&
      a.cinOuPasseport?.trim().toUpperCase() === cinNorm
    );
    if (duplicate) {
      this.modalError = `Un associé avec la pièce d'identité ${cinNorm} existe déjà.`;
      return;
    }

    const vn = this.valeurNominaleEff;
    const apport = computeApport(parts, vn);
    const totalParts = this.totalPartsAttendu;
    const pct = totalParts > 0
      ? Number(computePourcentageDetention(parts, totalParts))
      : 0;

    const toSave: Associe = {
      ...form,
      nom: form.nom.trim(),
      prenom: form.prenom.trim(),
      cinOuPasseport: cinNorm,
      nationalite: form.nationalite.trim(),
      adresse: form.adresse.trim(),
      nombreParts: parts,
      montantApport: apport,
      pourcentageDetention: pct,
      actif: true
    };

    const updated = [...(this.associes || [])];
    if (this.editingIndex !== null) {
      updated[this.editingIndex] = toSave;
    } else {
      updated.push(toSave);
    }

    this.associes = updated;
    this.associesChange.emit(this.associes);
    this.closeModal();
  }

  deactivateAssocie(associe: Associe): void {
    if (this.readonly) return;
    const idx = (this.associes || []).indexOf(associe);
    if (idx === -1) return;

    const updated = [...(this.associes || [])];
    if (associe.id) {
      updated[idx] = { ...associe, actif: false };
    } else {
      updated.splice(idx, 1);
    }

    this.associes = updated;
    this.associesChange.emit(this.associes);
  }

  reactivateAssocie(associe: Associe): void {
    if (this.readonly) return;
    const idx = (this.associes || []).indexOf(associe);
    if (idx === -1) return;

    const updated = [...(this.associes || [])];
    updated[idx] = { ...associe, actif: true };
    this.associes = updated;
    this.associesChange.emit(this.associes);
  }

  private emptyAssocie(): Associe {
    const vn = this.valeurNominaleEff;
    return {
      nom: '',
      prenom: '',
      cinOuPasseport: '',
      nationalite: 'Marocaine',
      adresse: '',
      nombreParts: 1,
      montantApport: vn,
      pourcentageDetention: 0,
      actif: true
    };
  }
}
