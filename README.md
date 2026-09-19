# Liquid Weather (琉璃天气)

**English** · [简体中文](README.zh-CN.md)

An Android weather app built around a **Liquid Glass** design language — frosted glass cards that refract the animated sky behind them, with a home screen widget, four weather data sources and four UI languages.

<p align="left">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="UI" src="https://img.shields.io/badge/UI-XML%20Views%20%2B%20Material%203-757575">
  <img alt="Glass" src="https://img.shields.io/badge/Glass-Liquid--Glass--Android-4A9EE8">
</p>

## Screenshots

| Day | Night | Widget |
| :---: | :---: | :---: |
| _(add a screenshot)_ | _(add a screenshot)_ | _(add a screenshot)_ |

## Features

**Weather**
- Current conditions: temperature, feels-like, condition, today's high/low
- Hourly forecast (48 h, with precipitation probability)
- Daily forecast (up to 15 days, with gradient temperature range bars)
- Details grid: humidity, wind, pressure, cloud cover, UV index, comfort, precipitation, sunrise/sunset
- Air quality card (AQI + PM2.5 / PM10 / O₃ / NO₂ / SO₂ / CO)
- Weather alerts, tinted by severity level (tap to expand the full text)
- Offline cache — the last successful data is shown when the network is unavailable

**Design**
- Liquid glass cards that refract the sky behind them, with dispersion (chromatic edge) and sensor-driven highlights
- Weather- and time-aware animated sky: drifting cloud layers, twinkling stars at night, rain streaks, a breathing sun glow
- iOS-style press feedback on the floating controls — scale-up with a light highlight and a springy release
- iOS-inspired vector icon set (SF Symbols-like filled shapes), no bitmap assets
- Centred, iOS-like layout for the current conditions block

**Home screen widget**
- iOS-style layout: city + location arrow, large temperature, condition, high/low, and a 6-column hourly strip
- Gradient background that changes with the weather (clear / night / cloudy / rain / snow / fog)
- Sunrise and sunset slots replace the hour label with the exact time and a dedicated icon
- Auto-sizing text so it renders correctly on any launcher grid (verified on ColorOS 4×6)
- Refreshes every 30 minutes via WorkManager, plus on widget placement and whenever the app updates its data

**Cities & settings**
- GPS location via `LocationManager` (no Google Play Services dependency) plus a built-in database of 190+ Chinese cities
- Multiple saved cities with quick switching; long-press a chip to delete
- Long-press the location button to add/switch to your current location instantly
- Switchable weather source, API keys, temperature units (°C/°F) and language

**Localisation**
- Simplified Chinese (default), Traditional Chinese, English, Japanese — the whole UI, weather descriptions, wind directions, AQI levels, error messages and time formats
- The weather API is queried in the selected language, so descriptions match the interface
- Respects the system per-app language setting on Android 13+

## Weather data sources

| Source | Coverage | Free tier | Notes |
| --- | --- | --- | --- |
| **Caiyun Weather** (default) | China & Asia-Pacific | Limited daily calls | Single combined `weather.json` request per refresh; includes AQI and alerts |
| **QWeather** | China | 1000 calls/day | Current + 7-day + 24-hour + alerts; new console accounts need a dedicated API Host |
| **AccuWeather** | Global | 50 calls/day | Current + 12-hour + 5-day; the location key is resolved once per city and cached |
| **OpenWeather** | Global | 1000 calls/day | Current + 5-day/3-hour; daily values are aggregated from the 3-hour steps |

All keys are stored locally in `SharedPreferences` and are only sent to the corresponding provider.

## Getting started

### Requirements
- Android Studio (JDK 17 bundled)
- Android SDK 36, `minSdk` 26 (Android 8.0)
- A device or emulator running Android 8.0+

### Build
```bash
git clone https://github.com/Lyinx01/LiquidWeather.git
cd LiquidWeather
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```
Or open the project in Android Studio and press **Run**. The first sync downloads Gradle 8.11.1 and all dependencies, including the glass library from JitPack.

### Configure an API key
The app needs a key from at least one provider. Open **Settings → Weather source**, pick a provider, paste its key and save.

- **Caiyun** — free token at [dashboard.caiyunapp.com](https://dashboard.caiyunapp.com/)
- **QWeather** — free key at [console.qweather.com](https://console.qweather.com/). Accounts registered after 2024 must also paste their dedicated **API Host** (Console → Settings → API Host), e.g. `xxxxxxxx.re.qweatherapi.com`.
- **AccuWeather** — free key at [developer.accuweather.com](https://developer.accuweather.com/)
- **OpenWeather** — free key at [home.openweathermap.org](https://home.openweathermap.org/api_keys)

## Tech stack

Kotlin · XML Views (the glass library ships View components, not Compose) · Material 3 · ViewBinding ·
ViewModel + LiveData · Coroutines · Retrofit + Gson · WorkManager · JitPack

## Architecture

The layering follows [breezy-weather](https://github.com/breezy-weather/breezy-weather): data sources → domain models → card-based UI.

```
app/src/main/java/com/liuli/weather/
├── App.kt                        # Application: initialises AppCtx
├── MainActivity.kt               # Home: glass cards, location flow, pull-to-refresh
├── SettingsActivity.kt           # Source / key / units / language settings
├── data/
│   ├── remote/                   # Retrofit APIs + Gson DTOs for the four providers
│   ├── source/                   # WeatherSource abstraction + one implementation per provider
│   ├── repository/               # WeatherRepository: source dispatch + per-source cache
│   ├── location/                 # LocationManager wrapper
│   ├── city/                     # Built-in city database (assets/cities.json)
│   ├── model/                    # Domain models (Weather, CurrentWeather, …)
│   └── prefs/                    # SettingsStore: keys, cities, units, language
├── ui/
│   ├── main/                     # ViewModel, UI state, hourly/daily/detail/alert adapters
│   ├── city/                     # City picker bottom sheet
│   └── common/                   # GlassPressEffect: iOS-style press animation
├── widget/                       # Home screen widget + WorkManager refresh worker
└── util/                         # Weather code mapping, time utils, unit conversion, API language
```

## How the liquid glass is wired

The glass library captures the view behind a `LiquidGlassView` and refracts it. Three rules shaped this project:

1. **The backdrop must contain detail.** Plain colours and smooth gradients show no refraction, so the app draws a layered sky — gradient base, soft radial clouds, stars, sun/moon glow — as the capture source.
2. **Sampling is split by role.** Floating controls sample the whole root layout (so they refract the content scrolling underneath, like an iOS navigation bar) and only during scrolling or while pressed; content cards sample the static sky layer and refresh on a low-frequency timer. This keeps scrolling smooth while the cards still track the drifting clouds.
3. **Press feedback uses `glassTint`.** A colour tint would interfere with the refracted image, so the press highlight is applied as an ARGB tint that fades in while the view scales up.

## Localisation

| Language | Resources |
| --- | --- |
| Simplified Chinese (default) | `values/strings.xml` |
| English | `values-en/strings.xml` |
| Traditional Chinese | `values-zh-rTW/strings.xml` |
| Japanese | `values-ja/strings.xml` |

`ApiLang` maps the app language to each provider's `lang` parameter, so weather descriptions come back in the same language as the interface. Adding a language means adding one `values-<tag>/strings.xml` plus an entry in `res/xml/locales_config.xml`.

## Notes

- Wind speed is shown in km/h and pressure in hPa (converted from the raw units where needed).
- Only the key of the currently selected source is required.
- Built for learning and personal use. Weather data belongs to the respective providers; the glass effect belongs to the [Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) author.
