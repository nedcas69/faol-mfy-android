# «Faol MFY» — Android ilova

Mahalla yettiligi hududiy faollik monitoringi. Kotlin + Jetpack Compose.
APK GitHub Actions'da yig'iladi — kompyuteringizga Android Studio kerak emas.

Minimal Android: **8.0 (API 26)**. Maqsadli: Android 15 (API 35).

---

## 1. GitHub'ga qo'yish

```bash
cd faol-mfy-android
git init
git add .
git commit -m "Faol MFY Android — birinchi versiya"
git branch -M main
git remote add origin https://github.com/<foydalanuvchi>/faol-mfy-android.git
git push -u origin main
```

Push qilgandan keyin **Actions** bo'limiga o'ting — yig'ish avtomatik boshlanadi.
5–8 daqiqadan keyin **Artifacts** bo'limidan `FaolMFY-apk` ni yuklab oling.

> Repozitoriy **public** bo'lsa Actions butunlay bepul.
> Private bo'lsa oyiga 2000 daqiqa bepul — bu yig'ish ~6 daqiqa oladi, yetadi.

## 2. Server manzilini ko'rsatish

Ilova qaysi serverga ulanishini bilishi kerak. Uch xil yo'l:

**A. Repozitoriya o'zgaruvchisi (tavsiya etiladi)**
Settings → Secrets and variables → Actions → Variables → New variable:
- Nomi: `API_BASE_URL`
- Qiymati: `http://100.x.x.x:8020/` (DGX'ning Tailscale manzili)

**B. Qo'lda ishga tushirishda**
Actions → «APK yig'ish» → Run workflow → `api_base_url` maydonini to'ldiring.

**C. Kodda** — `app/build.gradle.kts`, `apiBaseUrl` ning standart qiymati.

> Manzil oxirida **slesh bo'lishi shart**: `http://100.x.x.x:8020/`
> `localhost` yoki `127.0.0.1` YOZMANG — telefon uchun bu o'zining ichki manzili.

**Tailscale haqida:** DGX'ingiz Tailscale'da bo'lgani uchun eng oson yo'l —
telefonlarga ham Tailscale o'rnatib, bir tarmoqqa qo'shish. Shunda ilova
mahalladan turib ham serverga uladi, port ochish kerak emas.

## 3. Imzo kaliti (ixtiyoriy, lekin tavsiya etiladi)

Kalitsiz ham APK yig'iladi (debug imzo bilan) va o'rnatiladi. Lekin kalit
qo'ysangiz, keyingi versiyalar **eskisining ustiga yangilanadi** —
aks holda har safar o'chirib qayta o'rnatish kerak bo'ladi.

Kalit yaratish (istalgan JDK o'rnatilgan kompyuterda, bir marta):

```bash
keytool -genkey -v -keystore release.keystore -alias faolmfy \
  -keyalg RSA -keysize 2048 -validity 10000

base64 -w0 release.keystore > keystore.b64    # Linux
base64 -i release.keystore | tr -d '\n' > keystore.b64   # macOS
```

Settings → Secrets and variables → Actions → Secrets ga 4 ta secret qo'shing:

| Nomi | Qiymati |
|---|---|
| `KEYSTORE_BASE64` | `keystore.b64` fayl ichidagi matn |
| `KEYSTORE_PASSWORD` | keystore paroli |
| `KEY_ALIAS` | `faolmfy` |
| `KEY_PASSWORD` | kalit paroli |

`release.keystore` faylni **yo'qotmang va git'ga qo'ymang** — u yo'qolsa
yangilanishlarni chiqarib bo'lmaydi.

## 4. Telefonga o'rnatish

1. APK ni telefonga yuboring (Telegram, USB, yoki GitHub Release havolasi)
2. Fayl menejeridan oching
3. «Noma'lum manbalardan o'rnatish» → ruxsat bering
4. O'rnating

Play Protect ogohlantirishi chiqishi mumkin — «Baribir o'rnatish» ni bosing.
Bu Play Store'dan tashqari ilovalar uchun odatiy holat.

## 5. Sinov tartibi (Xiaomi, Redmi, Honor)

Bu uch brend eng agressiv «battery killer»lardan. Sinov rejasi:

**1-kun — asosiy tekshiruv**
1. Ilovani o'rnating, faollashtirish kodi bilan kiring
2. Sozlash sehrgarining **oltita qadamini ham** oxirigacha bajaring —
   ayniqsa 6-qadam (avtoishga tushirish)
3. Kun bo'yi telefonni odatdagidek ishlating
4. Kechqurun tekshiring: `/api/v1/admin/health/by-brand`

**2-kun — og'ir sharoit**
1. Ilovani «recent apps» dan surib tashlang → servis qayta tiklanadimi?
2. Telefonni 2 soat qulflab qo'ying → nuqtalar kelayotganmi?
3. Aviarejimni 1 soat yoqing → offline yozilib, keyin yuborilyaptimi?
4. Telefonni o'chirib yoqing → kuzatuv o'zi tiklanadimi?

**Nimaga qarash kerak**
- `service_killed` hodisalari soni (admin panelida ko'rinadi)
- Batareya sarfi — 9 soatlik kuzatuvda 12% dan oshmasligi kerak
- Kunlik nuqtalar soni — 700–1200 oralig'ida bo'lishi kerak
- Uzilishlar (`tracking_gap`) — 30 daqiqadan uzunlari

**Muhim:** Honor'da 6-qadamni bajarmasangiz, ilova 20–40 daqiqada o'ladi.
Bu xato emas — MagicOS shunday ishlaydi. Sinovni ataylab **bir marta
6-qadamsiz** ham o'tkazing: shunda watchdog uni qanday ushlashini
va `service_killed` hodisasi yozilishini ko'rasiz.

## 6. Ilova ichida nima bor

| Qism | Fayl |
|---|---|
| Kuzatuv servisi | `location/TrackingService.kt` |
| Ish oynasi (09:00–18:00) | `util/WorkWindow.kt`, `location/AlarmScheduler.kt` |
| Lokal outbox | `data/local/` (Room) |
| Sinxronizatsiya | `sync/SyncManager.kt` |
| **Watchdog** (servis o'lganini aniqlash) | `sync/WatchdogWorker.kt` |
| **Brendga moslashgan yo'riqnoma** | `util/OemHelper.kt` |
| Ro'yxatdan o'tish | `ui/screens/ActivationScreen.kt` |
| Sozlash sehrgari | `ui/screens/SetupWizardScreen.kt` |
| Bosh ekran | `ui/screens/HomeScreen.kt` |

**Kuzatuv parametrlari serverdan keladi** (`/api/v1/refs/config`):
masofa filtri, intervallar, paket hajmi. Ularni o'zgartirish uchun yangi
APK tarqatish shart emas — serverdagi `.env` ni tahrirlab, `docker compose
restart api` qilsangiz kifoya.

## 7. Ma'lumot yo'qotmaslik kafolatlari

- Har nuqta avval **lokal SQLite** ga yoziladi, keyin yuboriladi
- Internet bor-yo'qligidan qat'i nazar bir xil kod yo'li
- Har nuqta `device_id:client_seq` kaliti bilan ketadi — server dublikatni
  tashlaydi, shuning uchun qayta yuborish xavfsiz
- Sinxronlangan yozuvlar 48 soatdan keyin o'chadi, yuborilmagani 14 kun turadi
- Servis o'lsa — WorkManager 15 daqiqada qayta yoqadi va hodisani yozadi
- Telefon o'chib yonsa — `BootReceiver` kuzatuvni tiklaydi

## 8. Ma'lum cheklovlar

- **Qadam sanagichi yo'q telefonlar.** Arzon modellarda `TYPE_STEP_COUNTER`
  bo'lmasligi mumkin. Ilova buni aniqlab `no_step_sensor` hodisasini yuboradi.
  Bunday telefonlarda «telefonni qoldirib ketish» tekshiruvi ishlamaydi.
- **Aniq alarm ruxsati.** Android 12+ da `SCHEDULE_EXACT_ALARM` cheklangan.
  Ilova taxminiy alarmga o'tadi — 09:00 o'rniga 09:05 da yonishi mumkin.
  Watchdog buni 15 daqiqada tuzatadi.
- **Play Store yo'q.** Fon joylashuvi uchun Google alohida tekshiruv talab
  qiladi. Pilot uchun APK to'g'ridan-to'g'ri tarqatiladi.
- **iOS yo'q.** Apple Developer Program yiliga $99 + macOS talab qiladi.

---

## 9. Rasm biriktirish qanday ishlaydi

**Foydalanuvchi nuqtai nazaridan:** bosh ekranda har bir to'xtash kartochkasida
«Rasm» va «Izoh» tugmalari bor. Rasm tizim kamerasi orqali olinadi.

**Muhim cheklov:** to'xtash bir joyda **kamida 5 daqiqa** turgandan keyin
paydo bo'ladi. Shundan oldin kartochka yo'q, demak rasm ham qo'sha olmaydi.
Bu ataylab shunday: aks holda "mahalla oldidan mashinada o'tib ketib rasm
olish" mumkin bo'lardi.

**Texnik zanjir:**

```
Kamera → ilova papkasiga JPEG
       → 1280 px ga siqiladi (~250 KB), EXIF burilishi tuzatiladi
       → photo_queue jadvaliga yoziladi (lokal SQLite)
       → internet bo'lganda: presign → PUT (MinIO) → confirm
       → server 120 kunlik o'chirish sanasini belgilaydi
```

Uch qadamning har biri bazada alohida belgilanadi. Agar 250 KB fayl 90%
yuklanib uzilsa, keyingi urinishda presign qaytadan so'ralmaydi — faqat
PUT takrorlanadi. Presigned URL 15 daqiqada eskirsa, avtomatik yangisi
olinadi.

**Galereyadan eski rasm qo'yish mumkin emas:** ilova `TakePicture`
kontraktini ishlatadi va rasm to'g'ridan-to'g'ri o'z FileProvider papkasiga
yoziladi. Galereya tanlagichi umuman ochilmaydi.

**Rasmga koordinata qanday biriktiriladi:** avval EXIF dan o'qiladi (ba'zi
kameralar yozadi), bo'lmasa o'sha paytdagi oxirgi GPS nuqtasi olinadi.
Server bu koordinatani shu vaqtdagi trek nuqtasi bilan solishtiradi;
300 metrdan uzoq bo'lsa `photo_location_mismatch` hodisasi yoziladi.

**MinIO port 9000 telefon uchun ochiq bo'lishi shart.** Rasm API orqali
o'tmaydi — telefon to'g'ridan-to'g'ri MinIO ga yuklaydi. Tailscale
ishlatsangiz ikkala port ham (`8020` va `9000`) o'zi ishlaydi. `.env` dagi
`S3_ENDPOINT_PUBLIC` aynan telefon ko'radigan manzil bo'lishi kerak.

**Izohlar ham offline ishlaydi:** `note_queue` jadvaliga yoziladi va
internet bilan yuboriladi.

## 10. Rasm va izoh vaqtga bog'langan — nima uchun

To'xtashlar **hisoblanadigan** ma'lumot: har `recompute_day` da GPS
nuqtalaridan qaytadan quriladi va ID'lari o'zgaradi. Shuning uchun rasm va
izoh `stop_id` ga emas, **vaqtga** bog'lanadi:

- rasm → `taken_at`
- izoh → to'xtashning `started_at` qiymati (langar)

Qayta hisoblashda server ularni vaqt oralig'i bo'yicha yangi to'xtashlarga
qaytadan biriktiradi. Natijada to'xtashlarni xohlagancha qayta qurish
mumkin va xodimning ishi yo'qolmaydi.

---

## 11. Backend manzilini avtomatik topish (discovery)

Quick tunnel (`trycloudflare.com`) manzili har qayta ishga tushganda
o'zgaradi. Buni har safar APK ga yozib qayta tarqatmaslik uchun **discovery
URL** patterni ishlatiladi.

**Qanday ishlaydi:**

```
APK ichida  →  raw.githubusercontent.com/.../config/endpoint.json  (O'ZGARMAS)
                            ↓ o'qiydi
              { "api_base_url": "https://<tunnel>.trycloudflare.com/" }
                            ↓
              ilova shu manzilni ishlatadi
```

Ilova ishga tushganda GitHub'dagi `config/endpoint.json` ni o'qib backend
manzilini oladi. Tunnel o'zgarsa — serverdagi skript JSON'ni yangilaydi,
ilova keyingi ishga tushishda yangi manzilni o'zi topadi. APK'ga tegilmaydi.

**Internet yo'q bo'lsa:** oxirgi ishlagan manzil DataStore'da saqlanadi va
o'shandan foydalaniladi. U ham bo'lmasa — APK ichidagi standart manzil.

**Domen olgandan keyin:** JSON'ga doimiy manzil (masalan
`https://api.faolmfy.uz/`) yoziladi, skript to'xtatiladi, mexanizm
shunchaki o'sha bir xil qiymatni qaytaraveradi. Hech narsa buzilmaydi.

### Server tomonida — tunnel menejeri

`faolmfy-tunnel.sh` skripti (alohida paketda) ikkala tunnelni ko'taradi,
manzillarni ushlaydi, backend `.env` dagi `S3_ENDPOINT_PUBLIC` ni yangilab
`api` konteynerni qayta ishga tushiradi, va GitHub'dagi JSON'ni yangilaydi.
Tunnel uzilsa — hammasini qayta bajaradi.
