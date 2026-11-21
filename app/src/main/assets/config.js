// Domi TV - Einstellungen
// Diese Datei kannst du anpassen. Danach die APK neu bauen und installieren.

window.CONFIG = {
  // Dein Name in der Begrüßung
  name: 'Domi',

  // Startanimation: wann sie läuft.
  //  'immer'     = bei jedem Zurückkommen zum Home-Screen
  //  'aufwachen' = beim Einschalten und nach mehr als 2 Minuten weg
  //  'aus'       = nie
  intro: 'aufwachen',

  // Grafik-Qualität: 'auto' nimmt auf schwachen Geräten (Fire TV Stick mit 1 GB)
  // automatisch die flüssige Variante. Mit 'hoch' oder 'lite' legst du es fest.
  qualitaet: 'auto',

  // Deine Apps in dieser Reihenfolge.
  // pakete = mögliche Paketnamen, namen = App-Namen, wie sie am Fire TV heißen.
  // Die App wird zuerst über den Paketnamen gesucht, sonst über den Namen.
  apps: [
    { id: 'netflix', name: 'Netflix', art: 'Serien & Filme', glow: '#e01b2a', t1: '#8a111c', t2: '#22040a',
      pakete: ['com.netflix.ninja'], namen: ['netflix'] },
    { id: 'youtube', name: 'YouTube', art: 'Videos & Creator', glow: '#c4c4d2', t1: '#55555e', t2: '#17171b',
      pakete: ['com.amazon.firetv.youtube', 'com.google.android.youtube.tv'], namen: ['youtube'] },
    { id: 'prime', name: 'Prime Video', art: 'Filme & Serien', glow: '#1aa6dc', t1: '#0f6482', t2: '#051c25',
      pakete: ['com.amazon.firebat', 'com.amazon.avod', 'com.amazon.amazonvideo.livingroom'], namen: ['prime video', 'amazon prime video'] },
    { id: 'disney', name: 'Disney+', art: 'Familie & Blockbuster', glow: '#5a7aff', t1: '#2640a8', t2: '#0a1033',
      pakete: ['com.disney.disneyplus'], namen: ['disney+', 'disney plus'] },
    { id: 'ytmusic', name: 'YT Music', art: 'Musik & Playlists', glow: '#ff3a78', t1: '#8a1a48', t2: '#22051a',
      pakete: ['com.amazon.firetv.youtube.music', 'com.google.android.youtube.tvmusic'], namen: ['youtube music', 'yt music'] }
  ],

  // "Weiter ansehen" (Pfeil nach unten).
  // Leer lassen = es erscheinen automatisch die Apps, die du zuletzt geöffnet hast.
  // Eigene Titel mit Direktlink, Beispiel:
  //   { titel: 'Stranger Things', info: 'Staffel 4', app: 'netflix', link: 'https://www.netflix.com/title/80057281' }
  // Bei Netflix: Titel auf netflix.com öffnen, die Zahl aus der Adresse kopieren.
  weiterAnsehen: []
};
