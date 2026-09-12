#!/usr/bin/env bash
# Deploy website/ to a VPS as twigmark.com
# Usage: ./website/deploy-to-vps.sh root@HOST
set -euo pipefail
TARGET="${1:?usage: $0 root@host}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"

echo "Deploying $ROOT -> $TARGET:/var/www/twigmark"

ssh "$TARGET" 'mkdir -p /var/www/twigmark'
tar -C "$ROOT" -czf - \
  --exclude './deploy-to-vps.sh' \
  --exclude './README.md' \
  --exclude './nginx-twigmark.conf' \
  . | ssh "$TARGET" 'tar -C /var/www/twigmark -xzf -'

scp "$ROOT/nginx-twigmark.conf" "$TARGET:/etc/nginx/sites-available/twigmark"
ssh "$TARGET" 'bash -s' <<'REMOTE'
set -euo pipefail
ln -sfn /etc/nginx/sites-available/twigmark /etc/nginx/sites-enabled/twigmark
chown -R www-data:www-data /var/www/twigmark
chmod -R u=rwX,g=rX,o=rX /var/www/twigmark
nginx -t
systemctl reload nginx

if command -v certbot >/dev/null 2>&1; then
  if [[ ! -d /etc/letsencrypt/live/twigmark.com ]]; then
    certbot --nginx -d twigmark.com -d www.twigmark.com --non-interactive --agree-tos --register-unsafely-without-email --redirect || \
      certbot --nginx -d twigmark.com -d www.twigmark.com --non-interactive --agree-tos -m admin@twigmark.com --redirect || true
  else
    certbot install --cert-name twigmark.com --nginx --non-interactive 2>/dev/null || true
  fi
  nginx -t && systemctl reload nginx
fi

curl -sS -o /dev/null -w "http:%{http_code}\n" http://127.0.0.1/ -H "Host: twigmark.com" || true
ls -la /var/www/twigmark | head
REMOTE

echo "Done. Open https://twigmark.com/"
echo "Repo note: update docear_plugin_core/resources/version.properties homepage_url if desired."
