# Deploy QuDu-be — AWS EC2 (api.profilku.site)

**Sejak 19 September 2026, server EC2 menjalankan backend dengan Docker dan
di-deploy oleh CI/CD.** Bagian pertama di bawah menjelaskan keadaan sekarang dan
cara merawatnya. Sisa dokumen, mulai dari [Arsip: deployment
systemd](#arsip-deployment-systemd-sebelum-19-september-2026), adalah cara lama
(JAR + systemd + PostgreSQL native). Bagian itu disimpan karena service lama
masih terpasang dalam keadaan mati, sebagai jalan kembali darurat.

Untuk VM Google Cloud `34.171.0.24`, dokumennya [DEPLOY-GCP.md](DEPLOY-GCP.md).

---

## EC2 hari ini: Docker + CI/CD

```
 internet ──► nginx + certbot (host, :80 dan :443, api.profilku.site)
                 │ proxy_pass http://127.0.0.1:8080
                 ▼
          container qudu-be ─────► volume qudu-be_uploads (/app/uploads)
                 │ jdbc:postgresql://db:5432/qudu2
                 ▼
          container qudu-be-db ──► volume qudu-be_pgdata  (127.0.0.1:5433 untuk DBeaver)
```

Semua berkas ada di `/opt/qudu-be`:

| Berkas | Asal | Keterangan |
|---|---|---|
| `docker-compose.yml`, `docker-compose.aws.yml` | diunggah CI tiap deploy | jangan diedit di server |
| `scripts/aws-deploy.sh` | diunggah CI tiap deploy | yang menjalankan deploy |
| `.env` | **milik server** | dibuat sekali dari `/etc/qudu-be.env`; tidak pernah ditimpa CI |
| `secrets/firebase-credentials.json` | milik server | uid 1001, mode 400 |
| `.image-current` | ditulis skrip | image yang sedang melayani |

### Alur deploy

Push ke `main` → [deploy.yml](.github/workflows/deploy.yml):

1. `./mvnw verify` (test + gerbang coverage);
2. image dibangun di runner GitHub dan di-push ke
   `ghcr.io/bootcamp-binar-bcaf-itdp/qudu-be:<12 karakter SHA>`;
3. compose + skrip diunggah ke `/opt/qudu-be`, lalu `aws-deploy.sh` menarik
   image, menukar container, menunggu `healthy`, dan **rollback otomatis** ke
   image sebelumnya kalau gagal;
4. `https://api.profilku.site/api/plafonds/catalog` dicek dari internet.

Secret GitHub yang dipakai hanya empat yang sudah ada: `EC2_HOST`, `EC2_USER`,
`EC2_SSH_KEY`, `EC2_KNOWN_HOSTS`. Konfigurasi aplikasi tidak lewat GitHub.

**Rollback manual:** Actions → *Deploy AWS* → *Run workflow*, isi `image_tag`
dengan 12 karakter SHA commit yang ingin dikembalikan.

**`deploy-gcp.yml` juga berjalan pada push yang sama**, jadi satu push
men-deploy ke dua server. Keduanya memakai image yang identik.

### Mengubah konfigurasi

```bash
cd /opt/qudu-be
nano .env                     # mis. CORS_ALLOWED_ORIGIN, MAIL_PASSWORD
docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d app
```

`DB_PASSWORD` di `.env` hanya dibaca saat volume database pertama kali dibuat.
Mengubahnya belakangan **tidak** mengubah password di dalam database. Ganti
dulu di database (`ALTER USER qudu PASSWORD ...`), baru di `.env`.

`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, sama seperti sebelumnya: perubahan
entity yang menambah tabel/kolom butuh SQL manual sebelum deploy, atau aplikasi
menolak start dan deploy otomatis di-rollback.

### Operasi sehari-hari

```bash
cd /opt/qudu-be
C="docker compose -f docker-compose.yml -f docker-compose.aws.yml"
$C ps
$C logs -f --tail 200 app
docker exec -it qudu-be-db psql -U qudu -d qudu2
```

DBeaver: SSH tunnel ke `ubuntu@api.profilku.site`, lalu Host `localhost`,
Port **5433**, Database `qudu2`, user `qudu`, password = `DB_PASSWORD` di
`/opt/qudu-be/.env`. User lama `myappuser` dan port 5432 tidak dipakai lagi.

Backup database:

```bash
docker exec qudu-be-db pg_dump -U qudu -Fc qudu2 > ~/backups/qudu2-$(date +%F).dump
```

**Jangan pernah `docker compose down -v`** — `-v` menghapus volume database dan
seluruh data pinjaman.

### Migrasi 19 September 2026 — apa yang terjadi

Dijalankan oleh `aws-deploy.sh` sendiri, pada deploy pertama dengan database
container yang kosong:

- `qudu-be.service` dihentikan, lalu `qudu2` live di-`pg_dump` ke
  `~/backups/qudu2-pre-docker-20260919-100703.dump` (juga `myappdb`);
- di-restore ke container PostgreSQL 18; **jumlah baris cocok di 22 tabel**;
- 23 berkas dari `~/app/uploads` disalin ke volume `qudu-be_uploads`;
- container sehat dalam 27 detik; downtime sekitar satu menit;
- `.env` dibuat dari `/etc/qudu-be.env`. JWT secret dipertahankan, jadi sesi
  pengguna tidak putus. Database baru memakai user `qudu` dengan password acak
  baru;
- `qudu-be.service`, `postgresql@18-main`, `postgresql`, dan `redis-server`
  di-stop dan di-disable, dan `start.conf` cluster lama diset `manual`.
  **Tidak ada yang di-uninstall**: paket, jar di `~/releases`, dan data
  PostgreSQL native masih ada.

Catatan data yang sudah ada sebelum migrasi: hanya 9 dari 82 referensi dokumen
yang berkasnya ada di disk. Sisanya memakai path Windows
(`uploads\customer-documents\...`) dari data impor laptop dan memang tidak
pernah ada di server. Migrasi tidak mengubah angka itu.

### Kembali ke systemd (darurat)

Data native berhenti di titik migrasi. Semua yang ditulis setelah itu hanya ada
di container, jadi dump dulu dari container kalau data baru perlu dibawa.

```bash
cd /opt/qudu-be
docker compose -f docker-compose.yml -f docker-compose.aws.yml stop app
sudo sed -i 's/^manual$/auto/' /etc/postgresql/18/main/start.conf
sudo systemctl enable --now postgresql qudu-be.service
```

---

# Arsip: deployment systemd (sebelum 19 September 2026)

Semua di bawah ini menjelaskan cara lama. Ditulis setelah deployment nyata di
AWS EC2 pada 15 September 2026.

---

## Daftar isi

- **1.** [Bentuk yang akan Anda bangun](#1-bentuk-yang-akan-anda-bangun)
- **2.** [Keputusan sebelum mulai](#2-keputusan-sebelum-mulai)
- **3a.** [Menyiapkan VM — AWS EC2](#3a-menyiapkan-vm--aws-ec2)
- **3b.** [Menyiapkan VM — GCP Compute Engine](#3b-menyiapkan-vm--gcp-compute-engine)
- **4.** [Menyiapkan sistem operasi](#4-menyiapkan-sistem-operasi)
- **5.** [Database](#5-database)
- **6.** [Aplikasi](#6-aplikasi)
- **7.** [Nginx dan HTTPS](#7-nginx-dan-https)
- **8.** [Frontend dan CORS](#8-frontend-dan-cors)
- **9.** [Daftar periksa akhir](#9-daftar-periksa-akhir)
- **10.** [Operasi rutin](#10-operasi-rutin)
- **11.** [Kalau ada yang salah](#11-kalau-ada-yang-salah)
- **12.** [Jebakan yang sudah pernah memakan korban](#12-jebakan-yang-sudah-pernah-memakan-korban)
- [Soal Docker di repo ini](#soal-docker-di-repo-ini)

Langkah 3a dan 3b saling menggantikan — pilih satu sesuai penyedia cloud Anda,
lalu lanjut ke langkah 4 yang sama untuk keduanya.

---

## 1. Bentuk yang akan Anda bangun

```
                    internet
                        │
         ┌──────────────┴──────────────┐
         │                             │
   Vercel / Netlify              VM (EC2 atau GCE)
   quick-duit.contoh.site        api.contoh.site
   Angular statis                     │
         │                      ┌─────┴─────┐
         │   ── HTTPS ──▶       │   nginx   │  :80 dan :443
         │                      │  (TLS di  │
         │                      │   sini)   │
         │                      └─────┬─────┘
         │                            │ proxy_pass 127.0.0.1:8080
         │                      ┌─────┴─────┐
         │                      │  systemd  │  qudu-be.service
         │                      │  java -jar│
         │                      └─────┬─────┘
         │                            │ 127.0.0.1:5432
         │                      ┌─────┴─────┐
         │                      │PostgreSQL │  native, bukan container
         │                      └───────────┘
```

**Tiga hal yang membentuk keputusan desain ini**, semuanya berdasar pengalaman:

Java berjalan **langsung di host lewat systemd**, bukan di dalam container.
Alasannya praktis: VM kelas kecil punya RAM 1 GB, dan menumpuk Docker di atasnya
menghabiskan memori yang dibutuhkan JVM.

PostgreSQL **native di host**, bukan container, dan hanya mendengarkan
`127.0.0.1`. Database tidak pernah terekspos ke internet.

TLS berhenti di **nginx**. Aplikasi Spring tidak pernah tahu soal sertifikat.
Itu membuat perpanjangan sertifikat tidak menyentuh aplikasi sama sekali.

---

## 2. Keputusan sebelum mulai

### Ukuran mesin

| | Minimum yang jalan | **Yang disarankan** |
|---|---|---|
| RAM | 1 GB + swap 2 GB | 2 GB |
| Disk | 8 GB | **20 GB** |
| AWS | `t3.micro` | `t3.small` |
| GCP | `e2-micro` | `e2-small` |

**Disk 8 GB akan penuh, dan ini bukan ramalan.** Server yang dipakai menulis
dokumen ini mencapai 98% terisi (sisa 200 MB) hanya dengan OS, satu JDK, Maven,
dan satu jar 120 MB. Repositori `~/.m2` sendiri memakan 257 MB. Ambil 20 GB
sejak awal — menambah disk belakangan jauh lebih merepotkan.

**RAM 1 GB butuh swap, wajib.** Build Maven akan mati dengan `OutOfMemoryError`
atau proses terbunuh diam-diam tanpanya. Cara membuatnya ada di
[bagian 4](#4-menyiapkan-sistem-operasi).

### Nama domain

Siapkan dua nama:

| Nama | Menunjuk ke | Isi |
|---|---|---|
| `api.contoh.site` | IP VM | backend |
| `quick-duit.contoh.site` | Vercel / Netlify | frontend Angular |

**Frontend dan backend akan berada di origin berbeda**, jadi CORS diperlukan.
Kalau Anda ingin menghindarinya sama sekali, frontend harus dilayani oleh nginx
yang sama — tetapi itu berarti tidak memakai Vercel, dan menambah beban VM.

Kalau frontend Anda di Vercel, **tidak ada konfigurasi nginx apa pun yang bisa
menyatukan keduanya menjadi satu origin.** Jangan buang waktu mencobanya.

### Yang harus Anda siapkan sendiri

- Akun domain untuk membuat record A
- Akun Gmail dengan **App Password** untuk SMTP (bukan password biasa)
- Kredensial Firebase kalau push notification dipakai

---

## 3a. Menyiapkan VM — AWS EC2

### Membuat instance

1. EC2 → **Launch instance**
2. **AMI**: Ubuntu Server 24.04 LTS atau lebih baru
3. **Tipe**: `t3.small`
4. **Key pair**: buat baru, unduh. AWS memberi `.pem`; PuTTY di Windows butuh
   `.ppk` — konversi lewat PuTTYgen (*Load* → *Save private key*)
5. **Storage**: ubah dari 8 GB bawaan menjadi **20 GB gp3**
6. Luncurkan

### Security group

Ini yang paling sering terlewat dan menyebabkan HTTPS "tidak jalan padahal
sertifikat sudah terbit".

| Tipe | Port | Sumber | Kenapa |
|---|---|---|---|
| SSH | 22 | **IP Anda saja**, jangan `0.0.0.0/0` | akses admin |
| HTTP | 80 | `0.0.0.0/0` | certbot memverifikasi lewat sini |
| HTTPS | 443 | `0.0.0.0/0` | melayani trafik |

Port 80 harus tetap terbuka **selamanya**, bukan hanya saat penerbitan —
perpanjangan otomatis tiap 60 hari juga memakainya.

### IP tetap

EC2 → **Elastic IPs** → *Allocate* → *Associate* ke instance.

Tanpa ini, IP publik berubah setiap kali instance di-restart, dan record DNS
Anda menunjuk ke mesin orang lain.

### Masuk

```bash
# Linux / macOS / Git Bash
chmod 600 kunci.pem
ssh -i kunci.pem ubuntu@IP_ANDA

# Windows dengan PuTTY
plink -ssh -i kunci.ppk -l ubuntu IP_ANDA
```

Nama pengguna untuk AMI Ubuntu adalah `ubuntu`. Untuk Amazon Linux `ec2-user`,
Debian `admin`.

---

## 3b. Menyiapkan VM — GCP Compute Engine

### Membuat instance

```bash
gcloud compute instances create qudu-be \
  --zone=asia-southeast2-a \
  --machine-type=e2-small \
  --image-family=ubuntu-2404-lts-amd64 \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=20GB \
  --boot-disk-type=pd-balanced \
  --tags=http-server,https-server
```

Lewat Console: **Compute Engine → VM instances → Create instance**, lalu centang
*Allow HTTP traffic* dan *Allow HTTPS traffic* di bagian Firewall.

### Firewall

Tag `http-server` dan `https-server` sudah membuka 80 dan 443 lewat aturan
bawaan. Kalau proyek Anda tidak punya aturan itu:

```bash
gcloud compute firewall-rules create allow-http \
  --allow=tcp:80 --target-tags=http-server

gcloud compute firewall-rules create allow-https \
  --allow=tcp:443 --target-tags=https-server
```

SSH (22) sudah terbuka lewat aturan `default-allow-ssh`.

### IP tetap

```bash
gcloud compute addresses create qudu-be-ip --region=asia-southeast2

gcloud compute instances delete-access-config qudu-be \
  --zone=asia-southeast2-a --access-config-name="external-nat"

gcloud compute instances add-access-config qudu-be \
  --zone=asia-southeast2-a --access-config-name="external-nat" \
  --address=$(gcloud compute addresses describe qudu-be-ip \
      --region=asia-southeast2 --format='value(address)')
```

Tanpa ini IP bersifat ephemeral dan berubah saat VM dimatikan.

### Masuk

```bash
gcloud compute ssh qudu-be --zone=asia-southeast2-a
```

GCP mengurus kunci SSH sendiri — tidak ada `.pem` atau `.ppk` untuk dikelola.

---

## 4. Menyiapkan sistem operasi

Mulai titik ini, langkahnya **identik untuk AWS dan GCP**.

### Swap — kerjakan ini lebih dulu

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

free -h        # Swap harus terbaca 2.0Gi
```

Baris `fstab` itu yang membuat swap hidup lagi setelah reboot. Tanpa baris itu
swap hilang diam-diam dan build berikutnya gagal dengan alasan yang
membingungkan.

### Paket

```bash
sudo apt-get update && sudo apt-get upgrade -y

sudo apt-get install -y \
    openjdk-25-jdk \
    postgresql postgresql-contrib \
    nginx \
    certbot python3-certbot-nginx \
    git curl unzip
```

**Versi Java harus cocok dengan `pom.xml`.** Repo ini menetapkan
`<java.version>25</java.version>`. Kalau Ubuntu Anda belum punya
`openjdk-25-jdk`, gunakan Ubuntu yang lebih baru atau turunkan versi di
`pom.xml` — jangan memasang JDK lebih tua lalu berharap jar-nya jalan.

`unzip` terlihat sepele tapi sangat membantu saat memeriksa isi jar ketika
sesuatu tidak beres.

### Memeriksa

```bash
java -version          # openjdk version "25..."
psql --version         # psql (PostgreSQL) 18.x
nginx -v               # nginx/1.2x
certbot --version      # certbot 2.x atau 4.x
```

---

## 5. Database

```bash
sudo -u postgres psql
```

```sql
CREATE DATABASE qudu2;
CREATE USER qudu WITH ENCRYPTED PASSWORD 'GANTI_DENGAN_PASSWORD_KUAT';
GRANT ALL PRIVILEGES ON DATABASE qudu2 TO qudu;

\c qudu2
GRANT ALL ON SCHEMA public TO qudu;

\q
```

Baris `GRANT ALL ON SCHEMA public` itu **wajib di PostgreSQL 15 ke atas**. Tanpa
itu Hibernate gagal membuat tabel dengan pesan `permission denied for schema
public`, meski pemiliknya sudah diberi hak atas database.

### Pastikan tidak terekspos

```bash
sudo ss -tlnp | grep 5432
```

Harus berbunyi `127.0.0.1:5432`, **bukan** `0.0.0.0:5432`. Kalau `0.0.0.0`,
sunting `/etc/postgresql/*/main/postgresql.conf`, set
`listen_addresses = 'localhost'`, lalu `sudo systemctl restart postgresql`.

### Soal skema

Proyek ini **tidak memakai alat migrasi**. Skema dibentuk oleh
`spring.jpa.hibernate.ddl-auto`. Untuk deployment pertama `update` bisa dipakai,
tetapi sadari konsekuensinya: setiap perubahan entity akan mengubah bentuk
database produksi tanpa bisa dibatalkan, dan kolom yang dihapus dari entity
tidak ikut hilang.

### Data awal

**Backend ini tidak punya seed data.** Tidak ada `data.sql`, tidak ada
`CommandLineRunner`. Deployment pertama akan berjalan sehat dan **tidak seorang
pun bisa login** karena belum ada role, menu, maupun user SUPERADMIN.

Siapkan SQL awal Anda sendiri sebelum menganggap deployment selesai. Minimal
perlu: baris di tabel `roles`, baris di `menus`, dan satu user SUPERADMIN dengan
password yang sudah di-hash BCrypt.

---

## 6. Aplikasi

### Mengambil dan membangun

```bash
cd ~
git clone https://github.com/ORG/QuDu-be.git app
cd app

./mvnw clean package -DskipTests
```

Build memakan 3–8 menit di mesin kecil dan **inilah yang butuh swap**.

`-DskipTests` dipakai karena beberapa test butuh PostgreSQL sungguhan. Jalankan
test di CI, bukan di server produksi.

Hasilnya: `target/loan-0.0.1-SNAPSHOT.jar`, sekitar 120 MB.

### Berkas environment

Semua konfigurasi produksi hidup di sini, **bukan** di
`src/main/resources/application.properties`.

```bash
sudo nano /etc/qudu-be.env
```

```ini
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/qudu2
SPRING_DATASOURCE_USERNAME=qudu
SPRING_DATASOURCE_PASSWORD=password_database_anda
SPRING_JPA_HIBERNATE_DDL_AUTO=update

APP_JWT_SECRET=rahasia_acak_minimal_32_karakter
APP_SECURITY_JWT_TTL_MINUTES=30
APP_SECURITY_REFRESH_TTL_DAYS=7

APP_SECURITY_CORS_ALLOWED_ORIGIN=https://quick-duit.contoh.site
APP_FRONTEND_RESET_PASSWORD_URL=https://quick-duit.contoh.site/reset-password/{token}

SPRING_MAIL_USERNAME=email_anda@gmail.com
SPRING_MAIL_PASSWORD=app_password_16_karakter
```

```bash
sudo chmod 600 /etc/qudu-be.env
```

**Empat baris yang paling sering salah, dan akibatnya:**

`APP_SECURITY_JWT_TTL_MINUTES` — kalau tidak diisi, nilai dari
`application.properties` yang ikut terbangun ke dalam jar yang berlaku. Di repo
ini nilainya `10000000005` menit, kira-kira 19.000 tahun. Token tidak akan pernah
kedaluwarsa, seluruh rancangan refresh token menjadi sia-sia, dan token yang
bocor berlaku selamanya. **Selalu isi baris ini.**

`APP_SECURITY_CORS_ALLOWED_ORIGIN` — harus persis origin browser frontend: skema,
host, tanpa garis miring di akhir, tanpa `/api`. Salah satu karakter saja membuat
setiap permintaan ditolak.

`APP_FRONTEND_RESET_PASSWORD_URL` — perhatikan `{token}` sebagai **segmen path**,
bukan query parameter. Rute Angular-nya `reset-password/:token` dan komponennya
membaca `paramMap`. Menulis `?token={token}` menghasilkan tautan yang tampak
benar tetapi token-nya selalu kosong.

`SPRING_MAIL_PASSWORD` — App Password Google 16 karakter, bukan password akun.

### Kenapa env, bukan properties

Spring membaca variabel environment dengan *relaxed binding*:
`APP_SECURITY_CORS_ALLOWED_ORIGIN` memetakan ke
`app.security.cors-allowed-origin` dan **menimpa** nilai di dalam jar.

Ini penting karena `application.properties` di repo ini dilacak git dengan nilai
kosong dan diisi lokal oleh tiap developer. Nilai lokal itu ikut terbangun ke
dalam jar. Env file adalah satu-satunya tempat yang aman untuk nilai produksi,
dan satu-satunya yang tidak akan pernah ter-commit.

### Layanan systemd

```bash
sudo nano /etc/systemd/system/qudu-be.service
```

```ini
[Unit]
Description=QuDu-be
After=network.target postgresql.service

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/app
EnvironmentFile=/etc/qudu-be.env
ExecStart=/usr/bin/java -jar /home/ubuntu/app/target/loan-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now qudu-be.service
sudo systemctl status qudu-be.service
```

`SuccessExitStatus=143` mencegah systemd menganggap penghentian normal (SIGTERM)
sebagai kegagalan lalu me-restart tanpa henti.

`enable` membuatnya hidup lagi setelah VM reboot. Mudah terlupa, dan baru
ketahuan berbulan-bulan kemudian saat instance di-restart.

### Memeriksa

```bash
curl -i http://127.0.0.1:8080/api/plafonds/catalog
```

Harus `200`. Kalau bukan:

```bash
sudo journalctl -u qudu-be.service -n 50 --no-pager
```

---

## 7. Nginx dan HTTPS

### DNS lebih dulu

Buat record A di penyedia domain Anda:

| Tipe | Nama | Nilai | TTL |
|---|---|---|---|
| A | `api` | IP publik VM | 300 |

Tunggu sampai benar-benar teresolusi:

```bash
nslookup api.contoh.site 8.8.8.8
```

**Certbot akan gagal kalau nama belum menunjuk ke server ini.** Jangan lanjut
sebelum perintah di atas mengembalikan IP VM Anda.

### Konfigurasi nginx

```bash
sudo nano /etc/nginx/sites-available/qudu
```

```nginx
server {
    listen 80 default_server;
    server_name api.contoh.site;

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
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/qudu /etc/nginx/sites-enabled/qudu
sudo nginx -t
sudo systemctl reload nginx
```

`client_max_body_size 10M` diperlukan karena aplikasi ini menerima unggahan
dokumen. Nilai bawaan nginx 1 MB, dan gejalanya `413` yang tidak pernah sampai ke
log aplikasi.

### Sertifikat

```bash
sudo certbot --nginx -d api.contoh.site --agree-tos --no-redirect
```

**`--no-redirect` disengaja.** Dengan `--redirect`, nginx mengembalikan 301 dari
HTTP ke HTTPS — dan klien non-browser seperti aplikasi Android akan mengubah POST
menjadi GET saat mengikuti 301, sehingga panggilan API gagal dengan cara yang
sulit dilacak. Biarkan port 80 tetap melayani sampai Anda yakin semua klien sudah
memakai HTTPS.

Certbot menyunting berkas nginx Anda sendiri dan memasang timer perpanjangan.

```bash
sudo certbot certificates
systemctl list-timers | grep certbot
sudo certbot renew --dry-run
```

### Memeriksa dari luar

Jalankan ini dari **laptop Anda**, bukan dari server — inilah yang membuktikan
firewall benar:

```bash
curl -i https://api.contoh.site/api/plafonds/catalog
```

`000` atau timeout berarti port 443 tertutup di security group (AWS) atau
firewall rule (GCP), bukan masalah sertifikat.

---

## 8. Frontend dan CORS

### Arahkan frontend ke API

`src/environments/environment.ts`:

```ts
export const environment = {
  production: true,
  apiOrigin: 'https://api.contoh.site',
};
```

**Harus `https://`.** Halaman HTTPS tidak boleh memanggil endpoint HTTP — browser
memblokirnya sebagai *mixed content*, dan permintaannya tidak pernah keluar dari
browser. Header CORS dari backend tidak akan pernah terbaca karena tidak ada
permintaan yang sampai.

Gejalanya mudah disalahartikan sebagai masalah CORS. Bedanya: mixed content
muncul di konsol browser sebagai peringatan *blocked*, sementara CORS muncul
sebagai respons yang ditolak. Kalau tab Network tidak menunjukkan permintaan sama
sekali, itu mixed content.

### Deploy ke Vercel

```bash
npm run build        # menghasilkan dist/
```

Vercel: hubungkan repo, **Framework preset** Angular, lalu tambahkan domain
`quick-duit.contoh.site` di *Settings → Domains* dan ikuti record CNAME yang
diberikan.

### Cocokkan CORS

Nilai `APP_SECURITY_CORS_ALLOWED_ORIGIN` di server **harus persis sama** dengan
origin frontend. Setelah mengubahnya:

```bash
sudo systemctl restart qudu-be.service
```

Buktikan tanpa perlu membuka browser:

```bash
curl -s -D- -o /dev/null -X OPTIONS https://api.contoh.site/api/auth/login \
  -H "Origin: https://quick-duit.contoh.site" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type" | grep -i "access-control"
```

Harus muncul `Access-Control-Allow-Origin` berisi origin Anda. Ulangi dengan
origin asing — seharusnya `403` tanpa header CORS sama sekali.

---

## 9. Daftar periksa akhir

```bash
# di server
systemctl is-active qudu-be.service postgresql nginx      # tiga-tiganya active
systemctl is-enabled qudu-be.service                      # enabled
free -h | grep Swap                                       # swap hidup
df -h /                                                   # di bawah 80%
sudo ss -tlnp | grep 5432                                 # 127.0.0.1 saja
sudo certbot certificates                                 # masa berlaku

# dari laptop
curl -i https://api.contoh.site/api/plafonds/catalog       # 200
```

Lalu, di browser: login, buka DevTools → Network → respons `/api/auth/login`.
Kolom `expiresIn` harus `1800` untuk TTL 30 menit. Kalau angkanya raksasa,
`APP_SECURITY_JWT_TTL_MINUTES` tidak terbaca.

---

## 10. Operasi rutin

### Deploy otomatis — cara yang normal

Setelah CI/CD terpasang, **Anda tidak perlu menyentuh server untuk merilis.**
Push ke `main` → test berjalan di runner GitHub → jar dikirim ke server →
layanan di-restart → kesehatan diperiksa → kalau gagal, otomatis kembali ke rilis
sebelumnya.

Alurnya ada di `.github/workflows/deploy.yml`.

**Jar dibangun di runner, bukan di server.** Server hanya menerima berkas jadi.
Konsekuensinya server tidak butuh Maven, tidak butuh `~/.m2` (257 MB), dan tidak
pernah menghabiskan RAM 908 MB untuk kompilasi.

#### Tata letak rilis

```
~/releases/<sha>.jar                         satu berkas per rilis, 3 terbaru disimpan
~/app/target/loan-0.0.1-SNAPSHOT.jar   →     symlink ke rilis yang aktif
```

Systemd tetap menunjuk path yang sama, jadi unit tidak pernah perlu disunting.
Pertukaran rilis adalah `ln -sfn`, yang berupa *rename* di tingkat kernel —
atomik, tidak pernah ada saat ketika path itu menunjuk ke berkas yang rusak.

#### Secret yang harus diisi di GitHub

**Settings → Secrets and variables → Actions → New repository secret**

| Nama | Isi |
|---|---|
| `EC2_HOST` | `api.contoh.site` |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | isi lengkap kunci privat deploy, termasuk baris `BEGIN`/`END` |
| `EC2_KNOWN_HOSTS` | keluaran `ssh-keyscan -t ed25519 api.contoh.site` |

#### Membuat deploy key khusus

**Jangan memakai kunci `.pem`/`.ppk` admin Anda sebagai `EC2_SSH_KEY`.** Kunci itu
memberi akses penuh dan tidak bisa dicabut tanpa mengunci diri Anda sendiri.
Buat kunci terpisah yang hanya untuk CI:

```bash
# di server
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "github-actions-deploy"
cat ~/.ssh/github_deploy.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Lalu ambil bagian privatnya **dari mesin Anda sendiri**, jangan lewat layar orang
lain:

```bash
ssh -i kunci-admin ubuntu@IP "cat ~/.ssh/github_deploy"
```

Tempel isinya ke secret `EC2_SSH_KEY`, lalu hapus jejaknya di server:

```bash
ssh -i kunci-admin ubuntu@IP "shred -u ~/.ssh/github_deploy"
```

Bagian publiknya tetap di `authorized_keys` dan itulah yang membuat CI bisa masuk.
Kalau suatu saat kunci itu bocor, cukup hapus satu barisnya dari
`authorized_keys` — akses admin Anda tidak terganggu.

#### CI juga butuh sudo tanpa password

Workflow menjalankan `systemctl restart`. Pastikan:

```bash
sudo -n true && echo "NOPASSWD aktif"
```

Kalau belum: `echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' | sudo tee /etc/sudoers.d/ubuntu`

#### Rollback

**Actions → Deploy → Run workflow**, isi kolom *release* dengan nama jar yang
ingin dikembalikan, misalnya `a1b2c3d4e5f6.jar`. Workflow melewati tahap build
dan langsung menukar symlink.

Untuk melihat rilis apa saja yang tersedia:

```bash
ls -1t ~/releases/
readlink -f ~/app/target/loan-0.0.1-SNAPSHOT.jar   # yang sedang aktif
```

Rollback manual, kalau GitHub sedang tidak bisa diakses:

```bash
ln -sfn ~/releases/a1b2c3d4e5f6.jar ~/app/target/loan-0.0.1-SNAPSHOT.jar
sudo systemctl restart qudu-be.service
```

#### Apa yang menghentikan deploy

| Tahap | Menghentikan kalau |
|---|---|
| Test | ada test gagal, atau coverage turun di bawah gerbang JaCoCo |
| Periksa disk | ruang bebas server di bawah 300 MB |
| Unggah | scp terputus — berkas sementara tidak pernah diberi nama final |
| Kesehatan | endpoint tidak menjawab 200 dalam 45 detik → **otomatis rollback** |
| Bukti publik | sehat di localhost tetapi tidak terjangkau dari internet |

### Memperbarui aplikasi secara manual

Hanya diperlukan untuk server baru yang belum punya jar sama sekali, atau saat
GitHub sedang tidak bisa dipakai.

```bash
cd ~/app
git pull
./mvnw clean package -DskipTests
mv target/loan-0.0.1-SNAPSHOT.jar ~/releases/manual-$(date +%F).jar
ln -sfn ~/releases/manual-$(date +%F).jar ~/app/target/loan-0.0.1-SNAPSHOT.jar
sudo systemctl restart qudu-be.service
```

Downtime sekitar 20 detik. Ingat bahwa cara ini memerlukan `~/.m2` kembali diunduh
dan memakan ruang disk yang sengaja dibebaskan untuk rilis.

### Log

```bash
sudo journalctl -u qudu-be.service -f          # mengikuti
sudo journalctl -u qudu-be.service -p err      # error saja
sudo tail -f /var/log/nginx/error.log
```

### Cadangan database

```bash
sudo -u postgres pg_dump qudu2 | gzip > ~/qudu2-$(date +%F).sql.gz
```

Otomatiskan lewat cron, dan **salin keluar dari VM** — cadangan yang hanya ada di
mesin yang sama tidak melindungi dari apa pun.

### Sebelum mengubah konfigurasi

```bash
sudo cp -a /etc/qudu-be.env "/etc/qudu-be.env.bak-$(date +%Y%m%d-%H%M%S)"
```

Biasakan. Ini yang membedakan kesalahan yang bisa dibatalkan dalam sepuluh detik
dengan yang butuh satu jam.

---

## 11. Kalau ada yang salah

| Gejala | Penyebab yang paling mungkin |
|---|---|
| Layanan tidak mau start | Kredensial database salah di `/etc/qudu-be.env`. Baca `journalctl -u qudu-be -n 50` |
| `permission denied for schema public` | Lewat `GRANT ALL ON SCHEMA public TO qudu` di bagian 5 |
| Build mati tanpa pesan | Swap belum ada atau tidak aktif setelah reboot |
| Certbot gagal | DNS belum teresolusi, atau port 80 tertutup |
| HTTPS timeout dari luar, padahal 443 mendengarkan | Port 443 tertutup di security group / firewall rule |
| CORS ditolak terus | Origin tidak persis sama. Periksa garis miring di akhir dan `http` vs `https` |
| Tidak ada permintaan sama sekali di tab Network | Mixed content — frontend HTTPS memanggil API HTTP |
| Unggahan dokumen gagal `413` | `client_max_body_size` di nginx |
| Email reset password mengarah ke tempat salah | `APP_FRONTEND_RESET_PASSWORD_URL`, dan pastikan `{token}` segmen path |
| Login berhasil tapi pengguna keluar setelah 30 menit | Build frontend yang tayang belum punya alur refresh token |
| Semua tiba-tiba mati setelah beberapa bulan | Disk penuh. `df -h /` |

---

## 12. Jebakan yang sudah pernah memakan korban

Semuanya benar-benar terjadi pada deployment yang mendasari dokumen ini.

**Nilai placeholder yang lolos ke produksi.** `APP_SECURITY_CORS_ALLOWED_ORIGIN`
dan `APP_FRONTEND_RESET_PASSWORD_URL` sama-sama masih berbunyi
`https://domain-frontend-anda.vercel.app` berhari-hari setelah deploy. Yang
pertama membuat frontend tidak pernah bisa memanggil API. Yang kedua membuat
setiap email reset password mengarah ke domain yang tidak ada. Keduanya gagal
diam-diam — tidak ada error di log server.

**TTL token dari konfigurasi lokal.** `10000000005` menit ikut terbangun ke dalam
jar dan berlaku di produksi selama berhari-hari.

**Disk mencapai 98%.** Ruang tersisa 200 MB dari 6,7 GB tanpa ada peringatan.

**Unit systemd disunting tanpa `daemon-reload`.** `systemctl show qudu-be -p
NeedDaemonReload` berbunyi `yes`, artinya yang berjalan berbeda dari yang ada di
disk. Selalu `daemon-reload` setelah menyunting unit.

**Jar yang tayang tertinggal dua minggu dari repo.** Tidak ada yang menyadarinya
karena tidak ada apa pun yang menampilkan versi yang sedang berjalan.

---

## Soal Docker di repo ini

Sejak 19 September 2026 EC2 **berjalan dengan Docker**. Lihat bagian [EC2 hari
ini](#ec2-hari-ini-docker--cicd) di awal dokumen ini. Berkas yang dipakai:
`Dockerfile`, `docker-compose.yml`, `docker-compose.aws.yml`,
`scripts/aws-deploy.sh`, dan `.github/workflows/deploy.yml`.

`docker-compose.gcp.yml` dan `docker-compose.local.yml` untuk target lain (VM
GCP dan laptop). Jangan dipakai di EC2.
