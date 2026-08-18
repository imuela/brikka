import { Pipe, PipeTransform } from '@angular/core';

/** Looks up a raw enum value in a label map (see shared/labels/status-labels.ts). Falls back to
 * the raw value itself when the map has no entry, and to '—' for null/undefined — never renders
 * "null"/"undefined" directly. */
@Pipe({ name: 'statusLabel', standalone: true })
export class StatusLabelPipe implements PipeTransform {
  transform(value: string | null | undefined, labels: Record<string, string>): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return labels[value] ?? value;
  }
}
