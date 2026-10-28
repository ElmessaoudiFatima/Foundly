import { Component, OnInit, OnDestroy, inject, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { WorkflowService } from '../../../../services/workflow.service';
import {
  WorkflowVersionDetailDto,
  EtapeWorkflowDto,
  UpdateEtapeRequest,
  AddEtapeRequest,
  ReorderEtapesRequest
} from '../../../../models/workflow.models';

/**
 * Composant éditeur de workflow.
 * Design calqué fidèlement sur la maquette Image 2 :
 * - Liste des étapes en cartes horizontales avec drag-handle, numéro en pastille, métriques (Frais, Durée, Condition) et bouton Configurer
 * - Panneau latéral "Résumé de la version" avec forme, version, nombre d'étapes, statut et "Condition spécifique"
 * - Aucun emoji : uniquement des icônes SVG vectorielles épurées
 */
@Component({
  selector: 'app-workflow-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './workflow-editor.component.html',
  styleUrls: ['./workflow-editor.component.css']
})
export class WorkflowEditorComponent implements OnInit, OnDestroy {
  private readonly workflowService = inject(WorkflowService);
  private readonly fb = inject(FormBuilder);

  /** ID de la version à afficher/éditer */
  @Input() versionId!: string;

  /** Notifie le parent pour retourner à la liste */
  @Output() backToList = new EventEmitter<void>();

  /** Notifie le parent pour afficher un toast */
  @Output() toastEvent = new EventEmitter<string>();

  // ===================== ÉTAT =====================
  version: WorkflowVersionDetailDto | null = null;
  isLoading = false;
  isSaving = false;

  // Étape sélectionnée pour afficher le détail dans le panneau latéral
  selectedEtape: EtapeWorkflowDto | null = null;

  // Modale édition d'étape ("Configurer")
  isEditModalOpen = false;
  editingEtape: EtapeWorkflowDto | null = null;
  editForm!: FormGroup;
  editError = '';
  isSubmittingEdit = false;

  // Modale ajout d'étape
  isAddModalOpen = false;
  addForm!: FormGroup;
  addError = '';
  isSubmittingAdd = false;

  // Modale suppression d'étape
  isDeleteModalOpen = false;
  deletingEtape: EtapeWorkflowDto | null = null;
  isSubmittingDelete = false;

  // Modale confirmation publication / archivage
  isConfirmModalOpen = false;
  confirmAction: 'publish' | 'archive' | null = null;
  isSubmittingConfirm = false;

  ngOnInit(): void {
    this.initForms();
    this.loadVersion();
  }

  ngOnDestroy(): void {}

  private initForms(): void {
    this.editForm = this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(250)]],
      organismeCode: [''],
      organismeLibelle: ['', Validators.maxLength(200)],
      fraisMad: [0, [Validators.min(0)]],
      delaiIndicatif: ['', Validators.maxLength(100)],
      conditionApplication: ['', Validators.maxLength(500)],
      obligatoire: [true],
      externe: [false]
    });

    this.addForm = this.fb.group({
      code: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/^[A-Z0-9_]+$/)]],
      libelle: ['', [Validators.required, Validators.maxLength(250)]],
      organismeCode: [''],
      organismeLibelle: ['', Validators.maxLength(200)],
      fraisMad: [0, [Validators.min(0)]],
      delaiIndicatif: ['', Validators.maxLength(100)],
      conditionApplication: ['', Validators.maxLength(500)],
      obligatoire: [true],
      externe: [false]
    });
  }

  // ===================== CHARGEMENT =====================
  loadVersion(): void {
    if (!this.versionId) return;
    this.isLoading = true;
    this.workflowService.getVersionById(this.versionId).subscribe({
      next: (data) => {
        this.version = data;
        this.isLoading = false;
        // Conserver ou initialiser l'étape sélectionnée
        if (this.selectedEtape) {
          this.selectedEtape = data.etapes.find(e => e.id === this.selectedEtape?.id) ?? (data.etapes[0] || null);
        } else if (data.etapes.length > 0) {
          // Sélectionner la première étape ayant une condition spécifique par défaut
          const withCond = data.etapes.find(e => e.conditionApplication && e.conditionApplication.trim().length > 0);
          this.selectedEtape = withCond || data.etapes[0];
        }
      },
      error: () => {
        this.isLoading = false;
        this.toastEvent.emit('Impossible de charger la version de workflow.');
      }
    });
  }

  get isEditable(): boolean {
    return this.version?.statut === 'BROUILLON';
  }

  // ===================== SÉLECTION =====================
  selectEtape(etape: EtapeWorkflowDto): void {
    this.selectedEtape = etape;
  }

  // ===================== RÉORDONNANCEMENT =====================
  moveUp(etape: EtapeWorkflowDto): void {
    if (!this.version || !this.isEditable) return;
    const idx = this.version.etapes.findIndex(e => e.id === etape.id);
    if (idx <= 0) return;

    const newOrder = [...this.version.etapes];
    [newOrder[idx - 1], newOrder[idx]] = [newOrder[idx], newOrder[idx - 1]];
    this.applyReorder(newOrder);
  }

  moveDown(etape: EtapeWorkflowDto): void {
    if (!this.version || !this.isEditable) return;
    const idx = this.version.etapes.findIndex(e => e.id === etape.id);
    if (idx < 0 || idx >= this.version.etapes.length - 1) return;

    const newOrder = [...this.version.etapes];
    [newOrder[idx], newOrder[idx + 1]] = [newOrder[idx + 1], newOrder[idx]];
    this.applyReorder(newOrder);
  }

  private applyReorder(newOrder: EtapeWorkflowDto[]): void {
    if (!this.version) return;
    this.version = { ...this.version, etapes: newOrder.map((e, i) => ({ ...e, ordre: i + 1 })) };

    const request: ReorderEtapesRequest = { etapeIds: newOrder.map(e => e.id) };
    this.workflowService.reorderEtapes(this.versionId, request).subscribe({
      next: (updated) => {
        this.version = updated;
      },
      error: () => {
        this.loadVersion();
        this.toastEvent.emit('Erreur lors du réordonnancement. Rechargement...');
      }
    });
  }

  // ===================== ÉDITION D'ÉTAPE (CONFIGURER) =====================
  openEditModal(etape: EtapeWorkflowDto): void {
    this.editingEtape = etape;
    this.editForm.patchValue({
      libelle: etape.libelle,
      organismeCode: etape.organismeCode ?? '',
      organismeLibelle: etape.organismeLibelle ?? '',
      fraisMad: etape.fraisMad ?? 0,
      delaiIndicatif: etape.delaiIndicatif ?? '',
      conditionApplication: etape.conditionApplication ?? '',
      obligatoire: etape.obligatoire,
      externe: etape.externe
    });
    this.editError = '';
    this.isEditModalOpen = true;
  }

  closeEditModal(): void {
    this.isEditModalOpen = false;
    this.editingEtape = null;
    this.editError = '';
  }

  submitEditEtape(): void {
    if (!this.editingEtape || this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.isSubmittingEdit = true;
    this.editError = '';

    const raw = this.editForm.value;
    const request: UpdateEtapeRequest = {
      libelle: raw.libelle.trim(),
      organismeCode: raw.organismeCode?.trim() || null,
      organismeLibelle: raw.organismeLibelle?.trim() || null,
      fraisMad: raw.fraisMad ?? 0,
      delaiIndicatif: raw.delaiIndicatif?.trim() || null,
      conditionApplication: raw.conditionApplication?.trim() || null,
      obligatoire: raw.obligatoire,
      externe: raw.externe
    };

    this.workflowService.updateEtape(this.versionId, this.editingEtape.id, request).subscribe({
      next: () => {
        this.isSubmittingEdit = false;
        this.closeEditModal();
        this.toastEvent.emit('Étape mise à jour.');
        this.loadVersion();
      },
      error: (err) => {
        this.isSubmittingEdit = false;
        this.editError = err?.error?.message || 'Erreur lors de la mise à jour.';
      }
    });
  }

  // ===================== AJOUT D'ÉTAPE =====================
  openAddModal(): void {
    if (!this.isEditable) return;
    this.addForm.reset({
      code: '',
      libelle: '',
      organismeCode: '',
      organismeLibelle: '',
      fraisMad: 0,
      delaiIndicatif: '',
      conditionApplication: '',
      obligatoire: true,
      externe: false
    });
    this.addError = '';
    this.isAddModalOpen = true;
  }

  closeAddModal(): void {
    this.isAddModalOpen = false;
    this.addError = '';
  }

  submitAddEtape(): void {
    if (this.addForm.invalid) {
      this.addForm.markAllAsTouched();
      return;
    }
    this.isSubmittingAdd = true;
    this.addError = '';

    const raw = this.addForm.value;
    const request: AddEtapeRequest = {
      code: raw.code.trim().toUpperCase(),
      libelle: raw.libelle.trim(),
      organismeCode: raw.organismeCode?.trim() || null,
      organismeLibelle: raw.organismeLibelle?.trim() || null,
      fraisMad: raw.fraisMad ?? 0,
      delaiIndicatif: raw.delaiIndicatif?.trim() || null,
      conditionApplication: raw.conditionApplication?.trim() || null,
      obligatoire: raw.obligatoire,
      externe: raw.externe
    };

    this.workflowService.addEtape(this.versionId, request).subscribe({
      next: () => {
        this.isSubmittingAdd = false;
        this.closeAddModal();
        this.toastEvent.emit('Étape ajoutée.');
        this.loadVersion();
      },
      error: (err) => {
        this.isSubmittingAdd = false;
        this.addError = err?.error?.message || 'Erreur lors de l\'ajout de l\'étape.';
      }
    });
  }

  // ===================== SUPPRESSION D'ÉTAPE =====================
  openDeleteConfirm(etape: EtapeWorkflowDto): void {
    if (!this.isEditable) return;
    this.deletingEtape = etape;
    this.isDeleteModalOpen = true;
  }

  closeDeleteModal(): void {
    this.isDeleteModalOpen = false;
    this.deletingEtape = null;
  }

  confirmDeleteEtape(): void {
    if (!this.deletingEtape) return;
    this.isSubmittingDelete = true;

    this.workflowService.deleteEtape(this.versionId, this.deletingEtape.id).subscribe({
      next: () => {
        this.isSubmittingDelete = false;
        if (this.selectedEtape?.id === this.deletingEtape?.id) {
          this.selectedEtape = null;
        }
        this.closeDeleteModal();
        this.toastEvent.emit('Étape supprimée.');
        this.loadVersion();
      },
      error: (err) => {
        this.isSubmittingDelete = false;
        this.toastEvent.emit(err?.error?.message || 'Erreur lors de la suppression.');
        this.closeDeleteModal();
      }
    });
  }

  // ===================== ACTIONS SUR LA VERSION (PUBLIER / ARCHIVER) =====================
  openPublishConfirm(): void {
    this.confirmAction = 'publish';
    this.isConfirmModalOpen = true;
  }

  openArchiveConfirm(): void {
    this.confirmAction = 'archive';
    this.isConfirmModalOpen = true;
  }

  closeConfirmModal(): void {
    this.isConfirmModalOpen = false;
    this.confirmAction = null;
  }

  confirmActionSubmit(): void {
    if (!this.confirmAction || !this.versionId) return;
    this.isSubmittingConfirm = true;
    const action = this.confirmAction;

    const obs$ = action === 'publish'
      ? this.workflowService.publishVersion(this.versionId)
      : this.workflowService.archiverVersion(this.versionId);

    obs$.subscribe({
      next: (updated) => {
        this.isSubmittingConfirm = false;
        this.closeConfirmModal();
        const msg = action === 'publish'
          ? `Version ${updated.numeroVersion} (${updated.formeJuridiqueLabel}) publiée. L'ancienne version active est archivée.`
          : `Version ${updated.numeroVersion} (${updated.formeJuridiqueLabel}) archivée.`;
        this.toastEvent.emit(msg);
        this.loadVersion();
      },
      error: (err) => {
        this.isSubmittingConfirm = false;
        this.toastEvent.emit(err?.error?.message || `Erreur lors de l'opération.`);
        this.closeConfirmModal();
      }
    });
  }

  // ===================== NAVIGATION =====================
  goBack(): void {
    this.backToList.emit();
  }

  // ===================== FORMATTAGE (Image 2) =====================
  getOrganismeDisplay(etape: EtapeWorkflowDto): string {
    if (etape.externe) {
      const nom = etape.organismeLibelle || 'Banque';
      return `${nom} · Partenaire externe`;
    }
    if (!etape.organismeCode) {
      return 'Foundly · Système';
    }
    const nom = etape.organismeLibelle || etape.organismeCode;
    return `${nom} · Agent organisme`;
  }

  getFraisDisplay(etape: EtapeWorkflowDto): string {
    if (etape.externe && (!etape.fraisMad || etape.fraisMad === 0)) {
      return 'Variable';
    }
    if (etape.fraisMad === undefined || etape.fraisMad === null) {
      return '—';
    }
    if (etape.fraisMad === 0) {
      return '0 MAD';
    }
    return `${etape.fraisMad.toLocaleString('fr-MA')} MAD`;
  }

  getConditionDisplay(etape: EtapeWorkflowDto): string {
    if (etape.conditionApplication && etape.conditionApplication.trim().length > 0) {
      return etape.conditionApplication;
    }
    return 'Toutes les sociétés';
  }

  getSpecificConditionText(): string {
    if (this.selectedEtape?.conditionApplication && this.selectedEtape.conditionApplication.trim().length > 0) {
      return this.selectedEtape.conditionApplication;
    }
    // Si aucune condition sur l'étape courante, afficher la première étape qui en a une
    const withCond = this.version?.etapes.find(e => e.conditionApplication && e.conditionApplication.trim().length > 0);
    if (withCond) {
      return withCond.conditionApplication!;
    }
    return 'Cette étape s\'applique à l\'ensemble des dossiers créés sous cette forme juridique.';
  }
}
