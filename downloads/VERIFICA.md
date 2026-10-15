# Verifica della consegna

Eseguita il 15 ottobre 2026 nel checkout `/workspace/nfc_orario`.

| Verifica | Esito |
| --- | --- |
| Compilazione APK debug | Superata |
| Firma APK (apksigner) | Valida, schema v2 |
| Nome applicazione nell'APK (aapt) | NFC Time Tracker 1.0.1 |
| Certificato di firma rispetto all'APK 1.0.0 | Identico, aggiornamento installabile senza disinstallare |
| Test conteggi `AttendanceTest` | 12 superati |
| Test Room / repository `RepositoryTest` | 12 superati |
| Test XLSX / CSV / backup / PIN `ReportsBackupTest` | 9 superati |
| Test interfaccia Compose `InterfaceTest` | 1 superato |
| Test callback NFC e conferme `NfcScanTest` | 1 superato |
| Totale test | **35 superati, 0 falliti, 0 errori, 0 saltati** |
| Android lint debug | **0 errori, 0 avvisi** |
| XLSX aperto con openpyxl indipendente | Tre fogli, dipendente e durata numerica 08:30 corretti |
| Script cloud eseguito e ripetuto | Build, test e lint completati |
| Installazione APK su emulatore Android 10 | Riuscita |
| Avvio attività su emulatore | Attività avviata; collaudo manuale limitato da blocchi di System UI nell'emulatore software senza KVM |
| Hardware NFC / vibrazione | **Non verificati su dispositivo fisico** |

Il test dell'interfaccia esegue l'attività reale con Compose sotto Robolectric: apertura del terminale, accesso amministrativo iniziale senza PIN, inserimento di Mario Rossi, salvataggio, ricreazione dell'attività, verifica del dipendente e ritorno al terminale. Il database è reale SQLite nelle prove Robolectric; il test di riapertura chiude e riapre lo stesso file. Il test di concorrenza invia 12 letture dello stesso UID in parallelo e verifica un unico evento salvato. La migrazione ricostruisce lo schema v1 e verifica il passaggio a v2 mantenendo persone, eventi e tag. Un altro test esegue 50 scansioni di dipendenti distinti senza attese fra persone.

Il nome visualizzato nel launcher Android e nella schermata principale è **NFC Time Tracker 1.0.1**, generato dal `versionName` della build. L'APK corrente è una build di sviluppo firmata, versione 1.0.1 (versionCode 2), package `it.nfctimetracker`, min SDK 29 e target SDK 35. Non contiene il permesso INTERNET. Per uso operativo seguire la checklist nel README su un dispositivo NFC reale, includendo il selettore documenti, Excel/LibreOffice, backup e ripristino con PIN. La presenza di test superati non sostituisce la verifica dell'antenna, dei tag e del comportamento OEM.

Per la segnalazione di alternanza indipendente dall'UID, sono stati rieseguiti tutti i 35 test, lint e assembleDebug con esito positivo. Il nuovo test invia tag simulati al callback NFC registrato da MainActivity, controllando il percorso attività → ViewModel → database e la conferma Compose: A ENTRATA, B ENTRATA, tag sconosciuto rifiutato senza variazioni al database, A USCITA, B USCITA. Gli UID includono byte oltre 0x7F per verificare anche la conversione esadecimale. La conferma della versione 1.0.1 mostra l'UID letto sia per le timbrature riuscite sia per gli errori. La regola di alternanza per dipendente non è stata modificata: il difetto segnalato non è stato riprodotto nei sorgenti locali. Non è stato possibile confrontare i sorgenti con l'APK effettivamente installato dall'utente. La versione 1.0.1 è una build per la diagnosi, non una correzione confermata del problema sul dispositivo.

I report completi rigenerabili sono `app/build/reports/tests/testDebugUnitTest/index.html` e `app/build/reports/lint-results-debug.html`. Il file XLSX prodotto durante il test è `app/build/test-artifacts/report.xlsx`. Questi output di build non sono inclusi nei sorgenti Git.

Sono stati salvati nella bozza cloud i campi `install_script` e `start_skill`. Il salvataggio non pubblica l'ambiente: revisione, salvataggio e pubblicazione restano nelle impostazioni dell'ambiente. Non è stata verificata la ripresa in un nuovo task dopo la pubblicazione.
