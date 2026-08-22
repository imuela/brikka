export type StatusTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

/**
 * Deriva el tono visual de un badge a partir del propio código de estado, por patrones léxicos
 * comunes a los ~15 catálogos de estado de la aplicación (CaseStatus, ReviewStatus, TaskStatus,
 * BankRequestStatus, UserStatus, CompanyStatus, SubscriptionStatus...). No es una regla de
 * negocio: es una heurística puramente presentacional, la misma para cualquier catálogo, que no
 * necesita mantenerse por-dominio y por tanto no puede desincronizarse de él. Si un valor no
 * encaja en ningún patrón, se muestra en tono neutral en lugar de adivinar.
 */
export function statusTone(value: string | null | undefined): StatusTone {
  if (!value) {
    return 'neutral';
  }
  const v = value.toUpperCase();

  if (
    /(REJECTED|CANCELLED|CANCELED|ERROR|FAILED|OVERDUE|EXPIRED|DECLINED|SUSPENDED|BLOCKED|LOST|NO_VIABLE)/.test(
      v,
    )
  ) {
    return 'error';
  }
  if (
    /(PENDING|IN_REVIEW|IN_PROGRESS|DRAFT|REQUESTED|SUBMITTED|WAITING|SCHEDULED|TRIAL|REVISAR)/.test(
      v,
    )
  ) {
    return 'warning';
  }
  if (
    /(APPROVED|ACTIVE|COMPLETED|ACCEPTED|CLOSED_WON|WON|PAID|VERIFIED|CONFIRMED|ENABLED|FAVORABLE|AGREED)/.test(
      v,
    )
  ) {
    return 'success';
  }
  if (/(NEW|OPEN|SENT|CREATED|ASSIGNED|INFO)/.test(v)) {
    return 'info';
  }
  return 'neutral';
}
