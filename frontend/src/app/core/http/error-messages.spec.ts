import { ApiError } from './api-error';
import { friendlyErrorMessage } from './error-messages';

function apiError(overrides: Partial<ApiError>): ApiError {
  return { status: 400, code: null, message: 'raw backend message', requestId: 'r1', ...overrides };
}

describe('friendlyErrorMessage', () => {
  it('translates a known backend error code', () => {
    expect(friendlyErrorMessage(apiError({ status: 404, code: 'CASE_NOT_FOUND' }))).toBe(
      'No se ha encontrado la operación solicitada.',
    );
  });

  it('falls back to a status-based message when the code is unknown', () => {
    expect(friendlyErrorMessage(apiError({ status: 403, code: 'SOME_UNMAPPED_CODE' }))).toBe(
      'No tienes permisos para realizar esta acción.',
    );
  });

  it('falls back to a status-based message when there is no code', () => {
    expect(friendlyErrorMessage(apiError({ status: 409, code: null }))).toBe(
      'La operación no se ha podido completar por un conflicto con el estado actual.',
    );
  });

  it('uses a generic server-error message for unmapped 5xx statuses', () => {
    expect(friendlyErrorMessage(apiError({ status: 502, code: null }))).toBe(
      'Ha ocurrido un error en el servidor. Inténtalo de nuevo más tarde.',
    );
  });

  it('uses a generic fallback for any other unmapped status', () => {
    expect(friendlyErrorMessage(apiError({ status: 418, code: null }))).toBe(
      'No se ha podido completar la operación.',
    );
  });

  it('never returns the raw backend message', () => {
    const result = friendlyErrorMessage(apiError({ status: 400, code: null, message: 'Bad Request' }));
    expect(result).not.toBe('Bad Request');
  });
});
