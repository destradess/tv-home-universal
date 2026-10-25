# TV Home Universal



**Aplikasi, wallpaper, dan layar utama sesuai pilihanmu.**



Launcher minimalis untuk Android TV dan Google TV dengan tampilan liquid glass, navigasi remote, widget yang bisa diatur, screensaver bergerak, dan quick settings.



[English](README.md) | [Bahasa Indonesia](README.id.md)



> **Preview 0.2.1** — Menargetkan Android TV / Google TV 8.0+ (API 26). APK berhasil dibangun dan pemeriksaan logika lolos, tetapi versi universal ini belum diuji pada TV fisik. Dukungan fitur bergantung pada firmware setiap merek.



## Perbaikan navigasi 0.2.1

Timer Menonton kini dibuka di dalam overlay Quick Settings tanpa membawa Home ke depan aplikasi lain. Back kembali ke Quick Settings. Back dari Efek Suara dan submenu Settings berbasis Activity mengembalikan Settings dengan pilihan terakhir. Pengujian langsung di TV masih diperlukan. Lihat [catatan rilis](RELEASE-NOTES-0.2.1.md).

## Fitur utama



- Lima aplikasi favorit di bagian utama dan aplikasi lainnya dalam grid.

- Tahan OK pada aplikasi untuk memindahkan urutannya menggunakan tombol arah. OK menyimpan, Back membatalkan.

- Aplikasi yang baru dipasang otomatis muncul di launcher.

- Menu transparan dengan tema liquid glass.

- Widget jam, sapaan, hari/tanggal, dan jadwal salat yang dapat diaktifkan secara terpisah.

- Nama atau teks tambahan setelah sapaan waktu dapat disesuaikan.

- Wallpaper gradien bawaan serta integrasi sumber gambar online dengan kredensial milik pengguna.

- Kategori TMDB untuk film, series, dan anime trending maupun top rated, sesuai ketersediaan backdrop.

- Screensaver dengan pergerakan latar, jam/tanggal bergerak, dan pergantian gambar acak setiap dua menit.

- Quick settings untuk screensaver, input, tampilan, bunyi, Wi-Fi, Bluetooth, bahasa, dan setelan utama. Akses dari aplikasi lain bergantung pada layanan aksesibilitas dan firmware.

- Override Launcher opsional melalui aksesibilitas.

- Tujuh paket suara: PS5, PS2 System, Switch, Xbox Series, Persona 5, Pip-Boy, dan Portal 2, dengan pengaturan aktif/nonaktif dan volume.

- Bahasa Indonesia dan Inggris mengikuti bahasa sistem. Bahasa lain memakai Inggris sebagai cadangan.



## Cara memasang



1. Ambil APK preview dari aset release proyek jika sudah tersedia.

2. Pasang melalui metode sideload yang biasa digunakan di TV.

3. Buka **TV Home Universal** dari daftar aplikasi.

4. Pilih sebagai Home melalui pengaturan bawaan TV jika pilihan itu tersedia.

5. Jika diperlukan, aktifkan **Override Launcher** dan izinkan layanan aksesibilitasnya melalui pengaturan sistem.



Biarkan launcher bawaan aktif selama pengujian awal. Proyek ini tidak menyertakan script untuk menonaktifkan aplikasi sistem.



Application ID versi universal: `io.github.tvhome.universal`. Aplikasi ini terpisah dari build kustom sebelumnya.



## Paket suara



Buka Settings → Efek Suara, aktifkan efeknya, lalu pilih paket dan atur volume. Pilihan paket disembunyikan ketika efek nonaktif. Paket awal adalah PS2 System. Bunyi sleep/wake bergantung pada firmware dan callback aplikasi/layanan yang aktif.



Asal file audio tercatat di [SOUND-SOURCES.json](SOUND-SOURCES.json). Rekaman pihak ketiga terpisah dari lisensi MIT kode; lihat [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).



## Mode aplikasi

Buka **Settings → Mode Aplikasi**:

- **Klasik**: Lima aplikasi utama dengan grid aplikasi lain di bawahnya. Tetap menjadi mode awal versi universal.
- **Horizontal**: Semua aplikasi dalam satu baris. Dua posisi pertama tetap; mulai aplikasi ketiga, barisan bergeser dan aplikasi pilihan bertahan di slot kedua. Kartu di luar batas panel tersembunyi.

Mode tersimpan setelah restart. Kedua mode memakai urutan aplikasi yang sama dan mempunyai ikon masing-masing.

## Timer Menonton

Buka **Settings → Timer Menonton**, atau pilih tile di sebelah Screensaver pada Quick Settings. Atur slider **1–240 menit**, lalu aktifkan atau tekan Mulai.

Overlay kecil berisi angka sisa waktu muncul di pojok kanan atas saat aplikasi lain dibuka. Setelah waktu habis, muncul popup liquid glass dengan latar belakang gelap transparan, **hitung mundur 30 detik**, dan tombol **Tidur** serta **Batal**. Tidur langsung meminta sleep; Batal menghentikan timer. Jika dibiarkan, perintah tidur dikirim saat hitung mundur selesai.

Memerlukan Android 9+ dan layanan aksesibilitas TV Home aktif. Pada Android 12+, izin **Alarm dan pengingat** mungkin perlu diberikan melalui halaman sistem yang dibuka aplikasi. Android 8 tetap bisa memakai launcher, tetapi tidak mendukung Timer Menonton. Sleep memakai aksi penguncian layar dari aksesibilitas Android; hasilnya bergantung pada firmware TV dan perlu diuji. Ini bukan shutdown dengan hak istimewa.

Timer batal ketika layar dimatikan manual atau TV restart. Slider menyimpan durasi berikutnya; tekan Mulai / Ulangi untuk menerapkannya ke timer berjalan. Fitur ini tidak memerlukan Shizuku atau sambungan ADB.

## Personalisasi



**Widget:** Buka Settings → Widgets untuk mengatur jam, sapaan, hari/tanggal, dan jadwal salat. Jadwal salat awalnya nonaktif dan tidak memakai kota pribadi bawaan. Masukkan kota, wilayah, dan negara. Perhitungan menggunakan Muslim World League; bandingkan hasilnya dengan jadwal masjid setempat.



**Wallpaper:** Pilih API Source dan masukkan API Key jika diperlukan. Sumber yang diintegrasikan meliputi TMDB, Pixabay, Wallhaven, NASA, Wikimedia, AniList, Jikan, Waifu.im, Danbooru, dan Safebooru. Ketersediaan gambar, persyaratan kredensial, dan batas layanan berbeda. Unsplash, Pexels, serta Alpha Coders ditandai belum tersedia dalam preview ini. Integrasi online belum divalidasi lintas TV fisik.



**Screensaver:** Pilih TV Home Universal Screensaver melalui pengaturan screensaver sistem. Slider waktu memerlukan izin tulis sistem khusus; jika izin tidak tersedia, gunakan tombol pengaturan bawaan. Aktivasi otomatis dan sleep tetap bergantung pada firmware TV.



## Batasan preview



- Belum ada pengujian kompatibilitas pada TV fisik untuk versi universal ini.

- Input antena/HDMI hanya tersedia jika perangkat menyediakan layanan input yang sesuai. Integrasi TCL dipisahkan dari jalur pengaturan standar.

- Android 8–10 menggunakan pengaturan Wi-Fi bawaan untuk memilih jaringan. Pembacaan SSID bergantung pada izin dan kebijakan Android.

- Firmware dapat membatasi atau mereset layanan aksesibilitas setelah restart.

- Perubahan bahasa seluruh TV memerlukan izin atau dilakukan melalui pengaturan bahasa bawaan.

- Menu Power membuka menu daya sistem; belum menyediakan shutdown dengan hak istimewa. Shizuku tidak diperlukan.

- Paket suara yang dipilih digunakan untuk navigasi, konfirmasi, Back, membuka aplikasi, popup, toggle, serta peristiwa sleep/wake yang didukung. Hanya paket aktif yang dimuat. Bunyi sleep dapat terpotong oleh firmware.



Uji membuka aplikasi, urutan aplikasi, tombol Home/Back, quick settings, input, jaringan, keluar screensaver, bangun dari sleep, dan restart sebelum mengandalkan launcher ini sebagai Home utama.



## Build dan laporan masalah



Panduan build Windows, persyaratan JDK/Android SDK, dan perintah pengujian tersedia di [README bahasa Inggris](README.md#build-on-windows).



Saat melaporkan masalah, sertakan merek/model TV, versi Android, versi launcher, langkah pemicu, serta hasil yang diharapkan dan yang terjadi. Hapus API key dan informasi pribadi dari log atau tangkapan layar.



## Privasi dan kredit



Tidak ada SDK analitik proyek yang disertakan. Preferensi dan kredensial wallpaper disimpan dalam penyimpanan privat aplikasi. Layanan online menerima permintaan yang dibutuhkan fiturnya. Lihat [PRIVACY.md](PRIVACY.md).



Dikembangkan dari [TV Home oleh Mihir Gupta](https://github.com/mihirgupta0900/tv-home), dengan pemberitahuan hak cipta MIT asli tetap dipertahankan. Ini merupakan turunan independen.



Produk ini memakai API TMDB, tetapi tidak didukung atau disertifikasi oleh TMDB. Hak gambar online tetap dimiliki pemegang hak masing-masing.



Lihat [LICENSE](LICENSE) dan [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

