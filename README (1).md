# TRACKER: Catatan Build & Menjalankan (Tanpa USB)

Catatan pribadi supaya tidak lupa cara build APK, menjalankan backend, dan memasang aplikasi ke HP lewat WiFi.

- Folder proyek: `C:\Users\hikam\Downloads\Compressed\TRACKER`
- IP PC (WiFi) saat dibuat: `192.168.18.124`
- Port backend: `8080`, port download APK: `9000`

> **Penting:** IP PC bisa berubah kalau pindah WiFi atau router memberi IP baru. Cek dengan `ipconfig` (bagian **Wireless LAN adapter Wi-Fi**, baris **IPv4 Address**). Kalau berubah, lakukan bagian **5. Kalau IP PC berubah**.

---

## 0. Ringkasan cepat (pemakaian sehari-hari)

Kalau APK sudah terpasang di HP dan IP PC tidak berubah, cukup:

1. Nyalakan PC, sambungkan ke WiFi yang sama dengan HP.
2. Jalankan backend (bagian 3).
3. Buka aplikasi TRACKER di HP.

Tutup jendela CMD backend = aplikasi tidak bisa terhubung.

---

## 1. Prasyarat (sekali saja)

| Kebutuhan | Keterangan |
|---|---|
| JDK 17 | Cek: `java -version` |
| Android SDK | Lokasi: `C:\Users\hikam\AppData\Local\Android\Sdk` |
| Gradle | Dipakai hanya untuk membuat wrapper (`gradle wrapper`) |
| Node.js 18+ | Untuk backend. Cek: `node -v` |

### 1.1 Buat wrapper Gradle (kalau `gradlew.bat` belum ada)

```bat
cd /d C:\Users\hikam\Downloads\Compressed\TRACKER
gradle wrapper --gradle-version 8.7
```

### 1.2 Buat `local.properties` (kalau belum ada)

```bat
cd /d C:\Users\hikam\Downloads\Compressed\TRACKER
echo sdk.dir=C:/Users/hikam/AppData/Local/Android/Sdk> local.properties
type local.properties
```

Harus tampil: `sdk.dir=C:/Users/hikam/AppData/Local/Android/Sdk`

> Kalau muncul error `SDK location not found`, berarti file ini belum ada. Ulangi langkah di atas.

### 1.3 Buka firewall (CMD **sebagai Administrator**, sekali saja)

```bat
netsh advfirewall firewall add rule name="TRACKER 8080" dir=in action=allow protocol=TCP localport=8080
netsh advfirewall firewall add rule name="TRACKER 9000" dir=in action=allow protocol=TCP localport=9000
```

Harus muncul `Ok.` dua kali.

---

## 2. Build APK

Alamat server di-*hardcode* ke APK saat build, jadi APK harus dibuild ulang kalau IP PC berubah.

### 2.1 Set alamat server di `gradle.properties`

```bat
cd /d C:\Users\hikam\Downloads\Compressed\TRACKER
powershell -Command "(Get-Content gradle.properties) -replace '^trackerBaseUrl=.*','trackerBaseUrl=http://192.168.18.124:8080' | Set-Content gradle.properties"
findstr trackerBaseUrl gradle.properties
```

Harus tampil: `trackerBaseUrl=http://192.168.18.124:8080`

### 2.2 Izinkan koneksi HTTP ke IP PC

Buka file:

```bat
notepad app\src\main\res\xml\network_security_config.xml
```

Isi dengan (ganti IP kalau berubah), lalu simpan:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.18.124</domain>
    </domain-config>
</network-security-config>
```

> Tanpa langkah ini Android memblokir HTTP biasa (bukan HTTPS).

### 2.3 Build

```bat
gradlew clean assembleDebug
```

Tunggu sampai `BUILD SUCCESSFUL`. Baris `w:` (warning) boleh diabaikan.

> Kalau `gradlew` tidak dikenali, pakai `.\gradlew.bat clean assembleDebug`.

### 2.4 Cek alamat server sudah masuk ke APK

```bat
findstr /s /c:"BASE_URL" app\build\generated\source\buildConfig\debug\com\tracker\app\BuildConfig.java
```

Harus tampil: `BASE_URL = "http://192.168.18.124:8080"`. Kalau masih `10.0.2.2`, ulangi langkah 2.1 lalu build bersih lagi.

### 2.5 Salin APK ke folder berbagi

```bat
mkdir %USERPROFILE%\Desktop\tracker-share
copy /Y app\build\outputs\apk\debug\app-debug.apk %USERPROFILE%\Desktop\tracker-share\tracker-baru.apk
```

Lokasi asli APK: `app\build\outputs\apk\debug\app-debug.apk`

---

## 3. Jalankan backend

Buka CMD baru dan **biarkan terbuka**:

```bat
cd /d C:\Users\hikam\Downloads\Compressed\TRACKER\backend
npm install
set JWT_SECRET=isi-string-acak-panjang
npm start
```

Harus muncul: `TRACKER backend on :8080`

- `npm install` cukup sekali (boleh dilewati di lain waktu).
- `set JWT_SECRET=...` harus diulang setiap membuka CMD baru.
- Tes: di browser HP buka `http://192.168.18.124:8080/health`. Harus tampil `{"ok":true}`.

---

## 4. Pasang APK ke HP tanpa kabel

### 4.1 Jalankan server download (CMD baru, biarkan terbuka)

```bat
cd /d %USERPROFILE%\Desktop\tracker-share
npx --yes http-server . -p 9000
```

### 4.2 Di HP

1. Pastikan HP satu WiFi dengan PC. IP HP harus berawalan `192.168.18.`.
2. Buka `http://192.168.18.124:8080/health` di browser HP. Harus tampil `{"ok":true}`.
3. Buka `http://192.168.18.124:9000/tracker-baru.apk` dan download.
4. **Hapus TRACKER lama** di HP.
5. Install `tracker-baru.apk`. Kalau diminta, izinkan **Install unknown apps** untuk browser.
6. Buka TRACKER, tekan **Create account**, lalu login.

Setelah APK terpasang, jendela server download (port 9000) boleh ditutup. Jendela backend (port 8080) jangan.

---

## 5. Kalau IP PC berubah

1. Cek IP baru: `ipconfig` (adapter **Wi-Fi**, bukan VMware).
2. Ulangi bagian **2** dengan IP baru (`gradle.properties`, `network_security_config.xml`, build, cek `BASE_URL`, salin APK).
3. Jalankan backend (bagian 3) dan server download (bagian 4.1).
4. Di HP: hapus aplikasi lama, download dan install APK baru.

---

## 6. Troubleshooting

| Gejala | Penyebab / solusi |
|---|---|
| `failed to connect to /10.0.2.2` | APK lama masih terpasang. `10.0.2.2` hanya untuk emulator. Hapus aplikasi, install APK baru. |
| `failed to connect to /192.168.18.124` | HP dan PC beda jaringan, backend belum jalan, atau firewall belum dibuka. |
| `/health` tidak terbuka di HP | HP tidak satu WiFi dengan PC, atau router mengaktifkan AP isolation. Coba hotspot HP (lalu cek ulang IP PC dan build ulang). |
| `SDK location not found` | `local.properties` belum ada. Lihat bagian 1.2. |
| `'gradlew' is not recognized` | Pakai `.\gradlew.bat`, atau buat wrapper (bagian 1.1). |
| Akun hilang setelah restart | Normal, lihat catatan database di bawah. |
| IP di `ipconfig` ada beberapa | Pakai yang di bagian **Wireless LAN adapter Wi-Fi**. Abaikan VMware (`192.168.181.1`, `192.168.159.1`). |

---

## 7. Catatan database

Backend **belum punya database sungguhan**. Data (akun, grup, riwayat lokasi) disimpan di memori (`MemoryStore`), jadi **semua hilang setiap backend dimatikan atau di-restart**. Kalau backend di-restart, buat akun lagi lewat **Create account**.

`backend/src/database/schema.sql` hanya skema PostgreSQL untuk rencana ke depan; belum terhubung ke kode. Opsi pengembangan:

1. Simpan ke file JSON (paling cepat, data tetap ada setelah restart).
2. Tulis implementasi PostgreSQL untuk `backend/src/services/store.js` (lebih berat, cocok untuk produksi).

---

## 8. Catatan lain

- Aplikasi Android ini, menurut README bawaan proyek, belum pernah diuji di perangkat sungguhan sebelum ini. Kalau ada bug saat dipakai, itu mungkin dari kode proyek, bukan dari langkah di atas.
- Build `debug` dipakai di sini. Build `release` butuh konfigurasi signing sendiri.
- Penyimpanan di atas memakai HTTP biasa untuk jaringan lokal. Untuk dipakai lewat internet, gunakan server dengan HTTPS.
