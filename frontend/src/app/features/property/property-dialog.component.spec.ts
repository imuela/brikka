import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { Property } from './property.model';
import { PropertyDialogComponent } from './property-dialog.component';

const existingProperty: Property = {
  id: 'p1',
  companyId: 'co1',
  caseId: 'k1',
  address: { street: 'Calle Mayor 1', city: 'Madrid' },
  propertyType: 'FLAT',
  valuation: 250000,
  purchasePrice: 240000,
};

function configure(property: Property | null) {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [PropertyDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1', property } },
    ],
  });
  return dialogRef;
}

describe('PropertyDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('create mode: submits and closes with the returned property, dropping empty address fields', () => {
    configure(null);
    httpMock = TestBed.inject(HttpTestingController);
    const dialogRef = TestBed.inject(MatDialogRef) as unknown as { close: ReturnType<typeof vi.fn> };

    const fixture = TestBed.createComponent(PropertyDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(false);

    fixture.componentInstance.form.setValue({
      propertyType: 'FLAT',
      street: 'Gran Via',
      city: '',
      postalCode: '',
      province: '',
      valuation: '250000',
      purchasePrice: '',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      address: { street: 'Gran Via' },
      propertyType: 'FLAT',
      valuation: 250000,
      purchasePrice: null,
    });
    req.flush(existingProperty);

    expect(dialogRef.close).toHaveBeenCalledWith(existingProperty);
  });

  it('edit mode: pre-fills the form from the existing property', () => {
    configure(existingProperty);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(PropertyDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.isEditMode).toBe(true);
    expect(fixture.componentInstance.form.value.propertyType).toBe('FLAT');
    expect(fixture.componentInstance.form.value.street).toBe('Calle Mayor 1');
    expect(fixture.componentInstance.form.value.city).toBe('Madrid');
    expect(fixture.componentInstance.form.value.valuation).toBe('250000');
  });

  it('does not submit without a property type', () => {
    configure(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(PropertyDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/property`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error when the request fails', () => {
    configure(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(PropertyDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ propertyType: 'FLAT' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'VALIDATION_ERROR', message: 'Invalid property type.', requestId: 'r1' }, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.error()).toBe('No se han podido guardar los cambios. Revisa los datos introducidos.');
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(PropertyDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
