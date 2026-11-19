# TRACKER: Lanjutan Supaya Bisa Dipakai Semua Orang (Tanpa Satu WiFi)

## Kenapa sekarang harus satu WiFi?

Alamat server (`http://192.168.18.124:8080`) adalah **IP lokal** PC. IP seperti `192.168.x.x` hanya dikenal di dalam jaringan rumah. HP teman di luar WiFi itu tidak bisa menjangkaunya.

Supaya bisa dipakai dari mana saja, backend harus punya **alamat publik** (domain atau URL HTTPS) dan jalan terus tanpa tergantung PC pribadi.

## Gambaran besarnya

```
HP rider (data seluler / WiFi mana pun)
        │  https:// dan wss://
        ▼
  Domain / URL publik  ──►  Reverse proxy (HTTPS)  ──►  Backend Node.js  ──►  PostgreSQL
```

Yang perlu disiapkan: **(1)** alamat publik HTTPS, **(2)** database permanen, **(3)** server yang menyala terus, **(4)** APK yang dibuild dengan alamat publik itu.

---

## Tahap A: Uji coba cepat (PC tetap dipakai sebagai server)

Tujuan: teman bisa mencoba dari luar WiFi, tanpa sewa server.

**Cara:** pakai *tunnel* (misalnya Cloudflare Tunnel atau ngrok). Tunnel membuat URL HTTPS publik yang meneruskan lalu lintas ke `localhost:8080` di PC-mu.

Alur umumnya:

1. Install aplikasi tunnel, lalu jalankan dengan target `http://localhost:8080`.
2. Tunnel memberi URL seperti `https://xxxx.contoh.com`.
3. Build ulang APK:
   ```bat
   gradlew clean assembleDebug -PtrackerBaseUrl=https://xxxx.contoh.com
   ```
4. Bagikan APK ke teman. WebSocket otomatis jadi `wss://` karena aplikasi mengubah `http` menjadi `ws` dari alamat dasar.

**Batasan:**

- PC harus menyala, online, dan backend + tunnel harus jalan.
- Versi gratis sering memberi URL yang berubah-ubah. Kalau URL berubah, APK harus dibuild ulang. URL tetap biasanya butuh akun atau domain sendiri; cek ketentuan terbaru layanan yang dipilih.
- Data tetap hilang saat backend restart (belum ada database).

Cocok untuk tes beberapa jam atau beberapa hari, bukan untuk dipakai rutin.

---

## Tahap B: Database permanen (wajib sebelum dipakai serius)

Sekarang semua data ada di memori dan hilang saat restart. Yang perlu dikerjakan di kode:

1. Tambahkan `pg` ke `backend/package.json`.
2. Tulis implementasi PostgreSQL untuk `backend/src/services/store.js`, memakai skema di `backend/src/database/schema.sql`.
3. Baca `DATABASE_URL` dari environment.
4. Ubah `controllers/api.js` dan `services/hub.js` menjadi **async**, karena `MemoryStore` sekarang sinkron sedangkan `pg` asinkron.
5. Pertahankan `MemoryStore` untuk test supaya unit test tetap cepat.

Ini pekerjaan kode terbesar dalam daftar ini. Alternatif ringan untuk sementara: simpan ke file JSON (data bertahan setelah restart, tapi tidak cocok untuk banyak pengguna).

---

## Tahap C: Server yang menyala terus (hosting)

Pilihan umum:

| Pilihan | Cocok untuk | Catatan |
|---|---|---|
| VPS (mis. penyedia di Indonesia atau luar negeri) | Kontrol penuh, biaya bulanan tetap | Kamu atur sendiri Node, PostgreSQL, HTTPS |
| PaaS (Railway, Render, Fly.io, dan sejenisnya) | Cepat, tanpa mengurus server | Pastikan mendukung **WebSocket**, dan cek paket gratis/berbayar terbaru |
| Server di rumah (PC/mini PC) + tunnel | Murah | Bergantung listrik dan internet rumah |

Yang dibutuhkan di server apa pun:

- Node.js 18+ dan PostgreSQL (atau database terkelola dari penyedia).
- **Reverse proxy HTTPS** (Caddy atau nginx) yang meneruskan WebSocket, supaya `https://` dan `wss://` jalan.
- Proses yang restart otomatis (PM2 atau Docker dengan `restart: always`).
- `JWT_SECRET` string acak panjang, disimpan sebagai environment variable di server, **bukan di dalam kode**.
- Domain (disarankan) yang diarahkan ke server.

> Cek harga dan batas gratis di situs penyedia pada saat memilih, karena ketentuannya sering berubah.

---

## Tahap D: Keamanan dan kesiapan produksi

Karena aplikasi ini membawa **lokasi real-time orang**, bagian ini penting:

- Rate limiting untuk login/register (mencegah tebak password).
- Validasi input dan batas panjang data di API.
- Token JWT saat ini dikirim lewat query string di URL WebSocket (`/ws?token=...`). URL bisa tercatat di log proxy, jadi pertimbangkan cara pengiriman yang lebih aman.
- Atur CORS seperlunya.
- Backup database berkala.
- Jangan pernah membagikan `JWT_SECRET` atau kredensial database.
- Hapus izin HTTP biasa (cleartext) di `network_security_config.xml` untuk build rilis, supaya aplikasi hanya mau HTTPS.

---

## Tahap E: APK rilis dan cara membagikan

1. **Signing:** buat keystore sendiri, simpan aman (jangan masuk repo, jangan hilang; tanpa itu update aplikasi tidak bisa dipasang di atas versi lama).
2. Build rilis:
   ```bat
   gradlew assembleRelease -PtrackerBaseUrl=https://domain-kamu.com
   ```
3. Bagikan APK lewat tautan (Google Drive, GitHub Releases, atau server sendiri).
4. Pengguna perlu mengizinkan **Install unknown apps** sekali.
5. Pastikan lokasi latar belakang, foreground service, dan pengecualian optimasi baterai berfungsi di HP berbagai merek. Di banyak HP, aplikasi yang jalan di latar belakang dimatikan sistem, dan ini penting untuk aplikasi pelacak.
6. **Play Store** (opsional, nanti): butuh akun developer berbayar, kebijakan privasi, dan formulir penjelasan penggunaan lokasi latar belakang. Prosesnya lebih panjang daripada APK langsung.

---

## Tahap F: Privasi dan hukum

- Tampilkan persetujuan yang jelas sebelum aplikasi melacak lokasi.
- Siapkan kebijakan privasi sederhana: data apa yang disimpan, berapa lama (backend sudah punya retensi riwayat 30 hari), siapa yang bisa melihat, dan cara menghapus akun.
- Di Indonesia ada UU Pelindungan Data Pribadi (UU No. 27 Tahun 2022). Kalau aplikasi dipakai orang banyak, konsultasikan dengan ahli hukum. Ini bukan nasihat hukum.

---

## Urutan yang disarankan

1. **Tahap A** dulu, supaya teman bisa mencoba dari luar WiFi dengan usaha kecil.
2. **Tahap B** (database), karena tanpa ini data hilang terus.
3. **Tahap C** (hosting), supaya tidak bergantung PC.
4. **Tahap D** sebelum mengajak banyak orang.
5. **Tahap E** dan **F** saat siap dibagikan lebih luas.

Untuk mengerjakannya bersama AI, pakai `prompt.md`.
