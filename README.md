# Apk Çeviricisi Plus Ultra

**Geliştirici:** CEZALI GENÇ (SG) — Serhat Güner  
**Platform:** Android (Kotlin • Jetpack Compose • Material 3 • Coroutines • WorkManager)  
**Paket Kimliği:** `com.aistudio.apktranslatorpro.cz1g`

---

## Özellikler

- **6 Sekmeli Ana Yapı (`Scaffold` + `BottomNavigationBar` / `NavigationRail`):**
  - `Çalıştır`: SAF salt okunur APK seçimi, çoklu APK kuyruğu, otomatik kaynak dil tespiti, TR/EN/DE/FR/AR/100+ hedef dil, Gemini AI / Çevrimiçi REST (DeepL, LibreTranslate, Google v2) / Çevrimdışı ML Kit motorları, canlı log ve tek tıkla yükle/paylaş.
  - `Araçlar`: `ApkEditorDashboard` (Linux & PRoot dağıtım yönetimi, terminal oturum durdurma, paket yöneticisi durumu, `PermissionFixer` ve telefon sahibi izin merkezi), Decompiler, Rebuilder, Yan Yana XML Diff, Smali & DEX inceleyici, Manifest Editörü, Git & Git Clone.
  - `Önizleme`: Çeviri listesi, Türkçe karakter/uzun metin uyarı telemetrisi, kilit/geri alma, Bul & Değiştir, `strings.xml` ve İkili AXML canlı önizleme.
  - `Kurallar`: Placeholder (`%s`, `%1$d`, `{0}`), XML/HTML etiketi ve teknik terim koruma kuralları.
  - `Modeller`: Toplu çoklu dosya & çoklu dil kuyruğu (`LanguageManagerViewModel`), Room önbelleği, ML Kit çevrimdışı dil paketleri.
  - `Ayarlar`: Deneme / Google hesabı yönetimi, TR/EN/DE arayüz dili, Karanlık/Açık Ultra tema, AES-256-GCM şifreli API ayarları, RSA-2048 v1/v2/v3 imza anahtarı, çalışma zamanı izin merkezi.

## Temel Mimari Bileşenler

- **`ApkProcessor` (`core/apk/ApkProcessor.kt`)**: Coroutines (`Dispatchers.IO` / `Dispatchers.Default`) üzerinde `ZipInputStream` ile güvenli APK çıkarma, `resources.arsc` ve `AndroidManifest.xml` çözümleme, kaynak ID'lerini (`id`, `key`, `kind`, `subKey`, `localeQualifier`) koruyarak placeholder güvenli yerelleştirme, 4-byte hizalı paketleme ve `ApkSignerEngine` (`apksig` v1/v2/v3) hazırlığı.
- **`BackgroundTranslationWorker` (`core/work/BackgroundTranslationWorker.kt`)**: Jetpack WorkManager (`CoroutineWorker`) ile kesintisiz arka plan çeviri, derleme ve imzalama.
- **`ApkRecoveryValidator` (`core/apk/ApkRecoveryValidator.kt`)**: Format belirteci (`%s`, `%d`, `%1$s`) uyuşmazlığı veya bozuk HTML/XML etiketlerinden kaynaklı `IllegalFormatException` / `InflateException` çökmelerini tespit edip hatalı girdileri otomatik olarak orijinaline kurtarır ve `resources.arsc` başlığını doğrular.
- **`PermissionFixer` (`core/environment/PermissionFixer.kt`)**: Dahili `filesDir/linux` altındaki ikili dosyalarda (özellikle `proot`, `bash`, `zsh` ve paket yöneticileri) özyinelemeli olarak `File.setExecutable(true)` doğrular ve `ProcessBuilder` ortam değişkenlerini (`HOME`, `PREFIX`, `TMPDIR`, `PROOT_TMP_DIR`, `PATH`, `LD_LIBRARY_PATH`, `SHELL`) uygulama içi yollara yönlendirir.
- **`ApkEditorDashboard` (`ui/components/ApkEditorDashboard.kt`)**: Kurulu Linux dağıtımlarını geçiş anahtarlarıyla yöneten, aktif terminal oturumlarını canlı çıktı ve **Durdur** butonuyla kontrol eden, paket yöneticisi durumunu gösteren ve çalışma zamanı cihaz izinlerini (`POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`, `SAF` klasörü) Google Play politikalarına uygun biçimde isteyen kart tabanlı Compose paneli.

## Derleme ve Kurulum

### Gradle (Standart / Android Studio / AndroidIDE)
```bash
gradle :app:assembleDebug
```

### Termux Üzerinden Kurulum & Derleme
```bash
bash scripts/termux_setup_build.sh
```

### NixOS / Nix Flake Ortamı
```bash
nix develop
gradle :app:assembleDebug
```
