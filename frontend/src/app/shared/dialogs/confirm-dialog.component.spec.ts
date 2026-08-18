import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  function createComponent(data: ConfirmDialogData, dialogRef: Partial<MatDialogRef<ConfirmDialogComponent>>) {
    TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the given title and message', () => {
    const fixture = createComponent(
      { title: 'Quitar cliente', message: '¿Seguro que quieres quitar a Ada Lovelace?' },
      { close: vi.fn() },
    );

    expect(fixture.nativeElement.textContent).toContain('Quitar cliente');
    expect(fixture.nativeElement.textContent).toContain('¿Seguro que quieres quitar a Ada Lovelace?');
  });

  it('closes with true when confirm is called', () => {
    const close = vi.fn();
    const fixture = createComponent({ title: 't', message: 'm', confirmLabel: 'Quitar' }, { close });

    fixture.componentInstance.confirm();

    expect(close).toHaveBeenCalledWith(true);
  });

  it('closes with false when cancel is called', () => {
    const close = vi.fn();
    const fixture = createComponent({ title: 't', message: 'm' }, { close });

    fixture.componentInstance.cancel();

    expect(close).toHaveBeenCalledWith(false);
  });
});
