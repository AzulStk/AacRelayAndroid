# AAC Relay (Android)

App minimale che fa due cose sole:

1. mostra a schermo intero la pagina tablet di AzulAgentConsole (`http://IP:8765/?t=TOKEN`);
2. suona un doppio beep (e vibra) quando AAC ha un passaggio in attesa di conferma,
   **anche con il telefono in standby**.

## Come funziona

- La pagina è quella servita da AAC, invariata, dentro una WebView.
- Un servizio in primo piano interroga `/relay/status` ogni 3 secondi. Quando compare un
  `ticket` nuovo con `isOpen: true` suona: una volta per passaggio, non a ogni interrogazione.
- Per non addormentarsi in standby tiene un wake lock parziale e un Wi-Fi lock, e al primo
  avvio chiede l'esenzione dal risparmio energetico (va concessa). Costa un po' di batteria:
  il pulsante **Ferma** nella notifica spegne tutto.
- Il beep usa il volume **sveglia** (`STREAM_ALARM`): suona anche con la suoneria in silenzioso.
- L'indirizzo con il token si inserisce al primo avvio e resta sul telefono, mai nel sorgente.
  Per cambiarlo: compare "Cambia indirizzo" quando la pagina non è raggiungibile, oppure
  si cancellano i dati dell'app.

## Build

GitHub Actions (`.github/workflows/build.yml`) compila a ogni push su `main`
(APK fra gli artifact del run) e, sui tag `v*`, allega `AacRelay.apk` alla Release.

Secret da configurare in *Settings → Secrets and variables → Actions*:

| Secret | Contenuto |
|---|---|
| `AAC_KEYSTORE_BASE64` | keystore PKCS12 in base64 |
| `AAC_KEYSTORE_PASSWORD` | password del keystore |
| `AAC_KEY_ALIAS` | alias della chiave |
| `AAC_KEY_PASSWORD` | password della chiave |

Senza secret l'APK viene firmato con una chiave di debug diversa a ogni build: Android allora
rifiuta l'aggiornamento e bisogna disinstallare prima. Con il keystore fisso gli aggiornamenti
si installano sopra.

Rilascio: `git tag v1.0.0 && git push --tags`.

## Installazione

Scaricare `AacRelay.apk` dalla Release sul telefono, aprirlo, consentire "installa app
sconosciute" al browser/file manager. Al primo avvio: incollare l'indirizzo, consentire le
notifiche e l'esenzione batteria.

Su alcuni telefoni (Xiaomi, Huawei, Samsung con ottimizzazioni aggressive) serve anche
consentire l'avvio in background / togliere l'app dalle "app in sospensione".
