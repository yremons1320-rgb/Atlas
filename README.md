# ATLAS AI (Android, offline)

Asisten suara: ucapkan **"Hey Atlas"** -> dia menjawab "Ya?" -> ucapkan perintah.

## 1. Siapkan (sekali saja)
1. Buka folder ini di **Android Studio** (Koala atau lebih baru). Biarkan Gradle sync.
2. Unduh model wake word offline **vosk-model-small-en-us-0.15** dari https://alphacephei.com/vosk/models
   - Ekstrak, ubah nama foldernya menjadi `model-en-us`, taruh di `app/src/main/assets/`.
   - Buat file teks bernama `uuid` (isi bebas, misalnya `1`) di dalam `assets/model-en-us/`. Wajib ada.
3. Di HP, supaya benar-benar offline, unduh paket bahasa:
   - Pengaturan > Aplikasi Google > Suara/Voice > **Pengenalan ucapan offline** > Indonesia.
   - Pengaturan > Sistem > Text-to-speech > Google > unduh suara **Bahasa Indonesia**.
4. Colokkan HP (Developer options + USB debugging), lalu Run.

## 2. Izin (tombol-tombolnya ada di bagian bawah layar app)
- Mikrofon, Kontak, Telepon, Notifikasi, Foto: pop-up otomatis saat menekan MIC.
- **Aksesibilitas**: aktifkan "ATLAS AI". Dipakai untuk kunci layar, screenshot, dan menekan Kirim di WhatsApp.
  Jika diblokir (app dipasang dari luar Play Store): Info aplikasi > menu titik tiga > *Izinkan setelan terbatas*.
- **Akses Notif**: aktifkan agar bisa membacakan pesan WhatsApp (hanya di memori, tidak disimpan).
- **Overlay (tampil di atas aplikasi lain)**: perlu agar Android 10+ mengizinkan Atlas membuka aplikasi dari latar belakang.
- **Ubah Setelan**: untuk mengatur kecerahan.
- Matikan optimasi baterai untuk app ini supaya tidak dimatikan sistem.

## 3. Contoh perintah
- "Buka WhatsApp / Instagram / YouTube / Chrome / kamera / galeri / pengaturan / Free Fire"
- "Chat ke Budi" -> "Kirim pesan aku mau pergi" (konfirmasi dulu)
- "Chat Budi dan bilang aku mau pergi, setelah itu buka Instagram"
- "Telepon ibu", "Bacakan pesan WhatsApp terbaru", "Balas dengan nanti aku hubungi"
- "Kecilkan volume", "Naikkan kecerahan", "Kunci layar", "Screenshot", "Musik berikutnya"
- "Ingatkan aku 10 menit lagi", "Jam berapa sekarang"
- "Upload story dengan caption halo"

## 4. Keterbatasan (jujur)
| Fitur | Kenyataan |
|---|---|
| Wi-Fi / Bluetooth / Mode pesawat | Android 10+ melarang app menyalakan langsung. Atlas membuka panelnya, kamu tinggal ketuk. |
| Matikan layar | Dilakukan lewat kunci layar (Aksesibilitas). |
| Shutdown | Tidak bisa tanpa root, jadi tidak disediakan. |
| Ambil foto | Kamera dibuka, tombol rana ditekan manual. |
| Instagram story/DM | Tidak ada API resmi untuk otomatisasi. Atlas menyiapkan foto terbaru lewat share intent, kamu tekan "Bagikan". DM dan balas IG belum didukung. |
| WhatsApp kirim | Pakai wa.me dan klik tombol Kirim lewat Aksesibilitas, hanya setelah kamu bilang "ya". Bisa rusak jika WhatsApp mengubah UI. |
| "Diam" | Hanya bekerja lewat tombol STOP DARURAT atau saat Atlas menunggu perintah. Mic dimatikan saat Atlas bicara. |
| Wake word | Memakai model Vosk bahasa Inggris dengan kosakata terbatas. Akurasi "atlas" perlu dites. Alternatif lebih akurat: Picovoice Porcupine (butuh akses key). |
| "Otak AI" | Rule-based offline, bukan LLM. Bisa ditambah LLM on-device (misalnya MediaPipe LLM Inference) di `CommandParser.kt` untuk kalimat bebas. |
| Always listening | Memakai foreground service dengan notifikasi permanen, dan boros baterai. |

## 5. Struktur
- `AtlasService.kt`: wake word, sesi percakapan, konfirmasi, perintah berurutan
- `CommandParser.kt`: pemahaman perintah (ID/EN)
- `Actions.kt`: eksekusi (buka app, WA, kontrol perangkat)
- `AtlasAccessibilityService.kt`, `State.kt` (notifikasi + state), `Speaker.kt` (TTS), `MainActivity.kt` (UI)

## 6. Ngobrol (otak LLM offline)
Kalimat yang bukan perintah (misalnya "ceritakan tentang Jakarta") dijawab oleh LLM yang jalan di HP, dan percakapan berlanjut tanpa perlu bilang "Hey Atlas" lagi. Ucapkan "selesai" atau diam saja untuk mengakhiri.
1. Unduh model Gemma format MediaPipe `.task` (misalnya **Gemma3-1B-IT int4** dari `litert-community` di Hugging Face). Perlu login dan menyetujui lisensi Gemma.
2. Ganti namanya menjadi `model.task`.
3. Jalankan aplikasinya sekali, lalu kirim lewat USB:
   `adb push model.task /sdcard/Android/data/id.atlas.ai/files/model.task`
4. Mulai ulang Atlas. Log akan menulis "Mode ngobrol siap."
Catatan: model 1B hasilnya lumayan tapi tidak sepintar ChatGPT. Butuh RAM kosong sekitar 2 GB dan jawabannya bisa beberapa detik.

## 7. Kontrol aplikasi apa pun
- **Buka**: "buka <nama aplikasi apa pun yang terpasang>". Pencocokan berdasarkan nama di launcher.
- **Di dalam aplikasi** (lewat Aksesibilitas): "klik <tulisan tombol>", "ketik <teks>", "scroll ke bawah/atas", "kembali", "ke home", "baca layar".
- Klik pada tombol bertulisan kirim, bayar, hapus, beli, transfer, dan sejenisnya selalu minta konfirmasi dulu.
- Kolom password tidak pernah diisi atau dibaca.
- Keterbatasan: cuma jalan di aplikasi yang membuka elemen UI-nya ke Aksesibilitas. **Game (Free Fire dkk., digambar langsung oleh game engine)**, aplikasi bank, dan layar yang diblokir `FLAG_SECURE` tidak bisa dikontrol. Atlas tetap bisa membukanya.
