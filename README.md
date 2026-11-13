# APK Translator Pro

**Geliştirici Kuruluş:** Cezalı 1Genç (Serhat Güner)  
**Minimum Android:** 8.0 (API 26) • **Hedef SDK:** 36 (Android 14 / Infinix X6833B uyumlu)  
**Mimari:** Kotlin + Jetpack Compose (Material 3) + MVVM + Repository + Room + DataStore

---

## Temel Özellikler

1. **Güvenli APK Dosya Yönetimi (SAF)**
   - Android Storage Access Framework (`OpenDocument`, `OpenDocumentTree`, `CreateDocument`) ile dosya ve çıktı klasörü seçimi.
   - `ApkIntegrityHelper` ile seçilen orijinal APK hiçbir koşulda değiştirilmez; işlem öncesi ve sonrası **SHA-256** bütünlük doğrulaması yapılır.
   - Zip Slip (Path Traversal) ve ZIP bombası korumaları ile sabit tampon boyutlu akış işleme.

2. **İkili APK Analizi, Kaynak Çıkarma ve Yeniden Derleme**
   - Saf Kotlin ile yazılmış **Binary `resources.arsc` (ARSC)** ve **Binary `AndroidManifest.xml` / `res/**/*.xml` (AXML)** çözümleyicisi.
   - `string`, `string-array` ve `plurals` kaynaklarını çıkarır; standart, `FLAG_OFFSET16`, `FLAG_SPARSE` ve `FLAG_COMPACT` tablolarını okur ve mevcut hedef dil bölümlerini eksiksiz birleştirir.
   - Çevrilen hedef dili yeni `ResTable_type` dil bölümü olarak `resources.arsc` içine ekler; orijinal dili ve `classes.dex` / Smali iş mantığını tamamen korur.
   - Android 11–14+ uyumluluğu için `resources.arsc` dosyasını **sıkıştırmasız (`STORED`) ve 4-byte hizalı**, native `.so` kütüphanelerini ise 4096-byte hizalı paketler.

3. **Özelleştirilebilir Koruma Kuralları ve Çeviri Motorları**
   - **Kurallar Sekmesi:** Yer tutucuların (`%1$s`, `%d`, `{var}`, `{{var}}`, `${var}`, `\n`) ve XML/HTML etiketlerinin (`<b>`, `<xliff:g>`) otomatik korunup korunmayacağını seçin; özel teknik terimleri / marka adlarını çeviri dışında bırakmak için hariç tutulan terim listesini yönetin.
   - **Çevrimdışı Motor:** Google ML Kit on-device Translation + Language Identification (dil paketi indirme, silme, yüklü/yüklü değil durumu ve aktif dil çiftini tek dokunuşla indirme).
   - **Çevrimiçi Motor:** Gerçek HTTP API istemcisi (LibreTranslate, Google Cloud Translation v2, DeepL, Özel REST API). Bağlantı kesilirse `Otomatik` modda ML Kit çevrimdışına düşer.
   - **Veri Aktarım Onayı:** Çevrimiçi çeviri başlamadan önce açık kullanıcı onayı ister.
   - **Room Önbelleği & Android Keystore:** Çevirileri yerel Room veritabanında önbellekler; API adresi ve anahtarını Android Keystore AES-256-GCM ile şifreler.

4. **Gerçek APK İmzalama ve Doğrulama (`apksig`)**
   - Yerel **PKCS#12 (RSA-2048 + X.509 v3)** anahtarı oluşturur ve `com.android.tools.build:apksig` ile **v1 (JAR), v2 ve v3** imzalarını uygular.
   - `ApkVerifier` ile çıktı APK'sını kriptografik olarak doğrular.
   - **Önemli Uyarı:** Yeniden imzalanan APK orijinal geliştirici imzasını taşımaz; Play Lisans (`CHECK_LICENSE`) veya imza doğrulaması yapan uygulamalarda uyumsuzluk oluşabilir.

---

## Kurulum ve Derleme (Android Studio & Gradle)

### Gereksinimler
- Android Studio Ladybug / Meerkat veya üzeri
- JDK 17+ (Compile options Java 11)
- Android SDK 36 (Minimum SDK 26)

### Debug (Ayıklama) APK Oluşturma
```bash
gradle :app:assembleDebug
```
Çıktı konumu: `app/build/outputs/apk/debug/app-debug.apk`

### Birim ve Robolectric Testlerini Çalıştırma
```bash
gradle :app:testDebugUnitTest
```

### Release APK Oluşturma ve İmzalama Talimatları
1. Güvenli bir yükleme anahtarı (`upload-keystore.jks`) oluşturun:
   ```bash
   keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Ortam değişkenlerini ayarlayın:
   ```bash
   export KEYSTORE_PATH=/yol/my-upload-key.jks
   export STORE_PASSWORD="guclu_depo_sifresi"
   export KEY_PASSWORD="guclu_anahtar_sifresi"
   ```
3. Release APK veya AAB oluşturun:
   ```bash
   gradle :app:assembleRelease
   # veya Play Store paketi için:
   gradle :app:bundleRelease
   ```

---

## Açık Kaynak Bağımlılık ve Lisans Bildirimleri
- **Android `apksig`** — Apache License 2.0
- **Jetpack Compose, Material 3, Room, DataStore, Navigation, DocumentFile** — Apache License 2.0
- **OkHttp & Moshi** — Apache License 2.0
- **Google ML Kit Translate & Language Identification** — Google APIs Terms of Service

---

**CEZALI 1GENÇ © • SERHAT GÜNER • BAĞIMSIZ DİJİTAL TEKNOLOJİ OLUŞUMU**  
*Teknolojiyi Keşfet. Yazılımı Geliştir. Dijital Dünyayı Yönet.*
