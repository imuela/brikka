#!/usr/bin/env bash
#
# BRIKA — Sprint 24: genera las claves RSA (PKCS8 DER en base64) para el emisor JWT propio.
# Produce, para cada plano (internal y portal), un fichero de texto con la clave PRIVADA base64
# y otro con la PÚBLICA (para verificación/exposición de /.well-known si se desea).
#
# USO:
#   ./scripts/generate-jwt-keys.sh [directorio_salida]
#   (por defecto: .secrets/jwt/ — ignorado por Git)
#
# Las claves se introducen como:
#   SELF_AUTH_INTERNAL_SIGNING_KEY_PEM="$(cat .secrets/jwt/internal.private.key)"
#   SELF_AUTH_PORTAL_SIGNING_KEY_PEM="$(cat .secrets/jwt/portal.private.key)"
# El backend lee base64 PKCS8 DER (no PEM completo) — ver SelfIssuedTokenKeys.decode().
#
# REGLA DE SEGURIDAD (Sprint 24, gate de secretos):
#   - NUNCA commitees estos ficheros. .secrets/ está en .gitignore.
#   - En PROD las claves vienen de variables de entorno del orquestador, jamás del repo.
#   - Rotar = regenerar + re-emitir tokens; documentar el plan de rotación en 10_DEVOPS.md.

set -euo pipefail

OUT_DIR="${1:-.secrets/jwt}"
mkdir -p "$OUT_DIR"

gen() {
  local label="$1"
  local private_der
  local public_pem
  private_der="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -outform DER 2>/dev/null | base64 | tr -d '\n')"
  # Clave pública en PEM (útil para exponerla/exponer en /.well-known si se desea).
  public_pem="$(openssl pkey -inform DER -in <(printf '%s' "$private_der" | base64 -d) \
    -pubout 2>/dev/null | base64 | tr -d '\n')"
  printf '%s' "$private_der" > "$OUT_DIR/$label.private.key"
  printf '%s' "$public_pem" > "$OUT_DIR/$label.public.key"
  chmod 600 "$OUT_DIR/$label.private.key"
  printf '  %-8s -> %s/%s.private.key (%d bytes)\n' "$label" "$OUT_DIR" "$label" "${#private_der}"
}

echo "Generando claves JWT RSA (2048-bit, PKCS8 DER base64) en $OUT_DIR ..."
gen internal
gen portal
echo "Listo. Copia los valores a tu entorno/gestor de secretos; NO los subas a Git."