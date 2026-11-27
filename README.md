# Zall RW Admin

Aplikasi Android admin terpisah untuk mengelola kode redeem Zall RW. APK utama Zall RW tidak diubah oleh repositori ini.

## Fitur
- Login admin dengan sesi perangkat yang berlaku 30 hari; sesi disimpan agar panel tidak meminta password setiap dibuka.
- Membuat kode redeem dengan mode SINGLE (1 perangkat) atau MULTI (batas perangkat dapat diatur).
- Mengatur masa aktif 7, 30, 90, 365 hari, atau permanen setelah redeem.
- Menampilkan petunjuk kode, mode, batas perangkat, durasi, dan status.
- Mengaktifkan atau menonaktifkan kode yang sudah dibuat.
- Melihat riwayat aktivasi dan menyalin kode redeem saat kode dibuat; pengguna cukup memasukkan kode tanpa password redeem.

## Backend
Menggunakan Supabase Edge Function:
`https://vpcavoumahovwzeypxqa.supabase.co/functions/v1/admin-api`

Password tetap digunakan untuk setup dan login admin. Kode redeem tidak memiliki password terpisah: pengguna cukup memasukkan kode, sedangkan server memeriksa status kode, masa aktif, dan batas perangkat melalui RPC database yang hanya dapat dijalankan oleh service role.

## Build dan pengujian
GitHub Actions membangun `app-debug.apk`, memasangnya di emulator Android, lalu menguji apakah proses aplikasi tetap hidup 25 detik setelah dibuka. Tes startup ini membuktikan APK dapat diluncurkan, bukan pengujian end-to-end login atau transaksi redeem produksi.

