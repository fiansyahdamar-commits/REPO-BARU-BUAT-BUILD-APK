# Zall RW Admin

Aplikasi Android admin terpisah untuk mengelola kode redeem Zall RW. APK utama Zall RW tidak diubah oleh repositori ini.

## Fitur
- Login admin dengan sesi perangkat yang berlaku 30 hari; sesi disimpan agar panel tidak meminta password setiap dibuka.
- Membuat kode redeem dengan mode SINGLE (1 perangkat) atau MULTI (batas perangkat dapat diatur).
- Mengatur masa aktif 7, 30, 90, 365 hari, atau permanen setelah redeem.
- Menampilkan petunjuk kode, mode, batas perangkat, durasi, dan status.
- Mengaktifkan atau menonaktifkan kode yang sudah dibuat.
- Melihat riwayat aktivasi dan menyalin kode serta password redeem saat kode dibuat.

## Backend
Menggunakan Supabase Edge Function yang sudah ada:
`https://nvxgpmbakbzjyfjstdwl.supabase.co/functions/v1/admin-api-v2`

Admin API memeriksa password panel dan mengelola sesi admin di server. Setiap kode yang dibuat memiliki password redeem tersendiri. Endpoint redeem memeriksa password sebelum memproses batas perangkat dan masa aktif melalui RPC database yang hanya dapat dijalankan oleh service role.

## Build dan pengujian
GitHub Actions membangun `app-debug.apk`, memasangnya di emulator Android, lalu menguji apakah proses aplikasi tetap hidup 25 detik setelah dibuka. Tes startup ini membuktikan APK dapat diluncurkan, bukan pengujian end-to-end login atau transaksi redeem produksi.

