# cs-Kekik - CloudStream Türkçe Eklentileri

Bu repository, CloudStream uygulaması için Türkçe içerik sağlayan eklentileri içerir.

## 📱 Desteklenen Platformlar

- **AnimeciX** - Anime içerikleri
- **DiziBox** - Türk dizileri
- **FilmBip** - Film içerikleri
- **DiziPal** - Dizi içerikleri
- **JetFilmizle** - Film ve dizi içerikleri
- **FullHDFilm** - HD film içerikleri
- **DiziMom** - Dizi içerikleri
- **DiziKorea** - Kore dizileri
- **Donghuastream** - Anime içerikleri
- Ve daha fazlası...

## 🚀 Kurulum

### Otomatik Kurulum (Önerilen)

1. CloudStream uygulamasını açın
2. Ayarlar > Eklentiler bölümüne gidin
3. "Eklenti Ekle" butonuna tıklayın
4. Aşağıdaki URL'yi girin:
   ```
   https://github.com/keyiflerolsun/cs-Kekik
   ```
5. İstediğiniz eklentileri seçin ve yükleyin

### Manuel Kurulum

1. Bu repository'yi klonlayın
2. Gradle ile build edin:
   ```bash
   ./gradlew build
   ```
3. Oluşan `.aar` dosyalarını CloudStream'e yükleyin

## 📋 Gereksinimler

- Android 5.0+ (API 21+)
- CloudStream 3.x
- İnternet bağlantısı

## 🔧 Build

```bash
# Tüm eklentileri build et
./gradlew build

# Belirli bir eklentiyi build et
./gradlew :AnimeciX:build
```

## 📝 Lisans

Bu proje MIT lisansı altında lisanslanmıştır.

## 🤝 Katkıda Bulunma

1. Bu repository'yi fork edin
2. Yeni bir branch oluşturun (`git checkout -b feature/yeni-ozellik`)
3. Değişikliklerinizi commit edin (`git commit -am 'Yeni özellik eklendi'`)
4. Branch'inizi push edin (`git push origin feature/yeni-ozellik`)
5. Pull Request oluşturun

## ⚠️ Uyarı

Bu eklentiler sadece eğitim amaçlıdır. Telif hakkı olan içerikleri indirmekten sorumlu değiliz. Lütfen yerel yasalarınıza uygun hareket edin.

## 📞 İletişim

- **Geliştirici:** @keyiflerolsun
- **GitHub:** https://github.com/keyiflerolsun
- **Telegram:** @KekikAkademi

## 🔄 Güncellemeler

Bu repository otomatik olarak güncellenir. Yeni sürümler için CloudStream uygulamasındaki eklenti yöneticisini kontrol edin.

---

**Not:** Bu eklentiler CloudStream'in resmi eklentileri değildir. Sorun yaşarsanız lütfen GitHub Issues bölümünde bildirin.
