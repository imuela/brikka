# Sprint 0 skeleton image — infrastructure only, no business features.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Sprint 39 audit (D39-4): nginx:1.27-alpine is a floating tag — Trivy found 2 CRITICAL/33 HIGH
# CVEs (openssl, libxml2, musl, zlib, nghttp2, libpng, libexpat, curl...) in its baked-in Alpine
# 3.21.3 packages, every one of them already patched in the same v3.21 repo this image already
# points to (verified via `apk policy`, same pattern as the backend's D39-3). No base-image swap
# needed — just pulling the already-published, same-major-version fixes before dropping privileges.
RUN apk update && apk upgrade --no-cache
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
# Sprint 39 audit (D39-2): Sprint 38 left this image running as root, reasoning that nginx's
# master process needs root only to bind port 80 and that leaving it unprivileged would need
# structural changes (remapping the port). Re-investigated empirically this sprint: granting the
# nginx binary CAP_NET_BIND_SERVICE lets it bind port 80 without root at all — verified by running
# the built image and confirming both the master AND worker processes report `USER nginx` (not
# root) via `ps aux`, plus a real HTTP 200 from the running container. No port remapping, no
# docker-compose/docs changes needed.
RUN apk add --no-cache libcap \
    && setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx \
    && chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /run /etc/nginx/conf.d \
    && sed -i '/^user /d' /etc/nginx/nginx.conf
USER nginx
EXPOSE 80
