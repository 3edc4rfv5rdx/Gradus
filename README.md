# Gradus

**A plain, easy-to-read weather forecast for Android: now, the week ahead, and hour by hour.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/Gradus)](https://github.com/3edc4rfv5rdx/Gradus/releases/latest)

Gradus shows the weather where you are or in any city you add, in large type and
bright pictures. It needs no account and no key, and it shows no ads. The forecast
comes from [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).

## Features

- **Now**:
  - temperature, how it feels, humidity, and the sky in a picture and in words;
  - wind and pressure beside an arc that shows today's sunrise, sunset and day
    length, with the sun where it stands now;
  - the moon, worked out on the phone: a ring on the same arc while it is up, and
    its phase, moonrise and moonset under the arc (can be turned off in Settings);
  - the seven days ahead: low and high on one common scale (blue below zero, red
    above), average humidity, and rain or snow in mm when there is any.
- **Hourly**: the next 48 hours, under a heading for each day with its sunrise,
  sunset, day length and UV index. Each hour shows the sky, the temperature, the
  wind, the humidity, and rain or snow in mm.
- **Pictures that read at a glance**: light, moderate and heavy rain or snow are
  drawn as one, two or three drops or flakes.
- **Places**: tap the place name in the top bar. Pick *My location*, or find a city
  by name and add it to the list. The list and the choice are kept.
- **Refresh**: pull down to refresh. The last forecast is kept on the phone, so the
  app opens on it at once, even with no network. A new one is fetched when the app
  comes to the front and the forecast on screen is more than 15 minutes old. The
  time of the last update is in the top bar.
- **Settings**:
  - temperature in °C or °F;
  - wind speed in m/s or km/h, and wind direction as compass letters or an arrow;
  - pressure in mmHg (at the place's own height, as local forecasts give it) or
    hPa (reduced to sea level);
  - light/dark theme, accent colour, language (English, Русский, Українська), and
    the update check at launch.

## Install

Download `gradus-<version>-universal.apk` from the
[latest release](https://github.com/3edc4rfv5rdx/Gradus/releases/latest).

The forecast for *My location* needs access to location; approximate is enough.
Without it, add a city by name instead.

## Privacy

The app goes online for three things only: the forecast and the city search, both
from Open-Meteo, and the check of its own GitHub release for a newer version, which
can be turned off. The name of the place you are in is looked up by Android's own
geocoder. Nothing else is sent anywhere, and the app's data is left out of cloud
backups and device transfers.

## Build

```bash
./00-MakeAll.sh     # signed release build, installed on the emulator and the phone
./05-Lint.sh        # Android Lint; app/lint.xml leaves out the shared modules
./06-Test.sh        # unit tests
```

Requires JDK 21 and the Android SDK (compileSdk 36). Release signing reads
`~/.my-safe/key.properties`. Without it the build still works, but the APK is unsigned.

The in-app updater and the About dialog are compiled from
`../shared/updater` and `../shared/about`. Clone the shared repository next to this one.

## License

[GNU General Public License v3.0](LICENSE). Weather data by
[Open-Meteo.com](https://open-meteo.com/), under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

## Note

This codebase was developed with the help of artificial intelligence tools.
