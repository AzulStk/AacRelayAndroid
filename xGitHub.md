# AAC Relay — istruzioni GitHub e installazione

Repository: https://github.com/AzulStk/AacRelayAndroid

L'APK viene compilato da GitHub Actions (`.github/workflows/build.yml`): in locale non serve
Android Studio né l'Android SDK.

---

## 1. Configurare i secret di firma (una volta sola)

Senza secret la build funziona, ma l'APK è firmato con una chiave di debug diversa a ogni
build: Android rifiuta l'aggiornamento e bisogna disinstallare l'app prima di reinstallarla.
Con i secret la firma è sempre la stessa e gli aggiornamenti si installano sopra.

1. Aprire https://github.com/AzulStk/AacRelayAndroid/settings/secrets/actions
2. **New repository secret**, quattro volte:

   | Nome | Valore |
   |---|---|
   | `AAC_KEYSTORE_BASE64` | tutto il contenuto di `keystore/aacrelay.p12.base64.txt` |
   | `AAC_KEYSTORE_PASSWORD` | la password scritta in `keystore/SEGRETI-GITHUB.txt` |
   | `AAC_KEY_ALIAS` | `aacrelay` |
   | `AAC_KEY_PASSWORD` | la stessa password di `AAC_KEYSTORE_PASSWORD` |

La cartella `keystore/` è esclusa da git (`.gitignore`) e resta solo in locale.
**Conservare `keystore/aacrelay.p12`**: se va persa, la prossima versione andrà installata
dopo aver disinstallato quella vecchia.

---

## 2. Compilare

| Azione | Risultato |
|---|---|
| `git push` su `main` | build automatica, APK fra gli **artifact** del run |
| `git tag v1.0.0 && git push --tags` | build + **Release** con `AacRelay.apk` allegato |
| Actions → *Build APK* → **Run workflow** | build manuale, APK fra gli artifact |

Per ogni nuova versione da distribuire usare un tag nuovo (`v1.0.1`, `v1.1.0`, …).
Il numero di versione interno (`versionCode`) cresce da solo a ogni run.

### Dove scaricare l'APK

- **Artifact**: https://github.com/AzulStk/AacRelayAndroid/actions → aprire il run →
  sezione *Artifacts* → `AacRelay-apk` (è uno zip che contiene `AacRelay.apk`; serve essere
  loggati su GitHub).
- **Release** (consigliato per il telefono): https://github.com/AzulStk/AacRelayAndroid/releases
  → `AacRelay.apk`, scaricabile direttamente dal browser del telefono.

---

## 3. Installare sul telefono

1. Scaricare `AacRelay.apk` dal telefono (Release) e aprirlo.
2. Consentire **"Installa app sconosciute"** al browser o al file manager quando richiesto.
3. Al primo avvio:
   - incollare l'indirizzo completo della pagina AAC, codice compreso:
     `http://192.168.28.10:8765/?t=…`
   - **consentire le notifiche**;
   - **consentire l'esclusione dal risparmio energetico** (indispensabile per il beep in standby).
4. Su Xiaomi, Huawei e alcuni Samsung: nelle impostazioni dell'app consentire l'avvio /
   l'attività in background e togliere l'app dalle "app in sospensione".

Se la versione installata era firmata con la chiave di debug (build fatta prima dei secret),
per installare quella firmata va prima **disinstallata**.

---

## 4. Uso

- L'app mostra la pagina di AAC a schermo intero.
- Una notifica fissa indica lo stato: *Nessun passaggio in attesa*, *In attesa: UNO → DUE*,
  *AAC non raggiungibile*, *Codice errato*.
- Quando compare un passaggio nuovo: **doppio beep + vibrazione**, una volta per passaggio.
  Il beep usa il volume della **sveglia**: suona anche con la suoneria in silenzioso.
- **Indietro** manda l'app in secondo piano senza fermarla.
- **Ferma** (nella notifica) spegne la sorveglianza; si riattiva riaprendo l'app.
- Per cambiare indirizzo: quando la pagina non è raggiungibile compare *Cambia indirizzo*;
  in alternativa cancellare i dati dell'app.

La sorveglianza tiene sveglia CPU e Wi-Fi: consuma un po' di batteria finché è attiva.
