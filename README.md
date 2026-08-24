# 📺 DreamTV — Лучший IPTV плеер для TV Box

[![Android](https://img.shields.io/badge/Android-5.0+-green.svg)](https://android.com)
[![API](https://img.shields.io/badge/API-21+-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-TV-blue.svg)](https://developer.android.com/jetpack/compose)

🎬 **Современный IPTV плеер для Android TV Box** с поддержкой управления с пульта, веб-пультом со смартфона и голосовым поиском.

---

## ✨ Особенности

### 🎯 Для пользователей
- **📺 Поддержка M3U8** — загружайте свои плейлисты IPTV каналов
- **🎮 Управление с пульта** — полностью оптимизировано для TV Box
- **📱 Веб-пульт** — управляйте приставкой со смартфона через браузер
- **🔊 Голосовой поиск** — находите каналы голосом (Vosk AI)
- **🚀 Автозапуск** — приложение стартует при включении TV Box
- **🌙 Современный UI** — красивый тёмный дизайн в стиле Material 3
- **⚡ Два плеера** — IjkPlayer + VLC для максимальной совместимости

### 🛠️ Для разработчиков
- **Jetpack Compose** — современный декларативный UI
- **TV Material Design** — гайдлайны Android TV
- **Ktor + WebSocket** — real-time синхронизация веб-пульта
- **NanoHTTPD** — лёгкий HTTP сервер на устройстве
- **ZXing** — генерация QR-кодов для быстрого подключения
- **Coil** — загрузка логотипов каналов

---

## 📱 Как использовать

### 1️⃣ Установка плейлиста

1. Откройте приложение на TV Box
2. Перейдите в **Настройки** (последняя вкладка)
3. Вставьте ссылку на **M3U8 плейлист** или выберите локальный файл
4. Нажмите **"Загрузить каналы"**

> 💡 **Где взять плейлист?**
> - Найдите бесплатные IPTV плейлисты в интернете (по запросу "free iptv m3u8 playlist")
> - Используйте плейлисты от вашего IPTV провайдера
> - Создайте свой собственный плейлист в формате `.m3u`

### 2️⃣ Просмотр каналов

- Используйте **стрелки пульта** для навигации по сетке каналов
- Нажмите **OK** для переключения канала
- **Громкость** регулируется кнопками пульта
- **Назад** — возврат к списку каналов

### 3️⃣ Веб-пульт со смартфона

1. Включите **Веб-пульт 📱** в настройках приложения
2. Нажмите **"📲 Показать QR-код пульта"**
3. Отсканируйте QR-код камерой смартфона
4. Управляйте приставкой из браузера!

**Возможности веб-пульта:**
- 📺 Переключение каналов с логотипами
- 🔍 Поиск по названию канала
- ⏯️ Play/Pause/Next/Prev
- 🔊 Контроль громкости
- 🔄 Real-time синхронизация состояния

> ⚠️ **Важно:** Веб-пульт работает только в **локальной сети Wi-Fi**. Оба устройства должны быть подключены к одному роутеру.

### 4️⃣ Голосовой поиск

1. Нажмите на иконку **микрофона** в поиске
2. Произнесите название канала
3. Приложение найдёт совпадения

---

## 🏗️ Архитектура приложения

```
dreamTV/
├── app/src/main/
│   ├── java/com/example/dreamtv/
│   │   ├── MainActivity.kt          # Основная активность + UI на Compose
│   │   ├── WebRemoteService.kt      # HTTP сервер + WebSocket для веб-пульта
│   │   ├── BootReceiver.kt          # Автозапуск при загрузке TV
│   │   └── ui/theme/                # Тема Material 3 (Color, Type, Theme)
│   ├── assets/                       # Ресурсы (шрифты, данные)
│   └── res/                          # Android ресурсы (layout, drawable)
├── build.gradle.kts                  # Конфигурация проекта
└── settings.gradle.kts               # Настройки репозиториев
```

### 🔧 Технологии

| Компонент | Технология |
|-----------|------------|
| **UI Framework** | Jetpack Compose + TV Material 3 |
| **Видео плеер** | IjkPlayer + VLC (libvlc) |
| **Веб сервер** | NanoHTTPD (порт 8080) |
| **WebSocket** | Встроенный в NanoHTTPD |
| **QR коды** | ZXing |
| **Голос** | Vosk AI |
| **Картинки** | Coil |
| **HTTP клиент** | OkHttp |

### 📡 API Веб-пульта

```
GET  /api/status              - Текущее состояние плеера
GET  /api/channels            - Список всех каналов
GET  /api/qrcode              - QR-код и локальный адрес
POST /api/channel/{id}        - Переключиться на канал
POST /api/command             - Отправить команду (play, pause, etc)
WS   /ws/remote               - WebSocket для real-time обновлений
```

**Пример команды:**
```json
POST /api/command
{"command": "switchChannel", "value": 0}
```

---

## 🚀 Сборка проекта

### Требования
- **Android Studio** Hedgehog (2023.1.1) или новее
- **JDK** 17+
- **Android SDK** API 21+
- **Gradle** 8.0+

### Шаги

1. **Склонируйте репозиторий**
   ```bash
   git clone https://github.com/dreamcatchered/dreamTV.git
   cd dreamTV
   ```

2. **Откройте в Android Studio**
   - File → Open → выберите папку проекта
   - Дождитесь синхронизации Gradle

3. **Соберите APK**
   ```bash
   # Debug версия
   ./gradlew assembleDebug
   
   # Release версия (нужно настроить signing)
   ./gradlew assembleRelease
   ```

4. **Установите на устройство**
   ```bash
   # Через ADB
   adb install app/build/outputs/apk/debug/app-debug.apk
   
   # Или через Android Studio: Run → Run 'app'
   ```

### 📦 Генерация подписанного APK

Для публикации создайте `keystore.properties` в корне проекта:

```properties
storePassword=ваш_пароль
keyPassword=ваш_пароль_ключа
keyAlias=ваш_алиас
storeFile=путь/к/keystore.jks
```

Затем добавьте в `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        // загрузка из keystore.properties
    }
}
```

---

## ⚙️ Конфигурация

### Где указывать ссылки на M3U8?

Ссылки на плейлисты указываются **в интерфейсе приложения**:

1. Откройте приложение на TV Box
2. Перейдите на вкладку **⚙️ Настройки**
3. В поле **"Ссылка на плейлист"** вставьте URL вашего M3U8
4. Нажмите **"Загрузить каналы"**

**Формат M3U8 плейлиста:**
```m3u
#EXTM3U
#EXTINF:-1 tvg-logo="https://example.com/logo1.png",Channel 1
http://example.com/stream1.m3u8
#EXTINF:-1 tvg-logo="https://example.com/logo2.png",Channel 2
http://example.com/stream2.m3u8
```

### Настройка каналов по умолчанию

Приложение не содержит предустановленных каналов — вы загружаете свои плейлисты. Это даёт полную свободу выбора контента!

---

## 📋 Требования к устройству

| Параметр | Значение |
|----------|----------|
| **ОС** | Android 5.0+ (API 21+) |
| **Устройства** | TV Box, Android TV, Google TV |
| **Поддержка пульта** | ✅ Полная |
| **Leanback Launcher** | ✅ Оптимизировано |
| **Интернет** | Требуется для загрузки плейлистов |

### Рекомендуемые устройства
- 📺 **Xiaomi Mi Box**
- 📺 **NVIDIA Shield TV**
- 📺 **Chromecast with Google TV**
- 📺 **Amazon Fire TV Stick** (с модификациями)
- 📺 Любые Android TV Box на AliExpress

---

## 🔐 Безопасность

- ✅ **Локальный веб сервер** — работает только в вашей сети Wi-Fi
- ✅ **Нет внешних подключений** — все данные остаются на устройстве
- ✅ **Открытый исходный код** — можно проверить код на безопасность
- ⚠️ **Плейлисты IPTV** — используйте только доверенные источники

> ⚠️ **Внимание:** Приложение не предоставляет контент и не несёт ответственности за используемые плейлисты.

---

## 🛠️ Для разработчиков

### Вклад в проект

1. Fork репозиторий
2. Создайте ветку (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
4. Запушьте (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

### Структура кода

- **MainActivity.kt** — основная логика UI, навигация, обработка команд пульта
- **WebRemoteService.kt** — HTTP сервер, WebSocket, API для веб-пульта
- **ui/theme/** — тема Material 3 (цвета, шрифты, стили)
- **BootReceiver.kt** — автозапуск при загрузке устройства

### Полезные команды

```bash
# Запустить линтер
./gradlew lint

# Запустить тесты
./gradlew test

# Проверить зависимости
./gradlew app:dependencies

# Очистить сборку
./gradlew clean
```

---

## 🤝 Поддержка

### Вопросы и проблемы

- 📧 **GitHub Issues** — для багов и предложений
- 💬 **Discussions** — для вопросов и обсуждений

### Частые вопросы

**Q: Каналы не загружаются?**  
A: Проверьте ссылку на плейлист в формате M3U8. Убедитесь, что URL доступен из сети TV Box.

**Q: Веб-пульт не подключается?**  
A: Убедитесь, что оба устройства в одной Wi-Fi сети. Проверьте IP адрес в настройках приложения.

**Q: Можно ли добавить свои каналы?**  
A: Да! Создайте M3U плейлист со своими каналами и загрузите его в приложении.

**Q: Работает ли без интернета?**  
A: Да, если плейлист загружен локально. Веб-пульт работает в локальной сети без интернета.

---

## 📄 Лицензия

Этот проект распространяется без лицензии (All Rights Reserved).  
Для коммерческого использования свяжитесь с автором.

---

## 👨‍💻 Автор

**dreamcatchered**  
[GitHub Profile](https://github.com/dreamcatchered)

---

## 🙏 Благодарности

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [IjkPlayer](https://github.com/bilibili/ijkplayer)
- [VLC](https://www.videolan.org/)
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)
- [ZXing](https://github.com/zxing/zxing)
- [Vosk](https://github.com/alphacep/vosk-android)

---

**⭐ Поставьте звезду, если проект вам понравился!**

**Версия**: 1.1  
**Последнее обновление**: 26 февраля 2026  
**Сделано с ❤️ для Android TV Box**
