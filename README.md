# Gradus

**A plain weather forecast for Android: now, the week ahead, and hour by hour.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0), with no
account or key.

## Screens

- **Now**: temperature and how it feels, the sky, wind, humidity and pressure,
  sunrise and sunset, and the seven days ahead with their lows and highs.
- **Hourly**: the next 48 hours under a heading per day: temperature, wind,
  humidity and the chance of rain or snow.
- **Settings**: wind in m/s or km/h, its direction as an arrow or letters,
  pressure in mmHg or hPa, theme, colour, language, and the update check at launch.

The forecast is for where the phone is or for a city found by name; the cities
are kept in a list, picked from the place name in the top bar. Pull down to
refresh. The last forecast is kept on the phone, so the app opens on it at once,
and a new one is fetched when it is more than 15 minutes old.

## Building

The numbered scripts in the project root drive the build; `AGENTS.md` describes
how they fit together.
