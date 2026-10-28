import { Component, OnInit, inject, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { WorkflowService } from '../../../../services/workflow.service';
import {
  WorkflowVersionSummaryDto,
  CreateWorkflowVersionRequest,
  LegalFormCode
} from '../../../../models/workflow.models';

/**
 * Composant liste des versions de workflow.
 * Conforme à la maquette :
 * - Cartes horizontales épurées
 * - Aucun emoji, icônes vectorielles SVG intégrées
 * - Avertissement RG14 en bandeau ambre
 */
@Component({
  selector: 'app-workflow-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './workflow-list.component.html',
  styleUrls: ['./workflow-list.component.css']
})
export class WorkflowListComponent implements OnInit {
  private readonly workflowService = inject(WorkflowService);
  private readonly fb = inject(FormBuilder);

  /** Notifie le composant parent (AdminComponent) pour ouvrir l'éditeur */
  @Output() openEditor = new EventEmitter<string>();

  /** Notifie le parent pour afficher un toast */
  @Output() toastEvent = new EventEmitter<string>();

  // ===================== ÉTAT =====================
  versions: WorkflowVersionSummaryDto[] = [];
  isLoading = false;
  filterForme: LegalFormCode | '' = '';

  // Modale création de version
  isCreateModalOpen = false;
  isSubmittingCreate = false;
  createError = '';
  createForm!: FormGroup;

  // Confirmation archivage / publication
  isConfirmModalOpen = false;
  confirmAction: 'publish' | 'archive' | null = null;
  confirmVersionId: string | null = null;
  confirmVersionLabel = '';
  isSubmittingConfirm = false;

  readonly formes: { code: LegalFormCode; label: string }[] = [
    { code: 'AUTO_ENTREPRENEUR', label: 'Auto-entrepreneur' },
    { code: 'SARL', label: 'SARL' },
    { code: 'SARL_AU', label: 'SARL-AU' },
    { code: 'SA', label: 'SA' }
  ];

  ngOnInit(): void {
    this.initCreateForm();
    this.loadVersions();
  }

  private initCreateForm(): void {
    this.createForm = this.fb.group({
      formeJuridique: ['', Validators.required],
      description: [''],
      dupliquerDepuis: [null]
    });
  }

  // ===================== CHARGEMENT =====================
  loadVersions(): void {
    this.isLoading = true;
    const forme = this.filterForme || undefined;
    this.workflowService.getAllVersions(forme as LegalFormCode | undefined).subscribe({
      next: (data) => {
        this.versions = data;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.toastEvent.emit('Impossible de charger les versions de workflow.');
      }
    });
  }

  onFilterChange(): void {
    this.loadVersions();
  }

  // ===================== NAVIGATION =====================
  openWorkflowEditor(versionId: string): void {
    this.openEditor.emit(versionId);
  }

  // ===================== CRÉATION =====================
  openCreateModal(): void {
    this.createForm.reset({ formeJuridique: '', description: '', dupliquerDepuis: null });
    this.createError = '';
    this.isCreateModalOpen = true;
  }

  closeCreateModal(): void {
    this.isCreateModalOpen = false;
    this.createError = '';
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.isSubmittingCreate = true;
    this.createError = '';

    const payload: CreateWorkflowVersionRequest = {
      formeJuridique: this.createForm.value.formeJuridique,
      description: this.createForm.value.description || undefined,
      dupliquerDepuis: this.createForm.value.dupliquerDepuis || undefined
    };

    this.workflowService.createVersion(payload).subscribe({
      next: (created) => {
        this.isSubmittingCreate = false;
        this.closeCreateModal();
        this.toastEvent.emit(`Nouvelle version v${created.numeroVersion} créée en Brouillon.`);
        this.openWorkflowEditor(created.id);
      },
      error: (err) => {
        this.isSubmittingCreate = false;
        this.createError = err?.error?.message || 'Erreur lors de la création de la version.';
      }
    });
  }

  // Versions disponibles pour duplication
  get availableVersionsForDuplication(): WorkflowVersionSummaryDto[] {
    const selectedForme = this.createForm?.get('formeJuridique')?.value;
    if (!selectedForme) return [];
    return this.versions.filter(v => v.formeJuridique === selectedForme);
  }

  // ===================== CONFIRMATIONS =====================
  openPublishConfirm(version: WorkflowVersionSummaryDto): void {
    this.confirmVersionId = version.id;
    this.confirmAction = 'publish';
    this.confirmVersionLabel = `${version.formeJuridiqueLabel} v${version.numeroVersion}`;
    this.isConfirmModalOpen = true;
  }

  openArchiveConfirm(version: WorkflowVersionSummaryDto): void {
    this.confirmVersionId = version.id;
    this.confirmAction = 'archive';
    this.confirmVersionLabel = `${version.formeJuridiqueLabel} v${version.numeroVersion}`;
    this.isConfirmModalOpen = true;
  }

  closeConfirmModal(): void {
    this.isConfirmModalOpen = false;
    this.confirmAction = null;
    this.confirmVersionId = null;
  }

  confirmActionSubmit(): void {
    if (!this.confirmVersionId || !this.confirmAction) return;
    this.isSubmittingConfirm = true;

    const id = this.confirmVersionId;
    const action = this.confirmAction;

    const obs$ = action === 'publish'
      ? this.workflowService.publishVersion(id)
      : this.workflowService.archiverVersion(id);

    obs$.subscribe({
      next: (updated) => {
        this.isSubmittingConfirm = false;
        this.closeConfirmModal();
        const msg = action === 'publish'
          ? `Version ${updated.numeroVersion} (${updated.formeJuridiqueLabel}) publiée. L'ancienne version active a été archivée.`
          : `Version ${updated.numeroVersion} (${updated.formeJuridiqueLabel}) archivée.`;
        this.toastEvent.emit(msg);
        this.loadVersions();
      },
      error: (err) => {
        this.isSubmittingConfirm = false;
        const msg = err?.error?.message || `Erreur lors de l'opération.`;
        this.toastEvent.emit(msg);
        this.closeConfirmModal();
      }
    });
  }

  // ===================== HELPERS =====================
  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    try {
      return new Intl.DateTimeFormat('fr-FR', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      }).format(new Date(dateStr));
    } catch {
      return '—';
    }
  }
}
