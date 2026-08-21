import { ApiError } from './api-error';

/**
 * Auditoría UX/i18n pre-Sprint 16: el backend responde en inglés (05_API_SPECIFICATION.md §5,
 * ErrorResponse.message) y algunos mensajes son puramente técnicos ("Bad Request", "Http failure
 * response for ..."). Esta es la única fuente de verdad para traducir errores de API a un mensaje
 * en español comprensible para el usuario final. El código/mensaje original del backend nunca se
 * descarta a nivel de red — sigue disponible en la consola/DevTools para depuración — solo se
 * sustituye lo que se muestra en la interfaz.
 *
 * Prioridad: código de error conocido > mensaje genérico según el status HTTP.
 */
const ERROR_CODE_MESSAGES: Record<string, string> = {
  CASE_NOT_FOUND: 'No se ha encontrado la operación solicitada.',
  CASE_NOT_TERMINAL: 'La operación debe estar finalizada para realizar esta acción.',
  CASE_TERMINAL: 'No es posible modificar una operación que ya ha finalizado.',
  CASE_CLIENT_NOT_FOUND: 'El cliente indicado no está asociado a esta operación.',
  CLIENT_NOT_FOUND: 'No se ha encontrado el cliente solicitado.',
  CLIENT_ALREADY_LINKED: 'Este cliente ya está asociado a la operación.',
  COMPANY_NOT_FOUND: 'No se ha encontrado la empresa solicitada.',
  COMPANY_NOT_ACTIVE: 'Solo se puede suspender una empresa activa.',
  COMPANY_ALREADY_DELETED: 'La empresa ya ha sido eliminada.',
  DOCUMENT_NOT_FOUND: 'No se ha encontrado el documento solicitado.',
  DOCUMENT_REQUEST_NOT_FOUND: 'No se ha encontrado la solicitud de documento.',
  DOCUMENT_REQUIREMENT_NOT_FOUND: 'No se ha encontrado el requisito de documentación.',
  DOCUMENT_VERSION_NOT_FOUND: 'No se ha encontrado la versión del documento.',
  EMPTY_FILE: 'El fichero seleccionado está vacío.',
  EVIDENCE_DOCUMENT_VERSION_NOT_FOUND: 'El documento de evidencia indicado no existe.',
  FINANCIAL_PROFILE_NOT_FOUND: 'Este cliente todavía no tiene un perfil financiero registrado.',
  FINANCIAL_PROFILE_REQUIRED:
    'Todos los clientes del caso necesitan un perfil financiero antes de ejecutar el análisis.',
  FINANCING_DATA_REQUIRED:
    'El caso necesita una oferta bancaria seleccionada o una simulación (capital, tipo de interés y plazo) antes de poder ejecutar el análisis.',
  MONTHLY_INCOME_REQUIRED: 'Falta el dato de ingresos mensuales en el perfil financiero del cliente.',
  MONTHLY_INCOME_INVALID: 'Los ingresos mensuales del cliente no son válidos.',
  NO_CLIENTS_ON_CASE: 'El caso no tiene ningún cliente asociado todavía.',
  INVALID_FINANCIAL_PROFILE_SOURCE: 'La fuente del dato no es válida.',
  INVALID_FINANCIAL_PROFILE_STATUS: 'El estado de verificación no es válido.',
  NEGATIVE_FINANCIAL_VALUE: 'Los importes y cantidades no pueden ser negativos.',
  FILE_TOO_LARGE: 'El fichero supera el tamaño máximo permitido.',
  FORBIDDEN: 'No tienes permisos para realizar esta acción.',
  INVALID_ADDRESS: 'La dirección introducida no es válida.',
  INVALID_CANCELLATION_REASON: 'El motivo de cancelación no es válido.',
  INVALID_CONDITIONS: 'Las condiciones introducidas no son válidas.',
  INVALID_PARTICIPATION_TYPE: 'El tipo de participación no es válido.',
  INVALID_REVIEW_DECISION: 'La decisión de revisión no es válida.',
  INVALID_ROLE: 'El rol indicado no es válido.',
  INVALID_ROLE_ASSIGNMENT: 'No es posible asignar ese rol en esta operación.',
  INVALID_STATUS: 'El estado indicado no es válido.',
  INVALID_TARGET_STATUS: 'El estado destino no es válido.',
  INVALID_TRANSITION: 'No es posible realizar ese cambio de estado en este momento.',
  MISSING_CLIENT: 'La operación necesita al menos un cliente asociado para continuar.',
  NO_VERSION_TO_PUBLISH: 'El documento no tiene ninguna versión que publicar.',
  NO_VERSION_TO_REVIEW: 'El documento no tiene ninguna versión pendiente de revisión.',
  PLAN_NOT_FOUND: 'No se ha encontrado el plan solicitado.',
  PROPERTY_NOT_FOUND: 'No se ha encontrado ningún inmueble registrado para esta operación.',
  SUBSCRIPTION_NOT_FOUND: 'La empresa no tiene ninguna suscripción asignada.',
  // Sprint 22 (autenticación propia): UNAUTHENTICATED cubre login/refresh/password-reset con
  // credenciales o token inválidos — nunca "tu sesión ha caducado", que es el fallback genérico
  // de status 401 para peticiones ya autenticadas (ver STATUS_FALLBACK_MESSAGES más abajo).
  UNAUTHENTICATED: 'Email o contraseña incorrectos.',
  TOO_MANY_ATTEMPTS: 'Demasiados intentos. Inténtalo de nuevo en unos minutos.',
  UNSUPPORTED_MIME_TYPE: 'El tipo de fichero no está permitido.',
  UPLOAD_FAILED: 'No se ha podido subir el fichero.',
  USER_NOT_FOUND: 'No se ha encontrado el usuario solicitado.',
  USE_CANCEL_ENDPOINT: 'Utiliza la acción de cancelar para realizar este cambio.',
  INTERNAL_ERROR: 'Ha ocurrido un error inesperado.',
};

const STATUS_FALLBACK_MESSAGES: Record<number, string> = {
  400: 'No se han podido guardar los cambios. Revisa los datos introducidos.',
  401: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
  403: 'No tienes permisos para realizar esta acción.',
  404: 'No se ha encontrado el recurso solicitado.',
  409: 'La operación no se ha podido completar por un conflicto con el estado actual.',
  413: 'El fichero supera el tamaño máximo permitido.',
};

/** Translates a backend ApiError into a Spanish message suitable for display. Never returns the
 * raw backend text (English, or a technical HTTP status name) when a better alternative exists. */
export function friendlyErrorMessage(error: ApiError): string {
  if (error.code && ERROR_CODE_MESSAGES[error.code]) {
    return ERROR_CODE_MESSAGES[error.code];
  }
  if (STATUS_FALLBACK_MESSAGES[error.status]) {
    return STATUS_FALLBACK_MESSAGES[error.status];
  }
  if (error.status >= 500) {
    return 'Ha ocurrido un error en el servidor. Inténtalo de nuevo más tarde.';
  }
  return 'No se ha podido completar la operación.';
}
