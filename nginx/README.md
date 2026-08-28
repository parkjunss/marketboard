# Public HTTPS routing (external to this repo)

`marketboard.duckdns.org` is no longer fronted by a container from this
project's `docker-compose.yml`. The Pi (`rasp4`) runs several projects that
each want host ports 80/443, so one shared nginx (currently `findanswer-nginx`,
a container from a different project) owns those ports for all of them.
`backend` and `frontend` still publish to the host (`8081`, `3100`) exactly
as before — the shared nginx just proxies to those host ports instead of
container DNS names.

## One-time setup on the shared nginx side

1. **Reach the host from inside that container.** Add to the shared nginx's
   own compose service:
   ```yaml
   extra_hosts:
     - "host.docker.internal:host-gateway"
   ```
   (Requires Docker 20.10+; recreate that one container after adding it.)

2. **Add a server block** for this domain (adapt paths/cert location to
   wherever that nginx keeps its `conf.d` and its Let's Encrypt volume):
   ```nginx
   server {
       listen 80;
       server_name marketboard.duckdns.org;

       location /.well-known/acme-challenge/ {
           root /var/www/certbot;  # whatever webroot that certbot already uses
       }

       location / {
           return 301 https://$host$request_uri;
       }
   }

   server {
       listen 443 ssl;
       http2 on;
       server_name marketboard.duckdns.org;

       ssl_certificate     /etc/letsencrypt/live/marketboard.duckdns.org/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/marketboard.duckdns.org/privkey.pem;
       ssl_protocols TLSv1.2 TLSv1.3;

       location /api/ {
           proxy_pass http://host.docker.internal:8081;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }

       location /ws {
           proxy_pass http://host.docker.internal:8081;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_read_timeout 3600s;
       }

       location / {
           proxy_pass http://host.docker.internal:3100;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```

3. **Get a certificate for this domain into that nginx's certbot volume**,
   using whatever certbot container/webroot that project already has running
   (so the domain joins its existing renewal loop instead of needing a
   separate one):
   ```
   certbot certonly --webroot -w <that project's webroot path> -d marketboard.duckdns.org
   ```

4. **Keep DNS pointed at the Pi's current public IP.** This repo no longer
   runs a DuckDNS updater. Either keep a small standalone `linuxserver/duckdns`
   container running for this subdomain (it doesn't bind any host port, so it
   doesn't conflict with anything), or fold `marketboard` into whatever DNS
   update mechanism the shared-nginx host already uses.

## Why this isn't automated in `ci.yml`

The shared nginx belongs to a different project on the Pi, outside this
repo's checkout — the CI deploy job only has `docker compose` reach into
this project's own stack. Steps 1–4 above are a one-time manual setup; after
that, `backend`/`frontend` publishing to `8081`/`3100` is all the shared
nginx needs, and every normal `main` push deploys through it without further
changes on that side.
