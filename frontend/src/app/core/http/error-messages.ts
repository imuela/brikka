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
  DOCUMENT_NOT_FOUND: 'No se ha encontrado el documento solicitado.',
  DOCUMENT_REQUEST_NOT_FOUND: 'No se ha encontrado la solicitud de documento.',
  DOCUMENT_REQUIREMENT_NOT_FOUND: 'No se ha encontrado el requisito de documentación.',
  DOCUMENT_VERSION_NOT_FOUND: 'No se ha encontrado la versión del documento.',
  EMPTY_FILE: 'El fichero seleccionado está vacío.',
  FILE_TOO_LARGE: 'El fichero supera el tamaño máximo permitido.',
  FORBIDDEN: 'No tienes permisos para realizar esta acción.',
  INVALID_ADDRESS: 'La dirección introducida no es válida.',
  INVALID_CANCELLATION_REASON: 'El motivo de cancelación no es válido.',
  INVALID_CONDITIONS: 'Las condiciones introducidas no son válidas.',
  INVALID_PARTICIPATION_TYPE: 'El tipo de participación no es válido.',
  INVALID_REVIEW_DECISION: 'La decisión de revisión no es válida.',
  INVALID_STATUS: 'El estado indicado no es válido.',
  INVALID_TARGET_STATUS: 'El estado destino no es válido.',
  INVALID_TRANSITION: 'No es posible realizar ese cambio de estado en este momento.',
  MISSING_CLIENT: 'La operación necesita al menos un cliente asociado para continuar.',
  NO_VERSION_TO_PUBLISH: 'El documento no tiene ninguna versión que publicar.',
  NO_VERSION_TO_REVIEW: 'El documento no tiene ninguna versión pendiente de revisión.',
  PROPERTY_NOT_FOUND: 'No se ha encontrado ningún inmueble registrado para esta operación.',
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
