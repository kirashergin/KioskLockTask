# KioskLockTask

Android-приложение для режима киоска (Lock Task Mode) с использованием Device Owner API. Полностью блокирует устройство в одном приложении — без навигационных кнопок, без статус-бара, без возможности выйти.

## Возможности

- **Lock Task Mode** — устройство заблокировано в одном приложении, выход невозможен без ADB
- **Device Owner** — максимальные привилегии через DevicePolicyManager
- **Скрытие системных баров** — навигационная панель и статус-бар полностью скрыты (immersive + старые SYSTEM_UI_FLAG + прозрачные бары)
- **Перехват аппаратных кнопок** — Back, Home, Recents, Menu, Search, Assist заблокированы
- **Автозапуск** — приложение стартует при загрузке устройства (BootReceiver)
- **Launcher по умолчанию** — кнопка Home всегда возвращает в киоск
- **Экран не гаснет** — FLAG_KEEP_SCREEN_ON предотвращает появление lock screen
- **Блокировка поворота** — `screenOrientation="locked"`

## Требования

- Android 6.0+ (API 23+), рекомендуется Android 10+
- Android Studio Giraffe или новее
- ADB (Android Debug Bridge)
- Устройство или эмулятор

## Сборка

### Через Android Studio

1. Откройте папку проекта в Android Studio
2. Дождитесь синхронизации Gradle
3. Build > Build Bundle(s) / APK(s) > Build APK(s)

### Через командную строку

```bash
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

## Установка и настройка Device Owner

### Шаг 1 — Подготовка устройства

Device Owner можно установить **только** при одном из условий:
- Устройство после factory reset (до завершения Setup Wizard)
- На устройстве нет аккаунтов Google (или используется флаг `--user 0`)

### Шаг 2 — Установка APK

```bash
adb install app-debug.apk
```

### Шаг 3 — Назначение Device Owner

```bash
adb shell dpm set-device-owner "com.example.kiosk/.KioskDeviceAdminReceiver"
```

Если появляется ошибка `Not allowed to set the device owner because there are already some accounts on the device`, попробуйте:

```bash
# Вариант 1 — указать пользователя явно
adb shell dpm set-device-owner --user 0 "com.example.kiosk/.KioskDeviceAdminReceiver"

# Вариант 2 — удалить аккаунты через настройки и повторить

# Вариант 3 — factory reset и установить до входа в аккаунт Google
```

### Шаг 4 — Запуск

```bash
adb shell am start -n com.example.kiosk/.MainActivity
```

Или просто перезагрузите устройство — приложение запустится автоматически.

### Проверка статуса

```bash
# Проверить что Device Owner установлен
adb shell dpm list-owners

# Проверить что Lock Task активен
adb shell dumpsys activity activities | grep mLockTaskModeState
```

## Как это работает

### Архитектура

```
KioskApp.onCreate()
    └── tryConfigureDeviceOwnerPolicies()
            ├── setLockTaskPackages()         — разрешает lock task для нашего пакета
            ├── setLockTaskFeatures(admin, 0) — отключает ВСЕ элементы UI в lock task
            ├── setStatusBarDisabled(true)    — блокирует шторку уведомлений
            ├── setKeyguardDisabled(true)     — отключает экран блокировки
            └── addPersistentPreferredActivity() — делает себя HOME/Launcher

MainActivity.onCreate()
    ├── startLockTask()                       — входит в Lock Task Mode
    ├── enterImmersive()                      — скрывает системные бары
    ├── dispatchKeyEvent()                    — перехватывает аппаратные кнопки
    └── setOnSystemUiVisibilityChangeListener — мгновенно прячет бары если появились

BootReceiver
    └── запускает MainActivity при загрузке устройства
```

### Многослойная защита UI

| Слой | Механизм | Что делает |
|------|----------|------------|
| 1 | `setLockTaskFeatures(admin, 0)` | Системный уровень — убирает все элементы UI в lock task |
| 2 | `WindowInsetsControllerCompat.hide()` | Новый API — скрывает system bars |
| 3 | `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` | Старый API — авто-скрытие при появлении баров |
| 4 | `setOnSystemUiVisibilityChangeListener` | Ловит появление баров и прячет через 50мс |
| 5 | `onWindowFocusChanged` + `onResume` | Повторное скрытие при любой смене фокуса |
| 6 | Прозрачные system bars | Даже если бар мелькнёт — кнопки невидимы |

### Блокировка кнопок

| Кнопка | Защита |
|--------|--------|
| Back | `onBackPressed()` + `dispatchKeyEvent(KEYCODE_BACK)` |
| Home | Lock Task Mode + `addPersistentPreferredActivity` |
| Recents | Lock Task Mode + `dispatchKeyEvent(KEYCODE_APP_SWITCH)` |
| Menu | `dispatchKeyEvent(KEYCODE_MENU)` |
| Search / Assist | `dispatchKeyEvent(KEYCODE_SEARCH, KEYCODE_ASSIST, KEYCODE_VOICE_ASSIST)` |
| Статус-бар | `setStatusBarDisabled(true)` |

## Структура проекта

```
app/src/main/
├── AndroidManifest.xml                          — компоненты, разрешения, lock task mode
├── java/com/example/kiosk/
│   ├── KioskApp.kt                              — настройка Device Owner политик
│   ├── MainActivity.kt                          — UI, immersive mode, блокировка кнопок
│   ├── KioskDeviceAdminReceiver.kt              — Device Admin receiver
│   └── BootReceiver.kt                          — автозапуск после перезагрузки
└── res/
    ├── values/themes.xml                        — тема с прозрачными барами
    └── xml/device_admin_receiver.xml            — политики Device Admin
```

## Разрешения

| Разрешение | Зачем |
|------------|-------|
| `RECEIVE_BOOT_COMPLETED` | Автозапуск приложения после перезагрузки |
| `BIND_DEVICE_ADMIN` | Device Admin receiver (системное) |

Все остальные возможности (lock task, отключение статус-бара, keyguard) доступны через **Device Owner API** без дополнительных разрешений в манифесте.

## Выход из режима киоска

Выйти из киоска можно **только через ADB**:

```bash
# Способ 1 — снять Device Owner (приложение потеряет все привилегии)
adb shell dpm remove-active-admin "com.example.kiosk/.KioskDeviceAdminReceiver"

# Способ 2 — остановить приложение принудительно
adb shell am force-stop com.example.kiosk

# Способ 3 — удалить приложение
adb uninstall com.example.kiosk
```

## Опциональные усиления

В `KioskApp.kt` можно добавить дополнительные user restrictions:

```kotlin
// Запрет Safe Mode
dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)

// Запрет Factory Reset
dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)

// Запрет установки/удаления приложений
dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)

// Запрет USB (флешки, MTP)
dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)

// Запрет ADB (ОСТОРОЖНО — потеряете доступ к устройству!)
dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)

// Запрет удаления приложения
dpm.setUninstallBlocked(admin, packageName, true)

// Запрет скриншотов
dpm.setScreenCaptureDisabled(admin, true)
```

## Лицензия

MIT
