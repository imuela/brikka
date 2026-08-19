/**
 * Sprint 24 (entornos): configuración de PRODUCCIÓN del frontend.
 * Se inyecta en el build vía fileReplacements (angular.json → configurations.production).
 * La URL de la API debe venir del entorno real de despliegue, nunca de un valor hardcodeado.
 * Sustituye la URL de ejemplo por la del API de producción del orquestador al construir.
 *
 * A diferencia de environment.ts (dev, localhost), este fichero no se ejecuta en desarrollo:
 * solo se activa con la configuración de build "production".
 */
export const environment = {
  apiBaseUrl: 'https://api.brika.example.com',
};