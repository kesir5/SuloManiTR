# Sülo ManiTR

Android-App (Kotlin + Jetpack Compose) zur Verwaltung eines Kontos in der Türkei:
**Devisen, Gold, Eurobonds der Republik Türkei und sonstige Vermögenswerte** – alle Werte in USD und EUR, Kurse live aus dem Internet.

## Funktionen

- **Sperre:** Start nur nach Fingerabdruck/Gesicht oder der Bildschirmsperre des Geräts (PIN, Muster, Passwort). Nach 30 s im Hintergrund sperrt die App erneut. Screenshots/App-Vorschau sind blockiert.
- **Übersicht:** Gesamtvermögen, Aufteilung, nächste Auszahlung, alle Positionen mit USD- und EUR-Wert (farbig nach Anlageart).
- **Devisen / Gold / Misc.:** Tabellen mit Hinzufügen, Ändern, Entfernen (mit „Rückgängig“).
- **Bonds:** Auszahlungsplan (nächste je Anleihe oder 12 Monate, sortierbar), Bestandskarten (sortierbar), ISIN antippen → aktueller Kurs, Rendite und Kurs-Chart (1M/3M/1J), Türkei-CDS 5J.
- **Speicherung:** nur lokal, verschlüsselt (AES-256-GCM, Schlüssel im Android Keystore). Verschlüsseltes Backup (Passwort, AES-256 + PBKDF2) exportieren/importieren über das Profilbild-Menü.

## Datenquellen (kostenlos, ohne API-Schlüssel)

| Daten | Quelle |
|---|---|
| Wechselkurse | Frankfurter-API (EZB-Referenzkurse) – `api.frankfurter.dev` |
| Gold (XAU/USD) | `api.gold-api.com` |
| Eurobond-Kurse + Kursverlauf | Börse Frankfurt, Tageskurse (`api.boerse-frankfurt.de`, inoffizielle Schnittstelle) |
| Türkei CDS 5J (USD) | Seitenabruf `de.investing.com` |

Die Schnittstellen von Börse Frankfurt und investing.com sind **nicht offiziell dokumentiert** und können sich ändern oder den Zugriff sperren. Dann zeigt die App die zuletzt gespeicherten Werte; für Anleihen kann zusätzlich ein **manueller Kurs** hinterlegt werden. Bitte die Nutzungsbedingungen der Anbieter beachten (privater Gebrauch).

Kurse sind Schlusskurse bzw. verzögert und dienen der Übersicht – keine Anlageberatung.

## Bauen

### Automatisch mit GitHub Actions

1. Ordner in ein GitHub-Repository pushen (Branch `main`).
2. Reiter **Actions → „APK bauen“**: läuft bei jedem Push (oder manuell über *Run workflow*).
3. Fertige APK: im Lauf unter **Artifacts → SuloManiTR-apk** (`SuloManiTR-debug.apk`, `SuloManiTR-release.apk`).
4. Optional: Tag `v1.0.0` pushen → die APKs hängen automatisch an einem GitHub-Release.

Die APKs sind ohne weiteres installierbar (Release wird ohne eigene Schlüssel mit dem Debug-Schlüssel signiert). Für eine **eigene Signatur** vier Repository-Secrets anlegen:
`KEYSTORE_BASE64` (`base64 -w0 meinkey.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Hinweis: APKs mit unterschiedlicher Signatur lassen sich nicht übereinander installieren – bei einem Wechsel zuerst deinstallieren (vorher Backup exportieren!).

### Lokal

```bash
./gradlew testDebugUnitTest assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
```
Voraussetzungen: JDK 17, Android SDK (Platform 35, Build-Tools 34) – oder einfach in Android Studio öffnen.

### Installieren

APK auf das Handy kopieren, antippen, „Aus unbekannten Quellen installieren“ erlauben. Mindestens **Android 11 (API 30)**; auf dem Gerät muss eine Bildschirmsperre oder ein Fingerabdruck eingerichtet sein.

## Projektaufbau

```
app/src/main/java/de/sulo/manitr/
  MainActivity.kt      Sperre (BiometricPrompt), Re-Lock, FLAG_SECURE
  AppViewModel.kt      Zustand, Hinzufügen/Entfernen/Rückgängig, Aktualisieren, Backup
  Models.kt Calc.kt    Datenmodell, Kupontermine, Rendite (YTM), Summen
  MarketData.kt        Live-Abruf der Kurse
  Vault.kt Json.kt     verschlüsselte lokale Speicherung
  BackupCrypto.kt      passwortbasiertes Backup
  ui/                  Compose-Oberfläche (Übersicht, Listen, Bonds, Dialoge)
app/src/test/          Unit-Tests (Kupontermine, Beträge, Rendite, Backup, Parser)
```

## Anleihen anpassen

Kupon, Zahlungsrhythmus und Fälligkeit der voreingestellten Eurobonds stammen aus der Beispieltabelle und sollten einmalig gegen die Anleihebedingungen geprüft werden: Anleihe in der Liste antippen → Stammdaten korrigieren. Über „Eurobond hinzufügen → Neue Anleihe“ lassen sich beliebige weitere ISINs ergänzen.
