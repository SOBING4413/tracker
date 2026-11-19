# Prompt untuk melanjutkan pengembangan TRACKER

Cara pakai: salin semua isi di bawah garis `=== MULAI PROMPT ===` sampai `=== AKHIR PROMPT ===`, tempel ke chat baru (Claude atau Claude Code), lalu **upload ulang zip proyek** (atau buka foldernya kalau pakai Claude Code). Isi dulu bagian `[ISI DI SINI]`.

---

=== MULAI PROMPT ===

Kamu membantu saya melanjutkan proyek **TRACKER**: aplikasi grup touring motor dengan peta GPS langsung, alert otomatis "rider tertinggal" (default > 1,1 km), SOS, dan push-to-talk. Jawab dalam **bahasa Indonesia**, santai, langkah demi langkah, dan berikan perintah untuk **Windows CMD** (bukan PowerShell, kecuali saya minta).

## Struktur proyek

- `app/` : aplikasi Android (Kotlin, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, minSdk 24, targetSdk 34, paket `com.tracker.app`). Peta pakai osmdroid, jaringan pakai OkHttp (REST + WebSocket), lokasi pakai Play Services Location.
- `backend/` : Node.js (Express 4, `ws`, `jsonwebtoken`, `bcryptjs`), `node --test` untuk unit test.
  - `src/server.js`, `src/config/index.js`, `src/controllers/api.js`, `src/websocket/index.js`
  - `src/services/`: `alertEngine.js`, `geo.js`, `hub.js`, `store.js`
  - `src/database/schema.sql` (skema PostgreSQL, belum terhubung ke kode)
- `docs/`: INSTALLATION.md, ARCHITECTURE.md, API.md, PERMISSIONS.md

## Hal penting yang sudah saya temukan di kode

- Backend memakai `MemoryStore` (`store.js`): **semua data hilang saat restart**. `DATABASE_URL` dan `REDIS_URL` belum dibaca di mana pun, dan paket `pg` belum ada di `package.json`.
- Method `MemoryStore` bersifat **sinkron**. Kalau diganti PostgreSQL (asinkron), `api.js` dan `hub.js` harus ikut disesuaikan.
- Backend tidak memakai `dotenv`, jadi variabel seperti `JWT_SECRET` dan `PORT` diset lewat environment (`set JWT_SECRET=...`).
- Alamat server di Android **ditanam saat build** lewat `BuildConfig.BASE_URL` (dari properti Gradle `trackerBaseUrl`). URL WebSocket diturunkan dengan `BASE_URL.replaceFirst("http","ws")`, jadi `https://` otomatis menjadi `wss://`.
- `app/src/main/res/xml/network_security_config.xml` membatasi HTTP biasa (cleartext) hanya untuk domain yang didaftar. Untuk produksi harus HTTPS dan daftar cleartext dihapus.
- Token JWT dikirim lewat query string di URL WebSocket (`/ws?token=...&group=...`). Ini perlu dievaluasi dari sisi keamanan.
- README bawaan menyebut: aplikasi Android awalnya belum pernah dijalankan di perangkat, voice masih PCM lewat WebSocket (belum WebRTC/Opus), dan belum ada test instrumented.

## Yang sudah berhasil sampai sekarang

- Build debug APK dengan Gradle berhasil (`gradlew clean assembleDebug`), unit test lolos.
- Backend jalan di PC Windows saya di port 8080, APK dipasang di HP **tanpa USB** (download lewat `http-server` di jaringan WiFi yang sama).
- Saat ini hanya bisa dipakai kalau HP dan PC satu WiFi, dengan alamat `http://192.168.18.124:8080`.

## Tujuan baru

Aplikasi harus bisa dipakai oleh **banyak orang dari mana saja** (data seluler atau WiFi berbeda), tanpa harus satu WiFi dengan PC saya.

Pilihan hosting yang saya pertimbangkan: **[ISI DI SINI: mis. tunnel dulu untuk uji coba / VPS / Railway / Render / Fly.io / belum tahu, mohon rekomendasi]**
Domain yang saya punya: **[ISI DI SINI: nama domain atau "belum punya"]**
Anggaran per bulan: **[ISI DI SINI atau "gratis dulu"]**
Perkiraan jumlah pengguna awal: **[ISI DI SINI, mis. 10 orang]**
Distribusi aplikasi: **[ISI DI SINI: APK langsung / Play Store]**

## Pekerjaan yang saya minta (kerjakan berurutan, tunggu konfirmasi saya tiap tahap)

1. **Tahap A, akses publik cepat untuk uji coba.** Jelaskan dan bantu setup agar backend di PC saya bisa diakses lewat HTTPS publik (misalnya tunnel), lalu build APK dengan `trackerBaseUrl` HTTPS. Jelaskan batasannya (PC harus menyala, URL bisa berubah, dan sebagainya).
2. **Tahap B, penyimpanan data permanen.** Buat implementasi PostgreSQL untuk `store.js` (pakai `schema.sql`, tambahkan `pg`, baca `DATABASE_URL`), ubah `api.js` dan `hub.js` ke async, **pertahankan `MemoryStore` untuk test**, dan perbarui test supaya tetap lolos. Sertakan langkah menjalankan database di Windows dan di server.
3. **Tahap C, deploy ke server.** Buat panduan deploy (Docker atau PM2) ke hosting pilihan saya, termasuk reverse proxy HTTPS yang mendukung WebSocket (`wss://`), pengelolaan `JWT_SECRET`, dan restart otomatis.
4. **Tahap D, keamanan dan kesiapan produksi.** Rate limiting login/register, validasi input, pengganti token di URL WebSocket (kalau memungkinkan tanpa merusak aplikasi), CORS, hapus entri cleartext dari `network_security_config.xml` untuk build rilis, backup database.
5. **Tahap E, aplikasi Android rilis.** Konfigurasi signing (keystore **tidak** boleh masuk repo), `assembleRelease`, cara bagi APK ke teman-teman, cek izin lokasi latar belakang, foreground service, dan optimasi baterai, serta apa yang dibutuhkan kalau nanti mau ke Play Store.
6. **Tahap F, privasi.** Karena aplikasi melacak lokasi orang, bantu siapkan teks persetujuan/kebijakan privasi sederhana dalam bahasa Indonesia (jelaskan bahwa ini bukan nasihat hukum).

## Aturan kerja

- Jangan asumsikan sesuatu sudah ada. **Baca kodenya dulu** sebelum mengubah, dan sebut file mana yang kamu ubah.
- Beri **perintah lengkap dan bisa langsung disalin** untuk CMD, dan jelaskan hasil yang seharusnya muncul di setiap langkah.
- Setelah setiap perubahan kode, jalankan atau jelaskan cara menjalankan test (`npm test` di `backend/`, `gradlew testDebugUnitTest` di root).
- Beri tahu kalau ada langkah yang berisiko (menghapus data, membuka port, menyimpan rahasia).
- Kalau ada hal yang tidak pasti atau butuh keputusan saya (hosting, biaya, domain), tanyakan satu pertanyaan sekaligus, jangan menebak.
- Jangan simpan password, `JWT_SECRET`, atau keystore di dalam file yang akan di-commit.
- Di akhir tiap tahap, ringkas: apa yang sudah selesai, apa yang belum, dan langkah berikutnya.

Mulai dari **Tahap A**. Tanyakan dulu kalau ada informasi yang kurang.

=== AKHIR PROMPT ===
