# Domi TV: dein eigener Startbildschirm für den Fire TV Stick

Das ist die Oberfläche aus Version 2, mit Startanimation und „Weiter ansehen“ als echte Fire-TV-App.
Getestet ist die Oberfläche im Browser. Auf dem Stick selbst ist sie noch nicht getestet.

Du brauchst: deinen PC, den Fire TV Stick im selben WLAN und ca. 30–45 Minuten beim ersten Mal.

---

## 1. APK bauen (am PC)

1. **Android Studio** installieren (kostenlos): https://developer.android.com/studio
2. Android Studio öffnen → **Open** → den Ordner `DomiTV` auswählen.
3. Warten, bis unten „Gradle sync finished“ steht. Beim ersten Mal lädt er einiges herunter (5–15 Min.).
4. Oben im Menü: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Fertig. Die Datei liegt hier:
   `DomiTV/app/build/outputs/apk/debug/app-debug.apk`

> Alternative: Den Ordner in Claude Code öffnen und sagen: „Bau mir die Debug-APK“.

---

## 2. Fire TV vorbereiten

1. **Entwickleroptionen freischalten:**
   Einstellungen → Mein Fire TV → **Info** → 7× auf den **Gerätenamen** (Fire TV Stick) klicken,
   bis „Du bist jetzt Entwickler“ erscheint.
2. Einstellungen → Mein Fire TV → **Entwickleroptionen** → **ADB-Debugging: AN**.
3. **IP-Adresse** notieren: Einstellungen → Mein Fire TV → Info → **Netzwerk** (z. B. `192.168.1.42`).

---

## 3. App installieren (am PC)

1. **ADB** herunterladen („SDK Platform-Tools für Windows“):
   https://developer.android.com/tools/releases/platform-tools
   → ZIP entpacken, z. B. nach `C:\platform-tools`.
2. Die `app-debug.apk` in denselben Ordner kopieren.
3. Im Ordner `C:\platform-tools` oben in die Adresszeile `cmd` tippen und Enter drücken.
4. Verbinden (deine IP einsetzen):
   ```
   adb connect 192.168.1.42
   ```
   Auf dem Fernseher erscheint eine Frage → **„Von diesem Computer immer zulassen“** → OK.
   Dann den Befehl noch einmal ausführen.
5. Installieren:
   ```
   adb install -r app-debug.apk
   ```
   Es sollte `Success` erscheinen. „Domi TV“ ist jetzt in der App-Liste.

---

## 4. Home-Taste und Einschalten auf Domi TV umlenken

Das macht ein kleiner Hintergrund-Dienst in der App: Sobald die Amazon-Startseite erscheint
(Home-Taste, Einschalten, Neustart), öffnet er sofort Domi TV. Er liest keine Inhalte vom Bildschirm.

```
adb shell settings put secure enabled_accessibility_services at.domi.tvlauncher/at.domi.tvlauncher.HomeWatcherService
adb shell settings put secure accessibility_enabled 1
```

**Testen:** Home-Taste auf der Fernbedienung drücken → die Amazon-Startseite blitzt kurz auf,
dann kommt Domi TV mit der Startanimation.

Updates und Ruhezustand des Fire TV bleiben dabei ganz normal erhalten.

---

## 5. Bedienung

| Taste | Was passiert |
|---|---|
| ◀ ▶ | App wechseln (der Hintergrund wechselt mit) |
| OK | App öffnen |
| ▼ | „Weiter ansehen“ einblenden |
| ▲ | Zurück, bzw. ganz oben: Fire-TV-Startseite und Einstellungen |
| ☰ (Menü) | Fire-TV-Einstellungen |

Über **Haus-Symbol oben rechts** kommst du jederzeit zur normalen Amazon-Startseite.
Von dort wieder zurück: Domi TV in der App-Liste öffnen oder eine App starten und danach Home drücken.

---

## 6. Wenn etwas nicht klappt

**Home-Taste öffnet Domi TV nicht:**
Am PC eingeben und dann am Fernseher Home drücken:
```
adb logcat -s DomiTV
```
Schick mir die Zeilen, die mit `Fenster:` beginnen. Dann sehe ich, wie die Amazon-Startseite
auf deinem Stick intern heißt, und passe das an.

**Nach einem Fire-TV-Update oder Neustart funktioniert die Umleitung nicht mehr:**
Schritt 4 einfach noch einmal ausführen.

**Eine App-Kachel ist ausgegraut („nicht installiert“):**
Die App wurde unter einem anderen Namen gefunden. Am PC:
```
adb shell pm list packages
```
Den richtigen Paketnamen in `app/src/main/assets/config.js` bei `pakete` eintragen und neu bauen.

**Es ruckelt:**
Die App misst beim ersten Start die Leistung und schaltet bei Bedarf selbst in einen leichteren Modus.
Fest einstellen kannst du das in `config.js` mit `qualitaet: 'lite'`.

---

## 7. Anpassen

Alles Wichtige steht in **`app/src/main/assets/config.js`**:
- `apps`: Reihenfolge, Namen und Farben der Apps
- `intro`: Startanimation `'aufwachen'`, `'immer'` oder `'aus'`
- `weiterAnsehen`: Leer = die zuletzt geöffneten Apps. Oder eigene Serien mit Direktlink (Beispiel steht in der Datei).

Nach jeder Änderung: neu bauen (Schritt 1.4) und neu installieren (Schritt 3.5).

> Hinweis zu „Weiter ansehen“: Netflix, Prime und Disney+ geben nicht preis, was du zuletzt geschaut hast.
> Deshalb zeigt die Reihe die zuletzt geöffneten Apps oder Titel, die du selbst einträgst.

---

## 8. Alles rückgängig machen

```
adb shell settings put secure enabled_accessibility_services ""
adb uninstall at.domi.tvlauncher
```
Danach ist der Fire TV wieder genau wie vorher.

---

## Tipp für den Samsung

Schalte den Fernseher aus, **während der Fire TV läuft**. Dann startet er beim nächsten Mal direkt
auf diesem HDMI-Eingang, ohne den Umweg über ServusTV.
