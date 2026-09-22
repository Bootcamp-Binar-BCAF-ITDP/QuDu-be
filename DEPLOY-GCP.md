# Deploy QuDu-be di Google Cloud dengan Docker

Dokumen ini untuk deployment **berbasis Docker** di VM Google Compute Engine
`zefanyadelvin@34.171.0.24`.

Ini **bukan** dokumen yang sama dengan [DEPLOY.md](DEPLOY.md). Berkas itu untuk
server AWS EC2 (`api.profilku.site`), yang sejak 19 September 2026 juga memakai
Docker, tapi lewat `docker-compose.aws.yml` + `scripts/aws-deploy.sh` dan
nginx di host. Berkas `*.gcp.*` dan `*.aws.*` jangan dipertukarkan.

Semua perintah di bawah dijalankan **di VM**, kecuali disebutkan lain.

> **Status verifikasi.** Image ini sudah di-build dan dijalankan di laptop pada
> 18 September 2026 lewat `bash scripts/docker-local.sh up`: build multi-stage
> berhasil (image 728 MB), aplikasi start dalam ±8 detik dengan profil `docker`,
> Hibernate membuat skema di PostgreSQL 18 yang kosong, container `healthy`, dan
> `GET /api/plafonds/catalog` menjawab 200. Proses berjalan sebagai uid 1001 dan
> bisa menulis ke volume `/app/uploads`. Yang **belum** diuji: berjalan di VM GCP
> itu sendiri, dengan database yang Anda siapkan di sana.

## Uji lokal sebelum deploy

`docker-compose.local.yml` menambahkan PostgreSQL sendiri (`qudu-be-db`) di atas
compose utama. Hanya untuk laptop — di server tidak dipakai.

```bash
bash scripts/docker-local.sh up       # build, start db + app, tunggu healthy, smoke test
bash scripts/docker-local.sh status
bash scripts/docker-local.sh logs
bash scripts/docker-local.sh psql     # psql di container database
bash scripts/docker-local.sh down     # stop, data tetap
bash scripts/docker-local.sh reset    # stop dan HAPUS volume database + upload lokal
```

Run pertama membuat `.env.local` (gitignored) dengan password DB dan JWT secret
acak. Database lokal bisa dibuka dari DBeaver di `localhost:5433` — bukan 5432,
yang sudah dipakai PostgreSQL native di laptop.

Warning `constraint "…" does not exist, skipping` saat start pertama itu normal:
Hibernate mencoba melepas constraint lama di skema yang baru saja dibuat.

---

## Deploy otomatis lewat GitHub Actions (cara utama)

Setelah persiapan satu kali di bawah, cukup **push ke `main`**.
[`.github/workflows/deploy-gcp.yml`](.github/workflows/deploy-gcp.yml) lalu:

1. menjalankan test (`./mvnw verify`, termasuk gerbang coverage);
2. membangun image di runner GitHub dan mem-push ke
   `ghcr.io/bootcamp-binar-bcaf-itdp/qudu-be:<12 karakter SHA>` — VM tidak
   pernah mem-build dan tidak perlu clone repo;
3. merakit `.env` dari GitHub Secrets/Variables, lalu mengunggahnya bersama
   `docker-compose.yml`, `docker-compose.gcp.yml`, dan
   [`scripts/gcp-deploy.sh`](scripts/gcp-deploy.sh) ke `/opt/qudu-be` di VM;
4. `gcp-deploy.sh` di VM: memasang Docker kalau belum ada, menarik image,
   menyalakan PostgreSQL 18 (container `qudu-be-db`, volume `qudu-be_pgdata`),
   **me-restore dump sekali saja**, menukar container aplikasi, menunggu
   `healthy`, dan rollback ke image sebelumnya kalau gagal;
5. memeriksa `https://api-gcp.profilku.site/api/plafonds/catalog` dari
   internet — lihat *Akses publik: domain sendiri di belakang nginx*.

Workflow ini **terpisah** dari `deploy.yml` (EC2). Selama keduanya aktif, satu
push ke `main` men-deploy ke dua server. Untuk mematikan yang EC2, hapus blok
`push:` di `deploy.yml` sehingga tinggal `workflow_dispatch`.

### Dump berjalan sekali saja — bagaimana itu dijamin

Keputusannya diambil database sendiri, bukan berkas penanda: dump di-restore
**hanya kalau tabel `public.users` belum ada**. Setelah restore pertama berhasil,
cabang itu tidak akan pernah diambil lagi, dan berkas dump di VM dihapus (isinya
NIK nasabah dan hash password; aslinya tetap di laptop).

- Restore memakai `--single-transaction`: gagal di tengah berarti database tetap
  kosong, dan deploy berikutnya mencoba lagi.
- `qudu2-dump.sql` **bukan SQL teks** — formatnya `pg_dump -Fc` (header
  `PGDMP`) dari PostgreSQL 18.4. Skrip mendeteksinya dan memakai `pg_restore
  --no-owner --no-acl` tanpa `-C` (dump itu menyebut locale Windows
  `English_United States.1252` yang tidak ada di Linux). Dump SQL teks biasa juga
  diterima dan dijalankan lewat `psql`.
- Database kosong **tanpa** dump membuat deploy berhenti dengan pesan jelas,
  karena sekali Hibernate (`ddl-auto=update`) membuat tabel kosong, dump tidak
  bisa lagi di-restore otomatis. Set variable `ALLOW_EMPTY_DB=true` kalau memang
  sengaja mulai kosong.
- Mengulang seed dari nol berarti menghapus seluruh data:
  `docker compose -f docker-compose.yml -f docker-compose.gcp.yml down` lalu
  `docker volume rm qudu-be_pgdata`, unggah dump lagi, jalankan ulang workflow.
  Itu keputusan manual, tidak pernah dilakukan workflow.

### Persiapan satu kali

**a. Firewall GCP** (VPC network → Firewall), ingress:

| Port | Sumber | Untuk apa |
|---|---|---|
| 22 | `0.0.0.0/0` | runner GitHub tidak punya IP tetap |
| 80 | `0.0.0.0/0` | nginx, dan verifikasi certbot |
| 443 | `0.0.0.0/0` | HTTPS |

Port 5432 dan 8080 tidak perlu dibuka. PostgreSQL hanya terikat ke
`127.0.0.1` di VM.

Aturan bawaan GCP `default-allow-http` / `default-allow-https` **hanya berlaku
untuk VM ber-tag `http-server` / `https-server`**. Tanpa tag itu port 80 tetap
tertutup walau aturannya terlihat ada. Centang *Allow HTTP traffic* dan *Allow
HTTPS traffic* di VM → Edit, atau buat aturan dengan target *All instances*.
Gejalanya: deploy sehat di dalam VM, tetapi langkah "Buktikan dari internet
publik" gagal dengan kode `000`.

**b. Kunci SSH khusus CI** — di VM:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C github-actions-qudu
cat ~/.ssh/github_deploy.pub >> ~/.ssh/authorized_keys
cat ~/.ssh/github_deploy          # isi secret GCP_SSH_KEY, utuh BEGIN..END
sudo -n true && echo "sudo tanpa password: OK"
```

Workflow memakai `sudo -n` untuk memasang Docker dan membuat `/opt/qudu-be`.
Kalau VM memakai **OS Login**, `authorized_keys` diabaikan — daftarkan kunci
publiknya lewat metadata atau `gcloud compute os-login ssh-keys add`.

Di laptop, ambil sidik jari host untuk `GCP_KNOWN_HOSTS`:

```powershell
ssh-keyscan -t ed25519,rsa,ecdsa 34.171.0.24
```

**c. Unggah dump sekali** — dari laptop:

```powershell
scp "C:\Users\Lenovo\Documents\Code Testing\Test3\qudu2-dump.sql" zefanyadelvin@34.171.0.24:~/
```

Skrip mencarinya di `~/qudu2-dump.sql`. Jangan pernah meng-commit dump ke repo;
`.gitignore` sudah menolak `*.dump` dan `*-dump.sql`.

**d. GitHub** → repo → Settings → Secrets and variables → Actions.

*Repository secrets* (wajib):

| Secret | Isi |
|---|---|
| `GCP_HOST` | `34.171.0.24` |
| `GCP_USER` | `zefanyadelvin` |
| `GCP_SSH_KEY` | kunci privat `~/.ssh/github_deploy`, format OpenSSH |
| `GCP_KNOWN_HOSTS` | keluaran `ssh-keyscan` di atas |
| `DB_USERNAME` | user PostgreSQL baru, mis. `qudu` |
| `DB_PASSWORD` | password kuat baru |
| `JWT_SECRET` | `openssl rand -hex 32` — jangan memakai ulang nilai lama |
| `MAIL_USERNAME` | alamat Gmail pengirim |
| `MAIL_PASSWORD` | Gmail app password — **buat yang baru**; yang lama sudah ada di riwayat git |
| `FIREBASE_CREDENTIALS_JSON` | *opsional*, isi berkas service-account. Kosong = push notification mati |

Nilai tidak boleh berisi kutip tunggal `'` atau baris baru (kecuali JSON
Firebase); workflow menolaknya dengan pesan jelas.

*Repository variables* (opsional, ada default):

| Variable | Default |
|---|---|
| `CORS_ALLOWED_ORIGIN` | `https://quick-duit.profilku.site` |
| `FRONTEND_RESET_PASSWORD_URL` | `https://quick-duit.profilku.site/reset-password/{token}` |
| `APP_PORT` | `127.0.0.1:8080` (default). Jangan diisi `80`: nginx yang memakai port itu |
| `PUBLIC_BASE_URL` | `https://api-gcp.profilku.site` (default) |
| `JWT_TTL_MINUTES` / `REFRESH_TTL_DAYS` | `30` / `7` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_FROM` | `smtp.gmail.com` / `587` / = `MAIL_USERNAME` |
| `API_DOCS_ENABLED` | `true` |
| `ALLOW_EMPTY_DB` | `false` |

**e. Dokumen upload** tidak ada di dump — hanya path-nya. Salin folder
`uploads/` ke volume `qudu-be_uploads` seperti di §7.

### Hal yang perlu diketahui

- **`.env` di VM ditimpa setiap deploy.** Ubah konfigurasi di GitHub, bukan di VM.
- **`DB_PASSWORD` hanya dibaca saat volume database pertama kali dibuat.**
  Menggantinya di GitHub kemudian membuat aplikasi gagal login ke database.
  Ganti di database dulu
  (`docker exec -it qudu-be-db psql -U <user> -d qudu2 -c "ALTER USER <user> PASSWORD '...'"`),
  baru di GitHub.
- **Rollback:** Actions → Deploy GCP → Run workflow → isi `image_tag` dengan 12
  karakter awal SHA commit yang pernah sukses. Test dan build dilewati.
- **Paket GHCR** dibuat otomatis oleh push pertama, sebagai paket privat milik
  org yang tertaut ke repo ini. Kalau push pertama ditolak (`denied`), izinkan
  pembuatan paket di pengaturan org, bagian *Packages*.
- VM hanya menyimpan image yang sedang jalan dan satu sebelumnya.

### Akses publik: domain sendiri di belakang nginx

Sejak 22 September 2026 VM ini dijangkau lewat **`https://api-gcp.profilku.site`**
(A record ke `34.171.0.24`, TTL 300). Cloudflare quick tunnel sudah dihapus:
alamatnya acak dan berganti setiap container tunnel restart, jadi tidak bisa
dipakai frontend Vercel maupun klien Android.

Bentuknya sama seperti server EC2:

```
internet ──► nginx + certbot (host, :80 dan :443)
                │ proxy_pass http://127.0.0.1:8080
                ▼
         container qudu-be   (APP_PORT=127.0.0.1:8080)
```

`APP_PORT` **harus** `127.0.0.1:8080`, bukan `80`. Kalau container tetap
memegang port 80, nginx tidak bisa mengikatnya dan gagal start.

#### Persiapan satu kali

Urutannya penting. Aplikasi baru pindah dari port 80 saat deploy, jadi nginx
dipasang lebih dulu tetapi baru dinyalakan setelah port 80 bebas.

**1. Firewall GCP** — buka `443` (ingress, `0.0.0.0/0`). Port `80` tetap perlu
terbuka: certbot memakainya untuk verifikasi dan perpanjangan otomatis.

**2. Di VM — pasang nginx dan certbot:**

```bash
sudo apt-get update
sudo apt-get install -y nginx certbot python3-certbot-nginx
sudo systemctl stop nginx      # port 80 masih dipegang container
```

**3. Di VM — tulis konfigurasi situs:**

```bash
sudo tee /etc/nginx/sites-available/qudu-gcp > /dev/null <<'NGINX'
server {
    listen 80 default_server;
    server_name api-gcp.profilku.site;

    client_max_body_size 10M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX

sudo ln -sf /etc/nginx/sites-available/qudu-gcp /etc/nginx/sites-enabled/qudu-gcp
sudo rm -f /etc/nginx/sites-enabled/default
```

**4. Di GitHub** — Settings → Secrets and variables → Actions → *Variables*:
pastikan `APP_PORT` **tidak** bernilai `80`. Hapus variabelnya (defaultnya kini
`127.0.0.1:8080`) atau isi `127.0.0.1:8080`.

**5. Push ke `main`.** Deploy memindahkan aplikasi ke `127.0.0.1:8080`, sehingga
port 80 kosong. Langkah *Buktikan dari internet publik* akan **gagal** di sini —
itu wajar, nginx belum menyala.

**6. Di VM — nyalakan nginx, lalu ambil sertifikat:**

```bash
sudo nginx -t && sudo systemctl enable --now nginx
curl -i http://127.0.0.1:8080/api/plafonds/catalog     # aplikasi
curl -i http://api-gcp.profilku.site/api/plafonds/catalog   # lewat nginx

sudo certbot --nginx -d api-gcp.profilku.site --agree-tos --no-redirect -m <email-anda>
```

`--no-redirect` disengaja, sama seperti di EC2: redirect 301 mengubah POST milik
klien Android menjadi GET, dan kegagalannya sulit dilacak.

**7. Verifikasi, lalu jalankan ulang workflow** (Actions → *Deploy GCP* →
*Re-run jobs*) supaya langkah pemeriksaan publik lewat:

```bash
curl -i https://api-gcp.profilku.site/api/plafonds/catalog
sudo certbot renew --dry-run
```

#### Setelah HTTPS hidup

- Arahkan `environment.ts` frontend dan `BASE_URL` Android ke
  `https://api-gcp.profilku.site/` bila server ini yang dipakai.
- Isi variable `CORS_ALLOWED_ORIGIN` sesuai origin frontend yang memanggilnya.
- Sertifikat diperpanjang otomatis oleh timer `certbot.timer`; periksa dengan
  `systemctl list-timers | grep certbot`.

Bagian di bawah adalah jalur **manual** — untuk memahami apa yang dilakukan
workflow, atau untuk memperbaiki VM dengan tangan.

---
## Bentuk yang akan Anda bangun

```
        internet
            │
     ┌──────┴──────┐
     │    nginx    │   api-gcp.profilku.site, TLS oleh certbot
     └──────┬──────┘
            │ 127.0.0.1:8080
     ┌──────┴──────────────┐
     │ container qudu-be   │  docker compose
     │  /app/uploads  ─────┼──► named volume "qudu-be_uploads"
     └──────┬──────────────┘
            │ DB_URL
     ┌──────┴──────┐
     │ PostgreSQL  │   TIDAK termasuk dalam compose ini
     └─────────────┘
```

**`docker-compose.yml` hanya berisi aplikasi backend.** Databasenya harus sudah
ada dan bisa dijangkau dari dalam container — entah di VM yang sama di luar
Docker, di Cloud SQL, atau sebagai container terpisah yang Anda jalankan sendiri.

---

## 1. VM

Sediakan RAM minimal **2 GB**. Image dibangun dengan Maven di dalam Docker, dan
proses itu sendiri butuh sekitar 1,5 GB. Di mesin 1 GB build akan dimatikan di
tengah jalan tanpa pesan yang berguna.

Buka port yang memang Anda layani, di **VPC network → Firewall**:

| Port | Sumber | Untuk apa |
|---|---|---|
| 22 | IP Anda, atau `0.0.0.0/0` kalau CI ikut deploy | SSH |
| 80 | `0.0.0.0/0` | HTTP, sekaligus pengecekan certbot |
| 443 | `0.0.0.0/0` | HTTPS |

**Jangan buka 8080.** Compose menerbitkan port itu supaya nginx bisa
menjangkaunya; yang menghadap internet seharusnya nginx, bukan container.

---

## 2. Docker

```bash
sudo bash scripts/install-docker-debian.sh --user zefanyadelvin
```

Jalankan dengan `bash`, bukan `sh` — `sh` di Debian adalah dash dan skrip ini
memakai konstruksi bash. Setelah selesai, logout lalu login lagi supaya shell
Anda mengambil keanggotaan grup `docker`. Pastikan:

```bash
docker run --rm hello-world
docker compose version
```

---

## 3. Kode

```bash
sudo mkdir -p /opt/qudu-be && sudo chown "$USER" /opt/qudu-be
git clone https://github.com/Bootcamp-Binar-BCAF-ITDP/QuDu-be.git /opt/qudu-be
cd /opt/qudu-be
```

---

## 4. Konfigurasi dan secret

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

Isi semua yang kosong. Empat baris menentukan aplikasi bisa jalan atau tidak.

**`DB_URL`** — alamat database **dilihat dari dalam container**. `localhost` di
sana berarti container itu sendiri, bukan VM.

| Database berjalan di mana | Isinya |
|---|---|
| VM yang sama, di luar Docker | `jdbc:postgresql://172.17.0.1:5432/qudu2` |
| Cloud SQL | `jdbc:postgresql://<private-ip>:5432/qudu2` |
| Container terpisah | `jdbc:postgresql://<nama-container>:5432/qudu2`, keduanya satu network |

Untuk kasus pertama, PostgreSQL juga harus mendengarkan di bridge Docker, bukan
hanya di `localhost`: `listen_addresses = '*'` di `postgresql.conf`, izinkan
`172.17.0.0/16` di `pg_hba.conf`, dan biarkan port 5432 tetap tertutup di
firewall GCP supaya hanya VM itu sendiri yang bisa menjangkaunya.

**`JWT_SECRET`** — bangkitkan di VM, jangan memakai ulang nilai dari repo mana
pun:

```bash
openssl rand -hex 32
```

**`CORS_ALLOWED_ORIGIN`** — origin frontend persis seperti yang dilihat browser,
tanpa garis miring di ujung.

**`FRONTEND_RESET_PASSWORD_URL`** — `{token}` harus menjadi **bagian dari path**
(`…/reset-password/{token}`). Sebagai query parameter, tautannya sampai ke
pengguna dengan token kosong tanpa error apa pun.

Kalau push notification dinyalakan, letakkan berkas service-account di tempat
yang dibaca compose:

```bash
mkdir -p secrets
nano secrets/firebase-credentials.json
sudo chown 1001:1001 secrets/firebase-credentials.json
sudo chmod 400 secrets/firebase-credentials.json
```

Pemiliknya harus `1001`, user `qudu` di dalam container. Dengan `chmod 600` milik
user login Anda, container tidak bisa membacanya dan push gagal.

Lalu set `FIREBASE_ENABLED=true`. Dengan `FIREBASE_ENABLED=false`, berkas itu
tidak diperlukan dan push dilewati — email tetap terkirim.

`.dockerignore` sengaja mengeluarkan `src/main/resources/application.properties`
dan folder `json/` dari image, supaya kredensial tidak ikut terbangun ke dalam
image. Karena itu seluruh konfigurasi datang dari `.env`; profil `docker` sudah
lengkap sendiri (sudah diperiksa: 31 kunci, mencakup seluruh 22 kunci di berkas
dasar).

---

## 5. Skema database

Profil `docker` memakai `spring.jpa.hibernate.ddl-auto=update`, jadi Hibernate
membuat tabel yang belum ada saat start pertama. Cukup siapkan database kosong
beserta user-nya; sisanya diisi aplikasi.

**Seed data ada di `qudu2-dump.sql`** (format `pg_dump -Fc`, bukan SQL teks).
Restore **sebelum** aplikasi start pertama kali, supaya tabel buatan Hibernate
tidak bentrok dengan dump. Workflow sudah melakukannya sekali secara otomatis
(lihat bagian *Deploy otomatis*). Dengan tangan:

```bash
docker cp ~/qudu2-dump.sql qudu-be-db:/tmp/seed.dump
docker exec qudu-be-db sh -c 'pg_restore -U "$POSTGRES_USER" -d qudu2 --no-owner --no-acl --exit-on-error --single-transaction /tmp/seed.dump'
```

Tanpa dump, aplikasi tetap menyala, tetapi belum ada yang bisa login sampai Anda
memasukkan sendiri minimal satu role, baris-baris menu, dan satu user SUPERADMIN.

---

## 6. Menjalankan

```bash
docker compose build
docker compose up -d
docker compose ps
```

Build pertama makan waktu beberapa menit karena Maven mengunduh dependensinya di
dalam image. Build berikutnya memakai ulang layer itu selama `pom.xml` tidak
berubah.

Pantau prosesnya:

```bash
docker compose logs -f app
```

`docker compose ps` menampilkan `healthy` begitu container menjawab health
check-nya sendiri. Health check itu memanggil `/api/plafonds/catalog`, endpoint
publik yang benar-benar membaca database — jadi status `healthy` sekaligus bukti
bahwa `DB_URL`, user, dan password sudah benar.

```bash
curl -i http://localhost:8080/api/plafonds/catalog
```

---

## 7. Upload dokumen

Dokumen nasabah tersimpan di named volume `qudu-be_uploads`, ter-mount di
`/app/uploads`. Volume itu terpisah dari container, sehingga `docker compose
down` dan build ulang tidak menyentuhnya.

```bash
docker volume inspect qudu-be_uploads
docker compose exec app ls -la /app/uploads
```

Backup:

```bash
docker run --rm -v qudu-be_uploads:/data -v "$PWD":/backup alpine \
    tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
```

Restore:

```bash
docker run --rm -v qudu-be_uploads:/data -v "$PWD":/backup alpine \
    tar xzf /backup/uploads-2026-09-18.tar.gz -C /data
```

Kalau Anda lebih suka berkasnya berada di direktori biasa supaya gampang dilihat,
ganti baris volume di `docker-compose.yml` dengan bind mount, lalu serahkan
kepemilikannya ke user di dalam container:

```yaml
    volumes:
      - /srv/qudu/uploads:/app/uploads
```

```bash
sudo mkdir -p /srv/qudu/uploads && sudo chown -R 1001:1001 /srv/qudu/uploads
```

`1001` adalah user `qudu` di dalam image. Tanpa kepemilikan itu container tidak
bisa menulis, dan upload gagal dengan permission error.

---

## 8. HTTPS

```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx
```

`/etc/nginx/sites-available/qudu`:

```nginx
server {
    listen 80 default_server;
    server_name api-gcp.profilku.site;

    client_max_body_size 10M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -sf /etc/nginx/sites-available/qudu /etc/nginx/sites-enabled/qudu
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api-gcp.profilku.site --agree-tos --no-redirect
```

`client_max_body_size 10M` penting: default nginx hanya 1 MB, dan upload dokumen
akan gagal dengan 413 yang tidak pernah muncul di log aplikasi.

`--no-redirect` disengaja, sama seperti di EC2. Redirect 301 dari HTTP mengubah
POST milik klien Android menjadi GET, dan kegagalannya sulit dilacak.

---

## 9. Update dan rollback

```bash
cd /opt/qudu-be
git pull
docker compose build
docker compose up -d
docker compose ps
```

Compose baru mengganti container setelah image baru selesai dibangun, jadi
container lama tetap melayani selama build berlangsung. Downtime hanya beberapa
detik saat pertukaran.

**Rollback** berarti kembali ke image yang tadi berjalan. Beri tag sebelum
menimpanya, supaya ada yang bisa dituju:

```bash
docker tag qudu-be:local qudu-be:previous     # sebelum build
docker compose build
docker compose up -d
# kalau ternyata bermasalah:
APP_IMAGE=qudu-be:previous docker compose up -d
```

---

## 10. Kalau ada yang gagal

Tiga titik yang paling rawan. Dua yang pertama sudah lolos di build lokal, tapi
bisa patah lagi kalau `pom.xml` berubah:

1. **Layer `./mvnw dependency:go-offline`.** Kalau ada plugin yang tidak ikut
   terunduh di tahap itu, perintahnya gagal dan build berhenti. Perbaikannya:
   hapus baris itu dari `Dockerfile` — `mvnw package` di layer berikutnya tetap
   mengunduh apa yang kurang, hanya kehilangan manfaat cache.
2. **`mv target/*.jar target/app.jar`.** Benar selama `package` menghasilkan satu
   `.jar` (`loan-0.0.1-SNAPSHOT.jar`; `*.jar.original` tidak cocok dengan pola
   itu). Kalau suatu saat pom menghasilkan lebih dari satu artefak, ganti dengan
   nama berkas yang eksplisit.
3. **Health check.** Endpoint-nya menyentuh database, jadi container tidak akan
   pernah `healthy` selama `DB_URL` salah — padahal aplikasinya sendiri mungkin
   sudah hidup. Baca log sebelum menyimpulkan.

Gejala lain:

| Gejala | Penyebab |
|---|---|
| Build mati tanpa pesan | RAM VM habis. Minimal 2 GB |
| `Connection refused` ke database | `DB_URL` memakai `localhost`, yang di dalam container berarti container itu sendiri |
| `password authentication failed` | `pg_hba.conf` belum punya aturan untuk `172.17.0.0/16` |
| `missing table [x]` | `ddl-auto` di database itu `validate`, bukan `update` |
| Container restart terus | `docker compose logs app` — biasanya ada nilai kosong di `.env` |
| Upload gagal saat pakai bind mount | direktori host belum dimiliki `1001:1001` |
| 413 saat upload dokumen | `client_max_body_size` di nginx |
| Push tidak terkirim tanpa error | `FIREBASE_ENABLED=true` tapi berkas secret tidak ada atau tidak terbaca |
| `Syntax error: "(" unexpected` saat install Docker | skrip dijalankan dengan `sh`; pakai `bash` |

Perintah yang berguna:

```bash
docker compose logs --tail 100 app
docker compose exec app env | sort
docker inspect --format '{{json .State.Health}}' qudu-be
```
