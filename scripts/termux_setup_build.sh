#!/usr/bin/env bash
set -euo pipefail

# Termux Kurulum & Debug APK Derleme Betiği
# Geliştirici: CEZALI GENÇ (SG) • Serhat Güner

echo "==> Termux paketleri güncelleniyor..."
pkg update -y && pkg upgrade -y

echo "==> Gerekli geliştirme ve kabuk araçları kuruluyor (openjdk-17, proot, bash, zsh, git)..."
pkg install -y openjdk-17 gradle proot bash zsh git unzip which

echo "==> Çalıştırma (+x) izinleri doğrulanıyor..."
chmod +x ./scripts/*.sh 2>/dev/null || true

if [ -n "${ANDROID_SDK_ROOT:-}" ] || [ -n "${ANDROID_HOME:-}" ]; then
  echo "==> Android SDK algılandı: ${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
else
  echo "UYARI: ANDROID_SDK_ROOT veya ANDROID_HOME tanımlı değil."
  echo "Termux içinde android-sdk-tools kurduysanız şu komutu çalıştırın:"
  echo "  export ANDROID_SDK_ROOT=\$PREFIX/share/android-sdk"
fi

echo "==> Gradle ile Debug APK derleniyor..."
gradle :app:assembleDebug --no-daemon

echo "==> Tamamlandı! Çıktı: app/build/outputs/apk/debug/app-debug.apk"
