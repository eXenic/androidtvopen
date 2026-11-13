# AndroidTvOpen

Android TV için **Wake-on-LAN (WoL)** ile uzak bilgisayarı uyandırma, bilgisayar çevrimiçi olduğunda seçili **TV HDMI/Input kaynağına** otomatik geçiş yapma ve çoklu profil destekli Android TV uygulaması.

## Özellikler
- **Tek Tuşla Uyandır & Kaynağa Geç**: Hedef PC'ye Magic Packet gönderir, PC açılana (çevrimiçi olana) kadar bekler ve ardından seçili TV girişine (`HDMI 4` vb.) geçer.
- **5 Sn'de Bir WoL Tekrarı**: Bekleme sırasında paket kaybına karşı her 5 saniyede bir ek Magic Packet gönderir.
- **Çoklu Profil**: Birden fazla PC + TV Kaynak eşleşmesi ekleyip ana ekrandan tek tıkla geçiş yapabilirsiniz.
- **Tarama + Elle Giriş**:
  - Yerel ağ taraması ile PC IP/MAC bulma veya elle giriş.
  - `TvInputManager` ile TV kaynaklarını (HDMI 1–4, AV vb.) tarama veya elle ad/Input ID girişi.
- **Uzak PC Uyku / Kapatma**: İsteğe bağlı HTTP uç noktası (`pc_sleep_agent.ps1` ile Windows desteği) üzerinden TV kumandasından PC'yi uyutma.
- **Android TV D-Pad & Leanback Desteği**: TV kumandası ile tam uyumlu arayüz ve `wakeonline.jpg` tabanlı TV banner/ikon.

## Derleme
```bash
./gradlew assembleRelease
```
Hazır APK çıktısı: `AndroidTvOpen.apk`
