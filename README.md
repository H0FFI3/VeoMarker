# Veo Marker – Einrichtung

Nicht in diesem Container gebaut oder getestet (kein Android SDK). Beim ersten Build können kleine Anpassungen nötig sein.

## 1. Projekt anlegen
Android Studio → New Project → **Empty Activity** (Jetpack Compose).
Package name: `de.example.veomarker` (oder eigener; dann `package`-Zeilen in den 3 Kotlin-Dateien anpassen).
Min SDK: 26.

## 2. Dateien kopieren
`Data.kt`, `MainViewModel.kt`, `MainActivity.kt` in den Package-Ordner kopieren. Die generierte `MainActivity.kt` ersetzen.
Eventuell erzeugte Theme-Dateien (`ui/theme`) können bleiben oder gelöscht werden, sie werden nicht benutzt.

## 3. Room + KSP hinzufügen
Root `build.gradle.kts` → bei `plugins { ... }` ergänzen (KSP-Version muss zur Kotlin-Version passen, siehe github.com/google/ksp/releases):
```kotlin
id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
```
`app/build.gradle.kts` → in `plugins { ... }`:
```kotlin
id("com.google.devtools.ksp")
```
und in `dependencies { ... }`:
```kotlin
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
```

## 4. Manifest
In `AndroidManifest.xml` direkt unter `<manifest ...>` (vor `<application>`) einfügen, damit die Spracherkennung auf Android 11+ gefunden wird:
```xml
<queries>
    <intent>
        <action android:name="android.speech.action.RECOGNIZE_SPEECH" />
    </intent>
</queries>
```

## Bedienung
1. Spiel mit Titel anlegen, öffnen.
2. Beim Anpfiff **Anpfiff – Zeit starten**.
3. **Marke setzen + sprechen**: Zeitstempel wird sofort gespeichert, danach Tag einsprechen.
4. Offset eintragen: Im Veo-Video nachsehen, bei welcher Zeit der Anpfiff ist (z. B. 12:34) und bei „Anpfiff im Video bei“ eintragen. Alle Marken zeigen dann die Videozeit.
