import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { statusTone } from './status-tone';

/**
 * Pill de estado reutilizable. Recibe el valor crudo del backend y el mapa de etiquetas del
 * dominio (los mismos `*_LABELS` de `shared/labels/status-labels.ts` que ya usaba `StatusLabelPipe`
 * en texto plano) y añade un tono semántico (success/warning/error/info/neutral) derivado por
 * `statusTone()`. No sustituye a `StatusLabelPipe`: lo envuelve para los casos de solo lectura
 * donde el estado debe destacar visualmente (listas y detalle), no en controles de formulario.
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="status-badge" [class]="'status-badge--' + tone()">{{ label() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadgeComponent {
  readonly value = input.required<string | null | undefined>();
  readonly labels = input<Record<string, string>>({});

  protected readonly tone = computed(() => statusTone(this.value()));
  protected readonly label = computed(() => {
    const value = this.value();
    if (!value) {
      return '—';
    }
    return this.labels()[value] ?? value;
  });
}
