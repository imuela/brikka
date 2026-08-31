import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import {
  ASSIGNMENT_TYPE_LABELS,
  BANK_OFFER_STATUS_LABELS,
  BANK_REQUEST_STATUS_LABELS,
  CASE_STATUS_LABELS,
  CONVERSATION_STATUS_LABELS,
  CONVERSATION_TYPE_LABELS,
  DOCUMENT_REQUEST_STATUS_LABELS,
  FEE_STATUS_LABELS,
  FEE_TYPE_LABELS,
  FINANCING_REQUEST_STATUS_LABELS,
  MATCH_RESULT_LABELS,
  OPERATION_TYPE_LABELS,
  PARTICIPATION_TYPE_LABELS,
  PROPERTY_TYPE_LABELS,
  REVIEW_STATUS_LABELS,
  TASK_STATUS_LABELS,
  TASK_TYPE_LABELS,
  VIABILITY_CATEGORY_LABELS,
} from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { FinancialAnalysisResult } from '../../financial-analysis/financial-analysis.model';
import { FinancialAnalysisService } from '../../financial-analysis/financial-analysis.service';
import { CaseFee } from '../../case-fee/case-fee.model';
import { CaseFeeService } from '../../case-fee/case-fee.service';
import { EditCaseFeeDialogComponent } from '../../case-fee/case-fee-dialogs/edit-case-fee-dialog.component';
import { GeneratedDocument as ContractDocument } from '../../engagement-contract/engagement-contract.model';
import { EngagementContractService } from '../../engagement-contract/engagement-contract.service';
import { GeneratedDocument as DossierDocument } from '../../viability-dossier/viability-dossier.model';
import { ViabilityDossierService } from '../../viability-dossier/viability-dossier.service';
import { Bank } from '../../banks/bank.model';
import { BankService } from '../../banks/bank.service';
import { RunMatchingDialogComponent } from '../../bank-matching/bank-matching-dialogs/run-matching-dialog.component';
import { MatchingResultDetailDialogComponent } from '../../bank-matching/bank-matching-dialogs/matching-result-detail-dialog.component';
import { BankMatchResult } from '../../bank-matching/bank-matching.model';
import { BankMatchingService } from '../../bank-matching/bank-matching.service';
import { CreateBankOfferDialogComponent } from '../../bank-request/bank-request-dialogs/create-bank-offer-dialog.component';
import { CreateBankRequestDialogComponent } from '../../bank-request/bank-request-dialogs/create-bank-request-dialog.component';
import { CreateBankResponseDialogComponent } from '../../bank-request/bank-request-dialogs/create-bank-response-dialog.component';
import { BankOffer, BankRequest, FinalFinancing } from '../../bank-request/bank-request.model';
import { BankRequestService } from '../../bank-request/bank-request.service';
import { ConversationDetailDialogComponent } from '../../communications/communication-dialogs/conversation-detail-dialog.component';
import { CreateConversationDialogComponent } from '../../communications/communication-dialogs/create-conversation-dialog.component';
import { Conversation } from '../../communications/communication.model';
import { CommunicationService } from '../../communications/communication.service';
import {
  CaseChecklist,
  CaseDocument,
  CaseDocumentRequest,
  CaseDocumentVersion,
  DocumentType,
} from '../../documents/document.model';
import { CreateDocumentRequestDialogComponent } from '../../documents/document-dialogs/create-document-request-dialog.component';
import { CreateDocumentDialogComponent } from '../../documents/document-dialogs/create-document-dialog.component';
import { ReviewDocumentDialogComponent } from '../../documents/document-dialogs/review-document-dialog.component';
import { UploadVersionDialogComponent } from '../../documents/document-dialogs/upload-version-dialog.component';
import { VersionsDialogComponent } from '../../documents/document-dialogs/versions-dialog.component';
import { DocumentsService } from '../../documents/documents.service';
import { CreateFinancingRequestDialogComponent } from '../../financing/financing-dialogs/create-financing-request-dialog.component';
import { CreateSimulationDialogComponent } from '../../financing/financing-dialogs/create-simulation-dialog.component';
import { UpdateFinancingRequestDialogComponent } from '../../financing/financing-dialogs/update-financing-request-dialog.component';
import { FinancingRequest, Simulation } from '../../financing/financing.model';
import { FinancingService } from '../../financing/financing.service';
import { Property } from '../../property/property.model';
import { PropertyDialogComponent } from '../../property/property-dialog.component';
import { PropertyService } from '../../property/property.service';
import { CreateTaskDialogComponent } from '../../tasks/task-dialogs/create-task-dialog.component';
import { EditTaskDialogComponent } from '../../tasks/task-dialogs/edit-task-dialog.component';
import { Task } from '../../tasks/task.model';
import { TaskService } from '../../tasks/task.service';
import { AddClientDialogComponent } from '../case-dialogs/add-client-dialog.component';
import { AssignDialogComponent } from '../case-dialogs/assign-dialog.component';
import { CancelDialogComponent } from '../case-dialogs/cancel-dialog.component';
import { ChangeStatusDialogComponent } from '../case-dialogs/change-status-dialog.component';
import { ReopenDialogComponent } from '../case-dialogs/reopen-dialog.component';
import { AssignableUser, Case, CaseAssignment, CaseClient } from '../case.model';
import { CasesService } from '../cases.service';

@Component({
  selector: 'app-case-detail',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    CurrencyPipe,
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    StatusLabelPipe,
    StatusBadgeComponent,
  ],
  templateUrl: './case-detail.component.html',
  styleUrl: './case-detail.component.scss',
})
export class CaseDetailComponent {
  readonly caseStatusLabels = CASE_STATUS_LABELS;
  readonly participationTypeLabels = PARTICIPATION_TYPE_LABELS;
  readonly reviewStatusLabels = REVIEW_STATUS_LABELS;
  readonly documentRequestStatusLabels = DOCUMENT_REQUEST_STATUS_LABELS;
  readonly financingRequestStatusLabels = FINANCING_REQUEST_STATUS_LABELS;
  readonly matchResultLabels = MATCH_RESULT_LABELS;
  readonly bankRequestStatusLabels = BANK_REQUEST_STATUS_LABELS;
  readonly bankOfferStatusLabels = BANK_OFFER_STATUS_LABELS;
  readonly taskStatusLabels = TASK_STATUS_LABELS;
  readonly taskTypeLabels = TASK_TYPE_LABELS;
  readonly conversationTypeLabels = CONVERSATION_TYPE_LABELS;
  readonly conversationStatusLabels = CONVERSATION_STATUS_LABELS;
  readonly operationTypeLabels = OPERATION_TYPE_LABELS;
  readonly assignmentTypeLabels = ASSIGNMENT_TYPE_LABELS;
  readonly propertyTypeLabels = PROPERTY_TYPE_LABELS;
  private readonly casesService = inject(CasesService);
  private readonly propertyService = inject(PropertyService);
  private readonly documentsService = inject(DocumentsService);
  private readonly financingService = inject(FinancingService);
  private readonly bankService = inject(BankService);
  private readonly bankMatchingService = inject(BankMatchingService);
  private readonly bankRequestService = inject(BankRequestService);
  private readonly taskService = inject(TaskService);
  private readonly communicationService = inject(CommunicationService);
  private readonly financialAnalysisService = inject(FinancialAnalysisService);
  private readonly caseFeeService = inject(CaseFeeService);
  private readonly engagementContractService = inject(EngagementContractService);
  private readonly viabilityDossierService = inject(ViabilityDossierService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  /** Sprint 37 (D36-2): guards every open*() below against a second dialog opening while one is
   * already up — a double click, or a different action button, could otherwise stack two
   * mat-dialog-container instances (reproduced in Sprint 36). Checking MatDialog's own
   * openDialogs is the minimal fix: no new state to keep in sync, no change to any dialog's
   * result handling. */
  private hasOpenDialog(): boolean {
    return this.dialog.openDialogs.length > 0;
  }

  readonly caseId = this.route.snapshot.paramMap.get('id')!;
  readonly theCase = signal<Case | null>(null);
  readonly assignments = signal<CaseAssignment[] | null>(null);
  readonly assignableUsers = signal<AssignableUser[]>([]);
  readonly clients = signal<CaseClient[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly clientColumns = ['name', 'participationType', 'isPrimary', 'actions'];
  readonly assignmentColumns = ['userId', 'assignmentType', 'active'];

  readonly property = signal<Property | null>(null);
  readonly propertyLoading = signal(true);

  readonly documentTypes = signal<DocumentType[]>([]);
  readonly documents = signal<CaseDocument[] | null>(null);
  readonly documentColumns = ['type', 'status', 'actions'];

  readonly documentRequests = signal<CaseDocumentRequest[] | null>(null);
  readonly documentRequestColumns = ['type', 'client', 'status', 'dueAt', 'actions'];

  /** BRIKKA V2 I1: document checklist of the case (auto-generated server-side on entering
   * DOCUMENTATION). complete = every mandatory item APPROVED. */
  readonly checklist = signal<CaseChecklist | null>(null);
  readonly checklistError = signal<string | null>(null);
  readonly checklistColumns = ['document', 'holder', 'mandatory', 'state'];
  readonly checklistItemStateLabels: Record<string, string> = {
    MISSING: 'Falta',
    SUBMITTED: 'Subido (pendiente de revisión)',
    REJECTED: 'Rechazado',
    APPROVED: 'Aprobado',
  };

  readonly simulations = signal<Simulation[] | null>(null);
  readonly simulationColumns = ['principal', 'interestRate', 'termMonths', 'estimatedPayment', 'createdAt'];

  readonly financingRequests = signal<FinancingRequest[] | null>(null);
  readonly financingRequestColumns = ['status', 'requestedAmount', 'termMonths', 'createdAt', 'actions'];

  readonly banks = signal<Bank[]>([]);

  readonly matchResults = signal<BankMatchResult[] | null>(null);
  readonly matchResultColumns = ['bank', 'globalResult', 'evaluatedAt', 'actions'];

  readonly bankRequests = signal<BankRequest[] | null>(null);
  readonly bankRequestColumns = ['bank', 'status', 'submittedAt', 'actions'];

  readonly offers = signal<BankOffer[] | null>(null);
  readonly offerColumns = ['bank', 'amount', 'interestRate', 'termMonths', 'payment', 'status', 'actions'];
  readonly finalFinancing = signal<FinalFinancing | null>(null);

  readonly tasks = signal<Task[] | null>(null);
  readonly taskColumns = ['title', 'type', 'status', 'assignedTo', 'dueAt', 'actions'];

  readonly conversations = signal<Conversation[] | null>(null);
  readonly conversationColumns = ['type', 'status', 'createdAt', 'actions'];

  readonly financialAnalysisResults = signal<FinancialAnalysisResult[] | null>(null);
  readonly financialAnalysisRunning = signal(false);
  readonly financialAnalysisError = signal<string | null>(null);
  readonly financialAnalysisColumns = [
    'client',
    'monthlyIncome',
    'existingMonthlyDebts',
    'monthlyPayment',
    'dtiPercent',
    'viabilityCategory',
    'calculatedAt',
  ];
  readonly viabilityCategoryLabels = VIABILITY_CATEGORY_LABELS;

  readonly caseFee = signal<CaseFee | null>(null);
  readonly caseFeeLoading = signal(true);
  readonly caseFeeError = signal<string | null>(null);
  readonly feeTypeLabels = FEE_TYPE_LABELS;
  readonly feeStatusLabels = FEE_STATUS_LABELS;

  readonly contractDocument = signal<ContractDocument | null>(null);
  readonly contractGenerating = signal(false);
  readonly contractError = signal<string | null>(null);

  readonly dossierDocument = signal<DossierDocument | null>(null);
  readonly dossierGenerating = signal(false);
  readonly dossierError = signal<string | null>(null);

  constructor() {
    this.loadCase();
    this.loadAssignments();
    this.casesService.listAssignableUsers().subscribe({
      next: (users) => this.assignableUsers.set(users),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadClients();
    this.loadProperty();
    this.documentsService.listDocumentTypes().subscribe({
      next: (types) => this.documentTypes.set(types),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadDocuments();
    this.loadDocumentRequests();
    this.loadSimulations();
    this.loadFinancingRequests();
    this.bankService.list().subscribe({
      next: (banks) => this.banks.set(banks),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadMatchResults();
    this.loadBankRequests();
    this.loadOffers();
    this.loadTasks();
    this.loadConversations();
    this.loadFinancialAnalysis();
    this.loadCaseFee();
    this.loadContract();
    this.loadDossier();
  }

  private loadCase(): void {
    this.casesService.get(this.caseId).subscribe({
      next: (theCase) => this.theCase.set(theCase),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private loadAssignments(): void {
    this.casesService.listAssignments(this.caseId).subscribe({
      next: (assignments) => this.assignments.set(assignments),
      error: (err: ApiError) => {
        this.assignments.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  private loadClients(): void {
    this.casesService.listClients(this.caseId).subscribe({
      next: (clients) => this.clients.set(clients),
      error: (err: ApiError) => {
        this.clients.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openChangeStatus(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(ChangeStatusDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Case | undefined) => {
        if (result) {
          this.theCase.set(result);
        }
      });
  }

  openCancel(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CancelDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Case | undefined) => {
        if (result) {
          this.theCase.set(result);
        }
      });
  }

  openReopen(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(ReopenDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Case | undefined) => {
        if (result) {
          this.theCase.set(result);
        }
      });
  }

  openAssign(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(AssignDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: unknown) => {
        if (result) {
          this.loadAssignments();
        }
      });
  }

  openAddClient(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(AddClientDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((added: boolean | undefined) => {
        if (added) {
          this.loadClients();
        }
      });
  }

  removeClient(clientId: string): void {
    const client = this.clients()?.find((c) => c.clientId === clientId);
    const name = client ? `${client.firstName} ${client.lastName}` : 'este cliente';
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Quitar cliente',
          message: `¿Seguro que quieres quitar a ${name} de esta operación? Esta acción no se puede deshacer.`,
          confirmLabel: 'Quitar',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.casesService.removeClient(this.caseId, clientId).subscribe({
          next: () => this.loadClients(),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }

  private loadProperty(): void {
    this.propertyLoading.set(true);
    this.propertyService.get(this.caseId).subscribe({
      next: (property) => {
        this.property.set(property);
        this.propertyLoading.set(false);
      },
      error: (err: ApiError) => {
        this.propertyLoading.set(false);
        if (err.status !== 404) {
          this.error.set(friendlyErrorMessage(err));
        }
      },
    });
  }

  openProperty(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(PropertyDialogComponent, {
        data: { caseId: this.caseId, property: this.property() },
        width: '480px',
      })
      .afterClosed()
      .subscribe((result: Property | undefined) => {
        if (result) {
          this.property.set(result);
        }
      });
  }

  private loadDocuments(): void {
    this.documentsService.list(this.caseId).subscribe({
      next: (documents) => this.documents.set(documents),
      error: (err: ApiError) => {
        this.documents.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
    // BRIKKA V2 I1: the checklist state is derived from these documents' review status.
    this.loadChecklist();
  }

  private loadChecklist(): void {
    this.documentsService.getChecklist(this.caseId).subscribe({
      next: (checklist) => this.checklist.set(checklist),
      error: (err: ApiError) => {
        this.checklist.set(null);
        this.checklistError.set(friendlyErrorMessage(err));
      },
    });
  }

  /** BRIKKA V2 I1: resolve a checklist item's holder (null = document of the expediente). */
  checklistHolderName(clientId: string | null): string {
    if (!clientId) {
      return 'Expediente';
    }
    const holder = (this.clients() ?? []).find((c) => c.clientId === clientId);
    return holder
      ? `${holder.firstName ?? ''} ${holder.lastName ?? ''}`.trim() || clientId
      : clientId;
  }

  documentTypeName(documentTypeId: string): string {
    return this.documentTypes().find((t) => t.id === documentTypeId)?.name ?? documentTypeId;
  }

  openCreateDocument(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateDocumentDialogComponent, {
        data: {
          caseId: this.caseId,
          documentTypes: this.documentTypes(),
          holders: (this.clients() ?? []).map((c) => ({
            id: c.clientId,
            name: `${c.firstName ?? ''} ${c.lastName ?? ''}`.trim() || c.clientId,
          })),
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: CaseDocument | undefined) => {
        if (result) {
          this.loadDocuments();
          this.loadChecklist();
        }
      });
  }

  openUploadVersion(documentId: string): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(UploadVersionDialogComponent, { data: { documentId }, width: '400px' })
      .afterClosed()
      .subscribe((result: CaseDocumentVersion | undefined) => {
        if (result) {
          this.loadDocuments();
        }
      });
  }

  openReviewDocument(documentId: string): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(ReviewDocumentDialogComponent, { data: { documentId }, width: '400px' })
      .afterClosed()
      .subscribe((result: CaseDocumentVersion | undefined) => {
        if (result) {
          this.loadDocuments();
        }
      });
  }

  openVersions(documentId: string): void {
    if (this.hasOpenDialog()) return;
    this.dialog.open(VersionsDialogComponent, { data: { documentId }, width: '600px' });
  }

  publish(documentId: string): void {
    this.documentsService.publish(documentId).subscribe({
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  unpublish(documentId: string): void {
    this.documentsService.unpublish(documentId).subscribe({
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  download(documentId: string): void {
    this.documentsService.downloadCurrent(documentId).subscribe({
      next: (download) => window.open(download.url, '_blank'),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private loadDocumentRequests(): void {
    this.documentsService.listRequests(this.caseId).subscribe({
      next: (requests) => this.documentRequests.set(requests),
      error: (err: ApiError) => {
        this.documentRequests.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openCreateDocumentRequest(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateDocumentRequestDialogComponent, {
        data: { caseId: this.caseId, documentTypes: this.documentTypes() },
        width: '420px',
      })
      .afterClosed()
      .subscribe((result: CaseDocumentRequest | undefined) => {
        if (result) {
          this.loadDocumentRequests();
        }
      });
  }

  updateDocumentRequestStatus(id: string, status: string): void {
    this.documentsService.updateRequest(id, { status }).subscribe({
      next: () => this.loadDocumentRequests(),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  clientName(clientId: string | null): string {
    if (!clientId) {
      return '—';
    }
    const client = this.clients()?.find((c) => c.clientId === clientId);
    return client ? `${client.firstName} ${client.lastName}` : clientId;
  }

  userName(userId: string): string {
    const user = this.assignableUsers().find((u) => u.id === userId);
    return user ? `${user.firstName} ${user.lastName}` : userId;
  }

  private loadSimulations(): void {
    this.financingService.listSimulations(this.caseId).subscribe({
      next: (simulations) => this.simulations.set(simulations),
      error: (err: ApiError) => {
        this.simulations.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openCreateSimulation(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateSimulationDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Simulation | undefined) => {
        if (result) {
          this.loadSimulations();
        }
      });
  }

  private loadFinancingRequests(): void {
    this.financingService.listFinancingRequests(this.caseId).subscribe({
      next: (requests) => this.financingRequests.set(requests),
      error: (err: ApiError) => {
        this.financingRequests.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openCreateFinancingRequest(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateFinancingRequestDialogComponent, {
        data: { caseId: this.caseId },
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: FinancingRequest | undefined) => {
        if (result) {
          this.loadFinancingRequests();
        }
      });
  }

  openUpdateFinancingRequest(financingRequest: FinancingRequest): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(UpdateFinancingRequestDialogComponent, {
        data: { financingRequest },
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: FinancingRequest | undefined) => {
        if (result) {
          this.loadFinancingRequests();
        }
      });
  }

  bankName(bankId: string): string {
    return this.banks().find((b) => b.id === bankId)?.name ?? bankId;
  }

  finalFinancingBankName(): string {
    const financing = this.finalFinancing();
    if (!financing) {
      return '';
    }
    const offer = this.offers()?.find((o) => o.id === financing.bankOfferId);
    return offer ? this.bankName(offer.bankId) : '—';
  }

  private loadMatchResults(): void {
    this.bankMatchingService.list(this.caseId).subscribe({
      next: (results) => this.matchResults.set(results),
      error: (err: ApiError) => {
        this.matchResults.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openRunMatching(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(RunMatchingDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: BankMatchResult | undefined) => {
        if (result) {
          this.loadMatchResults();
        }
      });
  }

  openMatchingResultDetail(result: BankMatchResult): void {
    if (this.hasOpenDialog()) return;
    this.dialog.open(MatchingResultDetailDialogComponent, {
      data: { caseId: this.caseId, result },
      width: '700px',
    });
  }

  private loadBankRequests(): void {
    this.bankRequestService.list(this.caseId).subscribe({
      next: (requests) => this.bankRequests.set(requests),
      error: (err: ApiError) => {
        this.bankRequests.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openCreateBankRequest(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateBankRequestDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: BankRequest | undefined) => {
        if (result) {
          this.loadBankRequests();
        }
      });
  }

  openCreateBankResponse(bankRequestId: string): void {
    if (this.hasOpenDialog()) return;
    // No list endpoint exists for bank_responses (create-only, see BankRequestService) — the
    // dialog closing on success is the only and sufficient feedback, same as every other
    // create-only dialog in this app.
    this.dialog.open(CreateBankResponseDialogComponent, {
      data: { bankRequestId },
      width: '400px',
    });
  }

  openCreateBankOffer(bankRequestId: string): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateBankOfferDialogComponent, { data: { bankRequestId }, width: '400px' })
      .afterClosed()
      .subscribe((result: BankOffer | undefined) => {
        if (result) {
          this.loadOffers();
        }
      });
  }

  private loadOffers(): void {
    this.bankRequestService.listOffers(this.caseId).subscribe({
      next: (offers) => this.offers.set(offers),
      error: (err: ApiError) => {
        this.offers.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  selectOffer(offer: BankOffer): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Seleccionar oferta final',
          message: `¿Seguro que quieres seleccionar la oferta de ${this.bankName(offer.bankId)} (${offer.amount} €) como financiación final de esta operación? Esta acción sustituye cualquier selección anterior.`,
          confirmLabel: 'Seleccionar',
        },
        width: '420px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.bankRequestService.selectOffer(offer.id).subscribe({
          next: (finalFinancing) => {
            this.finalFinancing.set(finalFinancing);
            this.loadOffers();
          },
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }

  /** GET /api/v1/tasks is always tenant-wide (no case-scoped endpoint exists) — filtered
   * client-side to this case, same non-invented-endpoint approach as every other section here. */
  private loadTasks(): void {
    this.taskService.list().subscribe({
      next: (tasks) => this.tasks.set(tasks.filter((t) => t.caseId === this.caseId)),
      error: (err: ApiError) => {
        this.tasks.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openCreateTask(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateTaskDialogComponent, { data: { caseId: this.caseId }, width: '420px' })
      .afterClosed()
      .subscribe((result: Task | undefined) => {
        if (result) {
          this.loadTasks();
        }
      });
  }

  openEditTask(task: Task): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(EditTaskDialogComponent, { data: { task }, width: '420px' })
      .afterClosed()
      .subscribe((result: Task | undefined) => {
        if (result) {
          this.loadTasks();
        }
      });
  }

  completeTask(task: Task): void {
    this.taskService.complete(task.id).subscribe({
      next: () => this.loadTasks(),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  deleteTask(task: Task): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Eliminar tarea',
          message: `¿Seguro que quieres eliminar la tarea "${task.title}"? Esta acción no se puede deshacer.`,
          confirmLabel: 'Eliminar',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.taskService.delete(task.id).subscribe({
          next: () => this.loadTasks(),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }

  private loadConversations(): void {
    this.communicationService.listConversations(this.caseId).subscribe({
      next: (conversations) => this.conversations.set(conversations),
      error: (err: ApiError) => {
        this.conversations.set([]);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  openCreateConversation(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(CreateConversationDialogComponent, {
        data: { caseId: this.caseId, clients: this.clients() ?? [] },
        width: '420px',
      })
      .afterClosed()
      .subscribe((result: Conversation | undefined) => {
        if (result) {
          this.loadConversations();
        }
      });
  }

  openConversationDetail(conversation: Conversation): void {
    if (this.hasOpenDialog()) return;
    this.dialog.open(ConversationDetailDialogComponent, {
      data: {
        conversation,
        clients: this.clients() ?? [],
        assignableUsers: this.assignableUsers(),
      },
      width: '600px',
    });
  }

  private loadFinancialAnalysis(): void {
    this.financialAnalysisService.list(this.caseId).subscribe({
      next: (results) => this.financialAnalysisResults.set(results),
      error: (err: ApiError) => {
        this.financialAnalysisResults.set([]);
        this.financialAnalysisError.set(friendlyErrorMessage(err));
      },
    });
  }

  runFinancialAnalysis(): void {
    this.financialAnalysisRunning.set(true);
    this.financialAnalysisError.set(null);
    this.financialAnalysisService.run(this.caseId).subscribe({
      next: (results) => {
        this.financialAnalysisRunning.set(false);
        this.financialAnalysisResults.set(results);
      },
      error: (err: ApiError) => {
        this.financialAnalysisRunning.set(false);
        this.financialAnalysisError.set(friendlyErrorMessage(err));
      },
    });
  }

  financialAnalysisClientName(clientId: string): string {
    const client = this.clients()?.find((c) => c.clientId === clientId);
    return client ? `${client.firstName ?? ''} ${client.lastName ?? ''}`.trim() : clientId;
  }

  private loadCaseFee(): void {
    this.caseFeeLoading.set(true);
    this.caseFeeService.get(this.caseId).subscribe({
      next: (fee) => {
        this.caseFeeLoading.set(false);
        this.caseFee.set(fee);
      },
      error: (err: ApiError) => {
        this.caseFeeLoading.set(false);
        if (err.status !== 404) {
          this.caseFeeError.set(friendlyErrorMessage(err));
        }
      },
    });
  }

  openEditCaseFee(): void {
    if (this.hasOpenDialog()) return;
    this.dialog
      .open(EditCaseFeeDialogComponent, {
        data: { caseId: this.caseId, current: this.caseFee() },
        width: '420px',
      })
      .afterClosed()
      .subscribe((fee?: CaseFee) => {
        if (fee) {
          this.caseFee.set(fee);
          this.caseFeeError.set(null);
        }
      });
  }

  private loadContract(): void {
    this.engagementContractService.get(this.caseId).subscribe({
      next: (document) => this.contractDocument.set(document),
      error: (err: ApiError) => {
        this.contractDocument.set({ documentId: null, versions: [] });
        this.contractError.set(friendlyErrorMessage(err));
      },
    });
  }

  generateContract(): void {
    this.contractGenerating.set(true);
    this.contractError.set(null);
    this.engagementContractService.generate(this.caseId).subscribe({
      next: () => {
        this.contractGenerating.set(false);
        this.loadContract();
      },
      error: (err: ApiError) => {
        this.contractGenerating.set(false);
        this.contractError.set(friendlyErrorMessage(err));
      },
    });
  }

  downloadContractVersion(versionId: string): void {
    const documentId = this.contractDocument()?.documentId;
    if (!documentId) {
      return;
    }
    this.documentsService.downloadVersion(documentId, versionId).subscribe({
      next: (download) => window.open(download.url, '_blank'),
      error: (err: ApiError) => this.contractError.set(friendlyErrorMessage(err)),
    });
  }

  private loadDossier(): void {
    this.viabilityDossierService.get(this.caseId).subscribe({
      next: (document) => this.dossierDocument.set(document),
      error: (err: ApiError) => {
        this.dossierDocument.set({ documentId: null, versions: [] });
        this.dossierError.set(friendlyErrorMessage(err));
      },
    });
  }

  generateDossier(): void {
    this.dossierGenerating.set(true);
    this.dossierError.set(null);
    this.viabilityDossierService.generate(this.caseId).subscribe({
      next: () => {
        this.dossierGenerating.set(false);
        this.loadDossier();
      },
      error: (err: ApiError) => {
        this.dossierGenerating.set(false);
        this.dossierError.set(friendlyErrorMessage(err));
      },
    });
  }

  downloadDossierVersion(versionId: string): void {
    const documentId = this.dossierDocument()?.documentId;
    if (!documentId) {
      return;
    }
    this.documentsService.downloadVersion(documentId, versionId).subscribe({
      next: (download) => window.open(download.url, '_blank'),
      error: (err: ApiError) => this.dossierError.set(friendlyErrorMessage(err)),
    });
  }
}
