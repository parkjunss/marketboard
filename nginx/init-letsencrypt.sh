#!/bin/sh
# One-time bootstrap for the first real Let's Encrypt certificate. Run once from the
# repo root on the deploy host (the Pi), *before* the normal CI deploy loop takes over:
#
#   ./nginx/init-letsencrypt.sh
#
# Not meant to run on every deploy — re-running the dummy-cert dance is pointless once a
# real cert exists, and repeated certbot runs risk Let's Encrypt's rate limits. After this
# script succeeds, `docker compose up -d` (what the CI deploy job runs) just reuses the
# cert already sitting in the nginx/letsencrypt volume; the certbot service's renew-loop
# keeps it current from then on.
set -e

# Same fixed path the CI deploy job uses (see .github/workflows/ci.yml) — .env lives
# outside the (ephemeral, gitignored-inside) checkout so it persists across runs.
ENV_FILE="/home/jun/marketboard/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

# Must match server_name in nginx/conf.d/marketboard.conf.
DOMAIN="marketboard.duckdns.org"
DATA_PATH="./nginx/letsencrypt"
RSA_KEY_SIZE=4096

if [ -f "$DATA_PATH/live/$DOMAIN/fullchain.pem" ]; then
  echo "Certificate for $DOMAIN already exists in $DATA_PATH — nothing to do."
  echo "(Delete $DATA_PATH/live/$DOMAIN if you really want to re-bootstrap.)"
  exit 0
fi

if [ -z "$LETSENCRYPT_EMAIL" ]; then
  echo "LETSENCRYPT_EMAIL is not set in $ENV_FILE (or the file doesn't exist) — add it there, or export it, then re-run." >&2
  exit 1
fi

COMPOSE="docker compose --env-file $ENV_FILE"

echo "### Creating a dummy certificate for $DOMAIN so nginx can start ..."
mkdir -p "$DATA_PATH/live/$DOMAIN"
$COMPOSE run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=localhost'" certbot

echo "### Starting nginx ..."
$COMPOSE up -d nginx

echo "### Deleting dummy certificate for $DOMAIN ..."
$COMPOSE run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/$DOMAIN /etc/letsencrypt/archive/$DOMAIN /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

echo "### Requesting the real certificate for $DOMAIN ..."
$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    -d $DOMAIN \
    --rsa-key-size $RSA_KEY_SIZE \
    --email $LETSENCRYPT_EMAIL \
    --agree-tos \
    --no-eff-email" certbot

echo "### Reloading nginx ..."
$COMPOSE exec nginx nginx -s reload

echo "Done — $DOMAIN should now be serving a real Let's Encrypt certificate."
