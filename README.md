# DataBar — widget pemakaian data harian (Android)

Widget home screen 4x2: bar pemakaian **14 hari terakhir** + **total month-to-date** + rata-rata harian.

## Sumber data
`NetworkStatsManager.querySummaryForDevice()` — dihitung lokal di HP. App **tidak punya izin
INTERNET**, jadi tidak ada data yang dikirim ke mana pun.

## Izin
`PACKAGE_USAGE_STATS` ("Usage access") — harus diaktifkan manual, bukan dialog runtime:
buka app → tombol "Aktifkan izin Usage access" → Settings → Special access → Usage access → DataBar.

## Setelan
- **Tanggal mulai siklus (1-28)** — samakan dengan tanggal mulai paket operator, supaya
  "month to date" cocok dengan kuota. Default 1.
- **Ikut hitung Wi-Fi** — default mati (hanya seluler).

## Build
```bash
export JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android
~/.local/gradle-8.7/bin/gradle assembleDebug
```

## Batasan yang perlu diketahui
- Angka ini hitungan **di HP**, bisa beda tipis dengan hitungan operator.
- Data Usage Access hanya mencakup pemakaian sejak boot pertama setelah izin diberikan;
  Android menyimpan bucket harian sesuai siklus, bukan selamanya.
- Widget refresh mengikuti `updatePeriodMillis` (30 menit) dan saat layar/app dibuka;
  tidak realtime.
