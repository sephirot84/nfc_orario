# NFC Time Tracker

Terminale Android condiviso per registrare entrate e uscite tramite **UID NFC**, completamente offline. Kotlin, Jetpack Compose / Material 3, Room, MVVM, Coroutines e Flow. Android 10 (API 29) o successivo. Nessun account, server, pubblicità o permesso Internet.

## Download della versione 1.0.2 · Tema scuro

- [APK installabile di sviluppo](downloads/NFC-Time-Tracker-debug.apk)
- [Archivio dei sorgenti della versione attuale](downloads/NFC-Time-Tracker-source.zip)
- [Report XLSX di esempio](downloads/Esempio-report.xlsx)
- [Risultati delle verifiche](VERIFICA.md)
- [Checksum SHA-256 dei file di consegna](downloads/SHA256SUMS.txt)

Per scaricare l'APK dalla pagina del file su GitHub, usare **Download raw file**. Il nome visualizzato nell'elenco delle app Android e nella schermata principale è **NFC Time Tracker 1.0.2**. Il numero viene generato dal `versionName` della build. Lo ZIP contiene i sorgenti della stessa versione dell'APK; cache, SDK, chiavi di firma e configurazioni locali non sono inclusi.

## Tema scuro

La versione **1.0.2** usa un tema scuro fisso, anche se Android è impostato sul tema chiaro: sfondo blu notte, schede scure, testi chiari, ENTRATA verde acqua e USCITA azzurra. Dialoghi, menu, campi di inserimento e barre di sistema seguono la stessa impostazione. Le etichette testuali distinguono sempre le operazioni, oltre al colore.

Questa versione mantiene la diagnostica UID introdotta nella 1.0.1. Per aggiornare un'installazione esistente usare l'APK con la stessa firma, senza disinstallare l'app.

## Installazione e primo avvio

1. Compilare il progetto oppure installare `app/build/outputs/apk/debug/app-debug.apk`. Abilitare sul telefono l'installazione di app dalla sorgente usata per aprire il file. In alternativa: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Aprire l'app e premere l'icona amministratore in alto a destra. **Al primo avvio non esiste un PIN**: impostarlo in **Impostazioni** se desiderato.
3. In **Dipendenti**, aggiungere nome, cognome ed eventualmente matricola e minuti teorici giornalieri (480 = 8 ore).
4. Premere **Associa tag** e avvicinare il tag personale. Non viene scritto nulla sul tag. Un UID già associato a un altro dipendente non eliminato viene rifiutato.
5. Tornare al terminale con l'icona di uscita in alto a destra. Ogni scansione registra automaticamente ENTRATA / USCITA, separatamente per ciascun dipendente.

NFC deve essere disponibile e attivato. Il terminale deve essere visibile e il dispositivo sbloccato: non vengono effettuate timbrature in background. In amministrazione le letture non timbrano; servono solo all'associazione esplicitamente avviata. La conferma dura circa tre secondi e arriva **dopo il commit** del database. Il display può restare acceso. Una lettura di un altro dipendente può essere elaborata anche durante la conferma.

La build debug è installabile e firmata con la chiave di sviluppo generata localmente. Per distribuzione stabile usare **Build → Generate Signed Bundle / APK** in Android Studio, conservando in sicurezza la propria chiave di firma. Non cambiare firma per aggiornare un'installazione esistente.

### Verifica di letture attribuite al dipendente sbagliato

Dalla versione 1.0.1 la conferma mostra anche l'UID appena letto, inclusi gli avvisi di tag sconosciuto e antiduplicazione. Confrontarlo con l'UID in **Dipendenti**, insieme al nome visualizzato. Un UID sconosciuto non deve creare eventi né cambiare l'alternanza di alcun dipendente.

Per una prova con due persone e due tag distinti, partire da uno storico di prova vuoto: A → ENTRATA di A, B → ENTRATA di B, attendere l'intervallo antiduplicazione, A → USCITA di A, B → USCITA di B. Con uno storico esistente il primo verso dipende dall'ultima timbratura valida di ciascuna persona. Il test `NfcScanTest` ripete questa sequenza tramite il callback NFC dell'attività e verifica nomi, UID visualizzati ed eventi salvati, inserendo anche un tag sconosciuto. La lettura è simulata: l'hardware NFC resta da verificare sul dispositivo.

La versione 1.0.1 aggiunge questa visibilità e la verifica automatica; non modifica la regola di alternanza, già basata sull'ultima timbratura del dipendente identificato dal tag. Il problema segnalato non è stato riprodotto nella copia locale verificata.

## Funzioni e regole di calcolo

- Anagrafiche modificabili, disattivazione, eliminazione **logica**, sostituzione o rimozione tag. La rimozione dell'anagrafica non elimina lo storico; le statistiche e i report continuano a includerlo.
- Antiduplicazione per dipendente, inizialmente 10 secondi; configurabile tra 1 e 3600 secondi. Il timestamp viene persistito, quindi il blocco sopravvive al riavvio. Scansioni concorrenti sono serializzate in transazioni Room.
- Nessun reset a mezzanotte e **nessuna pausa pranzo automatica**. Il totale somma solo le coppie ENTRATA–USCITA valide e chiuse. I timestamp sono millisecondi UTC; durata reale anche nei cambi dell'ora legale.
- Turni notturni ripartiti per giorno secondo il fuso configurato (inizialmente Europe/Rome). Un turno aperto non aggiunge ore fino alla sua chiusura. I filtri conservano gli intervalli che iniziano prima del periodo e terminano al suo interno.
- Due entrate consecutive evidenziano la prima come incompleta; si abbina l'ultima entrata alla successiva uscita. Uscite senza entrata e coppie a timestamp uguali sono incomplete. Nessun orario viene inventato: correggere nello Storico.
- Storico filtrabile per dipendente, giorno, settimana, mese e intervallo personalizzato, con estremi inclusi. Aggiunte manuali, correzioni di orario e note, annullamento logico. Le correzioni mantengono l'origine originale NFC/MANUALE; vecchio e nuovo valore vengono conservati nel registro modifiche.
- Statistiche per dipendente: oggi, settimana e mese fino a oggi, totale storico, giorni lavorati e media del mese, saldo teorico del mese. Il teorico è facoltativo, **lunedì–venerdì dalla data di registrazione**, senza calendario festivi o ferie. Non modifica mai le ore effettive.
- Report XLSX con tre fogli: riepilogo giornaliero (coppie e quote dei turni notturni), dettaglio di ogni evento incluso lo stato annullato, riepilogo mensile. Durate Excel numeriche formattate `[h]:mm`, sommabili anche oltre 24 ore. CSV UTF-8 con BOM e separatore `;`; anagrafiche con associazioni esportabili separatamente. I filtri di dipendente e periodo si applicano ai report; l'export anagrafiche include tutti.
- Backup JSON locale versionato con checksum SHA-256, contenente anagrafiche, tag, eventi, registro e impostazioni, incluso l'hash del PIN. Ripristino con verifica e conferma esplicita, in una transazione atomica; viene ripristinato anche il PIN del backup. Il backup non è cifrato né autenticato: conservarlo in una posizione protetta. Limite 20 MB, 10.000 dipendenti e 100.000 eventi / modifiche per file; un superamento viene segnalato senza esportare un backup incompleto.

Il PIN è memorizzato con PBKDF2-HMAC-SHA256, salt casuale e 120.000 iterazioni; dopo cinque errori si attende 30 secondi. Non esiste recupero del PIN: conservarlo e provare periodicamente un backup su un dispositivo di riserva. L'area amministrativa si blocca passando in background, eccetto durante il selettore documenti necessario a importare/esportare. Non è una modalità kiosk gestita dal sistema operativo.

I tag sono riconosciuti esclusivamente tramite UID e possono essere clonati; non è prevista autenticazione crittografica. Dati e backup non sono cifrati dall'app. Effettuare backup periodici: disinstallare l'app o cancellarne i dati elimina il database locale. La precisione degli orari dipende dall'orologio del dispositivo; un salto indietro viene rifiutato se precedente all'ultima registrazione del dipendente.

## Android Studio e compilazione

Requisiti: JDK 17–21 (qui verificato JDK 21), Android SDK Platform 35 e Build Tools 35.0.0. Android Studio con supporto AGP 8.9.2 (Meerkat o successivo). Gradle Wrapper 8.13 con checksum; Kotlin / Compose compiler 2.1.20, Room 2.7.1. Aprire questa cartella come progetto e lasciare completare la sincronizzazione Gradle.

```sh
# Su una workstation con SDK già installato:
export ANDROID_HOME=/percorso/del/sdk
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

L'APK viene creato in `app/build/outputs/apk/debug/app-debug.apk`. La prima compilazione richiede Internet per scaricare dipendenze e strumenti; **l'app installata funziona offline**. Non sono necessari segreti o variabili applicative.

Nell'ambiente cloud Linux x86_64:

```sh
bash scripts/cloud-setup.sh
# Nei terminali successivi:
source scripts/cloud-env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Lo script cloud usa `/workspace/.tools` per JDK e SDK, `/workspace/.gradle` e `/workspace/.android` per le cache. Configura il proxy esistente per Java e usa il trust store CA gestito della macchina, mantenendo verifica TLS e checksum. Non modifica sorgenti o configurazioni Android Studio esistenti. Ogni task cloud è già isolato: usare il checkout esistente, senza creare altri Git worktree. La configurazione salvata dell'ambiente cattura separatamente questi passi; i processi dell'emulatore non sono necessari al normale sviluppo e non sopravvivono necessariamente alla pubblicazione.

## Struttura

- `data/Database.kt`: cinque tabelle Room, indici, FK e migrazione non distruttiva 1 → 2.
- `data/TrackerRepository.kt`: operazioni transazionali e audit amministrativo.
- `data/Backup.kt`, `data/Pin.kt`: formato backup, validazione e protezione PIN.
- `domain/Attendance.kt`: abbinamento eventi e conteggi, indipendenti dall'interfaccia.
- `domain/Reports.kt`: generazione OOXML XLSX e CSV senza server o librerie desktop.
- `ui/TrackerViewModel.kt`: stato Flow e operazioni asincrone su dispatcher IO.
- `ui/TrackerApp.kt`: terminale e schermate amministrative Compose.
- `MainActivity.kt`: NFC reader mode associato al ciclo di vita e vibrazione.
- `app/schemas/`: schema Room esportato da conservare nelle versioni successive.
- `.github/workflows/android.yml`: build, lint, test e artefatto APK in CI (alla pubblicazione del codice su GitHub).

Nessuna migrazione distruttiva è abilitata. Per una futura modifica allo schema aumentare la versione, aggiungere una migrazione e verificarla prima del rilascio. Le transazioni mantengono insieme evento, antiduplicazione e audit; l'app usa journal SQLite TRUNCATE e sincronizzazione FULL per privilegiare la durabilità.

## Verifica

I test JUnit e Robolectric reali esercitano conteggi, intervalli multipli, dipendenti intercalati, notti, mezzanotte, cambio mese, ora legale e solare, incomplete/annullate, associazioni univoche, scansioni concorrenti, antiduplicazione persistente, riapertura del database, correzioni e audit, migrazione 1 → 2, 50 dipendenti consecutivi, XLSX leggibile, escaping CSV, backup/ripristino e rifiuto di file danneggiati. I risultati dell'esecuzione corrente sono in `app/build/reports/tests/testDebugUnitTest/` e il rapporto lint in `app/build/reports/lint-results-debug.html`.

Prima dell'uso operativo verificare su **hardware NFC reale**:

1. Associare due tag a due persone e tentare l'associazione duplicata.
2. Scansionare A, B, A entro 10 secondi: le prime due timbrature devono riuscire, l'ultima essere rifiutata. Dopo l'intervallo, A deve registrare USCITA.
3. Controllare vibrazione, conferma di tre secondi, orientamento, blocco/sblocco e NFC disattivato.
4. Chiudere forzatamente e riaprire l'app; verificare dati e alternanza.
5. Esportare XLSX / CSV tramite il selettore documenti e aprirli con Excel o LibreOffice.
6. Salvare un backup, modificarne i dati di prova, ripristinarlo e controllare persone, tag, eventi, PIN e audit.

L'emulazione non verifica l'antenna NFC o il comportamento fisico dei tag; non sostituisce questo collaudo.
