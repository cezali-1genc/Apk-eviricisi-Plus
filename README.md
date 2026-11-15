# Apk Çeviricisi Plus

**Geliştirici Kuruluş:** Cezalı 1Genç (Serhat Güner)  
**Paket Kimliği (`applicationId`):** `com.aistudio.apktranslatorpro.cz1g`  
**Minimum Android:** 8.0 (API 26) • **Hedef SDK:** 36 (Android 14 / 15 / 16 uyumlu)  
**Mimari:** Kotlin + Jetpack Compose (Material 3) + MVVM + Repository + Room + DataStore + WorkManager

---

## Öne Çıkan Özellikler

1. **Güvenli APK Dosya Yönetimi (SAF) ve Çoklu Kuyruk**
   - Tekli veya çoklu APK seçimi, toplu APK içe aktarma ve WorkManager arka plan işlem kuyruğu.
   - Paket adı, uygulama adı, gerçek başlatıcı ikonu (`ApkIconExtractor`), minSdk/targetSdk, sürüm ve boyut analizi.
   - `ApkSecurityUtils` / `ApkIntegrityHelper` ile işlem öncesi ve sonrası **SHA-256** doğrulama, orijinal APK koruma, ZIP bomb ve Zip Slip (path traversal) koruması.
   - APK yedekleme, arşiv yöneticisi, Room tabanlı Son Kullanılan APK geçmişi ve iki APK arasında meta veri/izin/SHA karşılaştırma ekranı (`ApkCompareScreen`).

2. **105+ Dil, Otomatik Algılama ve 3 Arayüz Dili**
   - `LanguageOption` ile **105+ dil** desteği (Türkçe, İngilizce, Almanca, Fransızca, Arapça, Rusça, İspanyolca, İtalyanca, Japonca, Korece, Çince, Portekizce, Farsça, Azerice, Hollandaca, Lehçe, Ukraynaca ve 88+ ek dil; RTL bayrakları dahil).
   - APK `resources.arsc` analizi ile kaynak dil otomatik tespiti ve manuel seçim.
   - Uygulama arayüzünde **Türkçe (TR), English (EN), Deutsch (DE)** dil desteği + Dark/Light/Sistem temaları.

3. **Çoklu Çeviri Motorları ve Akıllı Çeviri Araçları**
   - **Çevrimdışı:** Google ML Kit On-Device Translation + Language Identification (dil paketi indirme, silme, güncelleme).
   - **Çevrimiçi:** REST API (LibreTranslate, Google Cloud Translation v2, DeepL, Özel REST) + Firebase AI Logic (Gemini). Ağ kesildiğinde `Otomatik` modda çevrimdışı motora geçiş.
   - **Akıllı Araçlar:** Yan yana önizleme düzenleyici, arama/değiştirme, satır kilitleme (`isLocked`), önceki çeviriye geri alma (`previousTranslatedText`), otomatik biçimlendirme/noktalama düzeltme, kalite puanlama, alternatif öneriler, özel kullanıcı sözlüğü ve JSON / XML / CSV dışa aktarma.

4. **Apktool Önbellek, XML Editör, Yan Yana XML Diff ve Smali/DEX**
   - `ApktoolService` ile APK doğrulama, `originals/` ve `decompiled/` çalışma dizini, `strings.xml` toplu anahtar yeniden adlandırma (layout referanslarıyla birlikte).
   - **Yan Yana XML Karşılaştırma (`XmlDiffComparisonScreen`):** Orijinal ve düzenlenmiş XML dosyalarını LCS algoritmasıyla satır satır renkli diff olarak gösterir ve `.diff` yaması dışa aktarır.
   - Renkli **Smali Bytecode Editörü**, DEX sınıf/metot inceleyici ve Java görünümü.

5. **Telefon Bağlantısı ile Saf Kotlin Git & Git Clone Entegrasyonu**
   - Harici `git` ikili dosyasına ihtiyaç duymadan Android cihaz üzerinden doğrudan HTTPS Git Smart HTTP `/info/refs?service=git-upload-pack` keşfi ve GitHub/GitLab/Gitea HTTPS klonlama.
   - Çalışma dizini `git status`, `XmlDiffEngine` ile satır farkı görüntüleme, yerel commit oluşturma, APK `strings.xml` içe/dışa aktarma ve PAT (Personal Access Token) ile GitHub Contents API üzerinden push.

6. **Gerçek APK Yeniden Oluşturma ve v1/v2/v3 İmzalama (`apksig`)**
   - `resources.arsc` (`FLAG_OFFSET16`, `FLAG_SPARSE`, `FLAG_COMPACT`) ve Binary XML yeniden derleme; `resources.arsc` için `STORED` + 4-byte hizalama, `.so` için 4096-byte hizalama.
   - RSA-2048 PKCS#12 anahtar üretimi, **v1 / v2 / v3** imza uygulama ve `ApkVerifier` sertifika doğrulama.

---

## Kurulum ve Derleme (Android Studio & Gradle)

### Gereksinimler
- Android Studio Ladybug / Meerkat veya üzeri
- JDK 17+ (Compile options Java 11)
- Android SDK 36 (Minimum SDK 26)

### 1. Debug APK Oluşturma
```bash
gradle :app:assembleDebug
```
Çıktı konumu: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Birim ve Robolectric Testlerini Çalıştırma
```bash
gradle :app:testDebugUnitTest
```
*(Firestore Emulator kuralları için: `firebase emulators:exec --only auth,firestore "gradle :app:testDebugUnitTest"`)*

### 3. Release APK Oluşturma ve İmzalama
1. Güvenli bir yükleme anahtarı (`my-upload-key.jks`) oluşturun:
   ```bash
   keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Ortam değişkenlerini ayarlayın (anahtar parolalarını kaynak koda veya `local.properties`'e yazmayın):
   ```bash
   export KEYSTORE_PATH=/yol/my-upload-key.jks
   export STORE_PASSWORD="guclu_depo_sifresi"
   export KEY_PASSWORD="guclu_anahtar_sifresi"
   ```
3. Release APK veya App Bundle (AAB) derleyin:
   ```bash
   gradle :app:assembleRelease
   # veya Play Store paketi için:
   gradle :app:bundleRelease
   ```
Çıktı konumu: `app/build/outputs/apk/release/app-release.apk`

---

**CEZALI 1GENÇ © • SERHAT GÜNER • BAĞIMSIZ DİJİTAL TEKNOLOJİ OLUŞUMU**  
*Teknolojiyi Keşfet. Yazılımı Geliştir. Dijital Dünyayı Yönet.*

