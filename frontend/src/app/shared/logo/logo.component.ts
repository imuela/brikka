import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Recursos reales de marca (docs/branding/assets/, fotografías del logotipo oficial — no
 * generados ni redibujados). No existe versión vectorial: son fotografías JPG recortadas a su
 * bounding box real, sin alterar el arte.
 *
 * variant:
 *  - 'full'     lockup completo (símbolo + wordmark "BRIKKA"). Espacio horizontal suficiente
 *               (login, header, sidebar expandido).
 *  - 'mark'     solo el símbolo/isotipo B, sin wordmark. Espacios reducidos.
 *  - 'wordmark' solo el texto "BRIKKA", sin símbolo. Sobre fondo claro únicamente (no existe
 *               versión del wordmark aislado para fondo oscuro entre los recursos reales).
 *
 * theme (aplica a variant="full"; 'mark'/'wordmark' solo tienen versión clara):
 *  - 'light'  fondo claro (blanco / neutral-50) -> brikka-logo-full-light.jpg (wordmark navy).
 *  - 'dark'   fondo navy (#050C1F, el propio sidebar) -> brikka-logo-full-dark.jpg (wordmark
 *             blanco) — el fondo de la fotografía coincide con --brikka-navy.
 *  - 'blue'   fondo azul de marca (#2759E0) -> brikka-logo-full-blue.jpg — el fondo de la
 *             fotografía coincide con --brikka-blue. Sin caso de uso actual en la app (no hay
 *             superficies sólidas en azul), se deja disponible para cuando lo haya.
 */
export type LogoVariant = 'full' | 'mark' | 'wordmark';
export type LogoTheme = 'light' | 'dark' | 'blue';

const SOURCES: Record<string, string> = {
  'full-light': 'branding/brikka-logo-full-light.jpg',
  'full-dark': 'branding/brikka-logo-full-dark.jpg',
  'full-blue': 'branding/brikka-logo-full-blue.jpg',
  mark: 'branding/brikka-mark-light.jpg',
  wordmark: 'branding/brikka-wordmark-light.jpg',
};

@Component({
  selector: 'app-logo',
  standalone: true,
  template: `<img [src]="src()" [alt]="alt()" [style.height.px]="heightPx()" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoComponent {
  readonly variant = input<LogoVariant>('full');
  readonly theme = input<LogoTheme>('light');
  readonly heightPx = input<number>(28);

  protected readonly alt = computed(() => 'Brikka');

  protected readonly src = computed(() => {
    const variant = this.variant();
    if (variant === 'full') {
      return SOURCES[`full-${this.theme()}`];
    }
    return SOURCES[variant];
  });
}
