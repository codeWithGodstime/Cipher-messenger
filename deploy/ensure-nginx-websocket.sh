#!/usr/bin/env bash
# Ensure nginx forwards WebSocket upgrades to the Messenger API on :8080.
# Hop-by-hop Connection/Upgrade headers are dropped unless set explicitly.
#
# Idempotent: only installs the map + /ws snippet and includes the snippet once.
# Does not rewrite existing location / proxy blocks (avoids duplicate directives).
set -euo pipefail

MAP_CONF=/etc/nginx/conf.d/00-websocket-map.conf
WS_SNIPPET=/etc/nginx/snippets/messenger-websocket.conf

install -d /etc/nginx/conf.d /etc/nginx/snippets

cat >"$MAP_CONF" <<'EOF'
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
EOF

cat >"$WS_SNIPPET" <<'EOF'
location /ws {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}
EOF

patched=0
shopt -s nullglob

# Deduplicate paths (sites-enabled often symlinks to sites-available).
configs=()
declare -A seen=()
for conf in /etc/nginx/sites-enabled/* /etc/nginx/sites-available/* /etc/nginx/conf.d/*; do
  [[ -e "$conf" ]] || continue
  case "$conf" in
    "$MAP_CONF") continue ;;
  esac
  real=$(readlink -f "$conf" 2>/dev/null || printf '%s' "$conf")
  [[ -f "$real" ]] || continue
  [[ -n "${seen[$real]:-}" ]] && continue
  seen[$real]=1
  configs+=("$real")
done

for conf in "${configs[@]}"; do
  if ! grep -q 'messenger.publicvm.com' "$conf" 2>/dev/null; then
    continue
  fi
  if grep -q 'snippets/messenger-websocket.conf' "$conf"; then
    continue
  fi
  # Insert before the first location so prefix /ws wins over location /.
  if grep -q 'location[[:space:]]' "$conf"; then
    sed -i '0,/location[[:space:]]/{s||    include snippets/messenger-websocket.conf;\n\n    location |}' "$conf"
  else
    sed -i '/server_name[[:space:]].*messenger.publicvm.com/a\    include snippets/messenger-websocket.conf;' "$conf"
  fi
  patched=1
  echo "patched $conf for /ws websocket proxy"
done

# Ciphertext uploads are up to 32 MiB. The default nginx body limit is 1m.
cat >/etc/nginx/conf.d/messenger-upload-size.conf <<'EOF'
client_max_body_size 32m;
EOF

install -d /var/lib/messenger/media
chmod 700 /var/lib/messenger/media
install -d /etc/systemd/system/messenger-api.service.d
cat >/etc/systemd/system/messenger-api.service.d/media.conf <<'EOF'
[Service]
Environment=MEDIA_DIR=/var/lib/messenger/media
EOF
if command -v systemctl >/dev/null 2>&1; then
  systemctl daemon-reload
fi

nginx -t
systemctl reload nginx
echo "nginx websocket proxy ready (patched=$patched)"
