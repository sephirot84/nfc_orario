# Verifica della consegna

Eseguita il 15 ottobre 2026 nel checkout `/workspace/nfc_orario`.

| Verifica | Esito |
| --- | --- |
| Compilazione APK debug | Superata |
| Firma APK (apksigner) | Valida, schema v2 |
| Test conteggi `AttendanceTest` | 12 superati |
| Test Room / repository `RepositoryTest` | 12 superati |
| Test XLSX / CSV / backup / PIN `ReportsBackupTest` | 9 superati |
| Test interfaccia Compose `InterfaceTest` | 1 superato |
| Totale test | **34 superati, 0 falliti, 0 errori, 0 saltati** |
| Android lint debug | **0 errori, 0 avvisi** |
| XLSX aperto con openpyxl indipendente | Tre fogli, dipendente e durata numerica 08:30 corretti |
| Script cloud eseguito e ripetuto | Build, test e lint completati |
| Installazione APK su emulatore Android 10 | Riuscita |
| Avvio attività su emulatore | Attività avviata; collaudo manuale limitato da blocchi di System UI nell'emulatore software senza KVM |
| Hardware NFC / vibrazione | **Non verificati su dispositivo fisico** |

Il test dell'interfaccia esegue l'attività reale con Compose sotto Robolectric: apertura del terminale, accesso amministrativo iniziale senza PIN, inserimento di Mario Rossi, salvataggio, ricreazione dell'attività, verifica del dipendente e ritorno al terminale. Il database è reale SQLite nelle prove Robolectric; il test di riapertura chiude e riapre lo stesso file. Il test di concorrenza invia 12 letture dello stesso UID in parallelo e verifica un unico evento salvato. La migrazione ricostruisce lo schema v1 e verifica il passaggio a v2 mantenendo persone, eventi e tag. Un altro test esegue 50 scansioni di dipendenti distinti senza attese fra persone.

L'APK finale è una build di sviluppo firmata, versione 1.0.0, package `it.nfctimetracker`, min SDK 29 e target SDK 35. Non contiene il permesso INTERNET. Per uso operativo seguire la checklist nel README su un dispositivo NFC reale, includendo il selettore documenti, Excel/LibreOffice, backup e ripristino con PIN. La presenza di test superati non sostituisce la verifica dell'antenna, dei tag e del comportamento OEM.

I report completi rigenerabili sono `app/build/reports/tests/testDebugUnitTest/index.html` e `app/build/reports/lint-results-debug.html`. Il file XLSX prodotto durante il test è `app/build/test-artifacts/report.xlsx`. Questi output di build non sono inclusi nei sorgenti Git.

Sono stati salvati nella bozza cloud i campi `install_script` e `start_skill`. Il salvataggio non pubblica l'ambiente: revisione, salvataggio e pubblicazione restano nelle impostazioni dell'ambiente. Non è stata verificata la ripresa in un nuovo task dopo la pubblicazione.
