import { DatePipe } from '@angular/common';
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
import {
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
import { Property } from '../../property/property.model';
import { PropertyDialogComponent } from '../../property/property-dialog.component';
import { PropertyService } from '../../property/property.service';
import { AddClientDialogComponent } from '../case-dialogs/add-client-dialog.component';
import { AssignDialogComponent } from '../case-dialogs/assign-dialog.component';
import { CancelDialogComponent } from '../case-dialogs/cancel-dialog.component';
import { ChangeStatusDialogComponent } from '../case-dialogs/change-status-dialog.component';
import { ReopenDialogComponent } from '../case-dialogs/reopen-dialog.component';
import { Case, CaseAssignment, CaseClient } from '../case.model';
import { CasesService } from '../cases.service';

@Component({
  selector: 'app-case-detail',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
  ],
  templateUrl: './case-detail.component.html',
  styleUrl: './case-detail.component.scss',
})
export class CaseDetailComponent {
  private readonly casesService = inject(CasesService);
  private readonly propertyService = inject(PropertyService);
  private readonly documentsService = inject(DocumentsService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly caseId = this.route.snapshot.paramMap.get('id')!;
  readonly theCase = signal<Case | null>(null);
  readonly assignments = signal<CaseAssignment[] | null>(null);
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

  constructor() {
    this.loadCase();
    this.loadAssignments();
    this.loadClients();
    this.loadProperty();
    this.documentsService.listDocumentTypes().subscribe({
      next: (types) => this.documentTypes.set(types),
      error: (err: ApiError) => this.error.set(err.message),
    });
    this.loadDocuments();
    this.loadDocumentRequests();
  }

  private loadCase(): void {
    this.casesService.get(this.caseId).subscribe({
      next: (theCase) => this.theCase.set(theCase),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  private loadAssignments(): void {
    this.casesService.listAssignments(this.caseId).subscribe({
      next: (assignments) => this.assignments.set(assignments),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  private loadClients(): void {
    this.casesService.listClients(this.caseId).subscribe({
      next: (clients) => this.clients.set(clients),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  openChangeStatus(): void {
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
    this.casesService.removeClient(this.caseId, clientId).subscribe({
      next: () => this.loadClients(),
      error: (err: ApiError) => this.error.set(err.message),
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
          this.error.set(err.message);
        }
      },
    });
  }

  openProperty(): void {
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
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  documentTypeName(documentTypeId: string): string {
    return this.documentTypes().find((t) => t.id === documentTypeId)?.name ?? documentTypeId;
  }

  openCreateDocument(): void {
    this.dialog
      .open(CreateDocumentDialogComponent, {
        data: { caseId: this.caseId, documentTypes: this.documentTypes() },
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: CaseDocument | undefined) => {
        if (result) {
          this.loadDocuments();
        }
      });
  }

  openUploadVersion(documentId: string): void {
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
    this.dialog.open(VersionsDialogComponent, { data: { documentId }, width: '600px' });
  }

  publish(documentId: string): void {
    this.documentsService.publish(documentId).subscribe({
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  unpublish(documentId: string): void {
    this.documentsService.unpublish(documentId).subscribe({
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  download(documentId: string): void {
    this.documentsService.downloadCurrent(documentId).subscribe({
      next: (download) => window.open(download.url, '_blank'),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  private loadDocumentRequests(): void {
    this.documentsService.listRequests(this.caseId).subscribe({
      next: (requests) => this.documentRequests.set(requests),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  openCreateDocumentRequest(): void {
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
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  clientName(clientId: string | null): string {
    if (!clientId) {
      return '—';
    }
    const client = this.clients()?.find((c) => c.clientId === clientId);
    return client ? `${client.firstName} ${client.lastName}` : clientId;
  }
}
