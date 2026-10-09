# Piano di lavoro e User Story

## Stato del progetto

Docker Compose, PostgreSQL, Redis e Nginx sono configurati. Sono presenti registrazione con conferma password, persistenza utenti, login JWT e navigazione tra `/register`, `/login` e una dashboard con form di creazione autenticata. US01 aggiunge creazione anonima di link, validazione HTTP/HTTPS, codici Base62 univoci e persistenza PostgreSQL tramite API e form React. US02 aggiunge redirect pubblici `302` e pagine HTML inglesi per codici inesistenti (`404`) e indisponibilità della persistenza (`503`). US08 aggiunge alias personalizzati facoltativi per utenti autenticati. Logout, proprietà dei link e gestione personale restano da implementare.

Questo documento definisce il lavoro necessario per realizzare le funzionalità previste nel [README](README.md): link brevi, account, dashboard, alias personalizzati, scadenza, cache, limiti delle richieste e statistiche. Il deployment pubblico resta fuori dal perimetro attuale.

La pianificazione deriva dalla lettura del codice e della documentazione; non certifica l'esecuzione di build o servizi.

## Divisione del lavoro tra due persone

P1 e P2 identificano le due persone responsabili. Ogni persona cura anche i test e la documentazione delle proprie funzionalità.

| Area | P1 — Link e dati | P2 — Account e interfaccia |
|---|---|---|
| Backend principale | Creazione link, codici Base62 univoci, redirect `302`, elenco ed eliminazione | Registrazione, login, hashing delle password, JWT e configurazione Spring Security |
| Database | Entità e migrazioni di link ed eventi di clic | Entità e migrazione utenti |
| Funzionalità avanzate | Alias personalizzati, scadenza, cache Redis e invalidazione | Limiti delle richieste con Redis |
| Statistiche | Registrazione clic e API delle statistiche | Visualizzazione delle statistiche |
| Frontend | Componenti della dashboard: elenco, copia ed eliminazione link | Struttura dell'app, client API, login, registrazione e form di creazione |
| Verifica | Test di link, redirect, cache e statistiche | Test di autenticazione, autorizzazione, limiti e flussi frontend |

### Decisioni e coordinamento iniziale

Prima di implementare le funzionalità, concordare:

- Richieste e risposte delle API, formato degli errori e codici HTTP.
- Gestione della sessione e modalità di utilizzo del JWT dal frontend.
- Regole di proprietà dei link: P2 rende disponibile l'identità autenticata e P1 la usa per autorizzare le operazioni sui link.
- Numerazione delle migrazioni Flyway, per evitare conflitti.
- Regole degli alias, fuso orario delle statistiche e soglie del rate limiting.

**Decisione di prodotto confermata in PRODUCT.md:** la creazione di link sarà consentita senza account. Gli account serviranno alla gestione personale. La gestione o rivendicazione successiva dei link anonimi resta da definire; la registrazione non li assegna automaticamente al nuovo utente.

## User Story

### US01 — Accorciare un URL

**Come utente, voglio trasformare un URL lungo in un link breve, per condividerlo facilmente.**

Responsabili: P1 backend, P2 form.

Criteri di accettazione:

- Il servizio accetta URL HTTP/HTTPS validi.
- Genera un codice univoco e salva il link in PostgreSQL.
- Mostra l'URL breve completo all'utente.
- Segnala gli URL non validi senza creare un link.

### US02 — Aprire un link breve

Implementata: `GET` e `HEAD /r/{shortCode}` pubblici, ricerca esatta e sensibile alle maiuscole su PostgreSQL, `Location` con destinazione originale e `Cache-Control: no-store`. Nessuna cache, statistica o scadenza anticipata. Gli errori `404` e `503` restituiscono pagine HTML inglesi accessibili senza JavaScript. Parametri aggiunti al link breve non vengono trasferiti alla destinazione.

**Come visitatore, voglio aprire un link breve, per raggiungere la pagina originale.**

Responsabile: P1.

Criteri di accettazione:

- Un link valido restituisce un redirect `302` verso la destinazione originale.
- Un codice inesistente restituisce `404`.
- Il redirect è accessibile senza login.

### US03 — Registrarsi

**Come nuovo utente, voglio creare un account, per gestire i miei link.**

Responsabile: P2.

Criteri di accettazione:

- Email e password vengono validate secondo regole documentate.
- Un'email già registrata viene rifiutata.
- La password viene salvata tramite hashing e non in chiaro.
- Il form verifica la conferma password. Dopo la registrazione effettua l'accesso automatico e apre la dashboard; gli errori sono comprensibili.

### US04 — Accedere e uscire

**Come utente registrato, voglio accedere al mio account e uscire dalla sessione, per utilizzare le funzionalità personali.**

Responsabile: P2.

Criteri di accettazione:

- Credenziali valide consentono l'accesso tramite JWT.
- Credenziali errate mostrano un errore.
- Un token scaduto richiede un nuovo accesso.
- Il logout rimuove la sessione dal client.

### US05 — Visualizzare i propri link

**Come utente autenticato, voglio vedere i link che ho creato, per consultarli e gestirli.**

Responsabile: P1.

Criteri di accettazione:

- L'elenco mostra soltanto i link dell'utente autenticato.
- Ogni elemento mostra destinazione, link breve, data di creazione e stato.
- Sono gestiti caricamento, errori ed elenco vuoto.
- Le API impediscono l'accesso ai link di altri utenti.

### US06 — Copiare un link

**Come utente, voglio copiare un link breve con un clic, per condividerlo rapidamente.**

Responsabile: P1.

Criteri di accettazione:

- Il pulsante copia l'URL breve completo.
- Viene mostrata una conferma della copia.
- Un eventuale errore di copia viene segnalato.

### US07 — Eliminare un link

**Come utente autenticato, voglio eliminare un mio link, per impedirne ulteriori utilizzi.**

Responsabile: P1.

Criteri di accettazione:

- L'interfaccia richiede una conferma prima dell'eliminazione.
- Soltanto il proprietario può eliminare il link.
- Il link eliminato scompare dall'elenco.
- Il link non funziona più, anche se era presente nella cache.

### US08 — Scegliere un alias

Implementata: `POST /api/v1/links` accetta `alias` facoltativo. Dopo `String.strip()`, valori omessi, null o vuoti generano un codice Base62 di 8 caratteri; un alias non vuoto richiede JWT e 3–32 caratteri ASCII tra lettere, numeri, `-` e `_`. Maiuscole e minuscole restano distinte, senza parole riservate. Alias e codici generati condividono l'unicità globale PostgreSQL: collisioni esplicite restituiscono `409 ALIAS_ALREADY_TAKEN`, alias invalidi `400 INVALID_ALIAS`, assenza di login `401`. La dashboard riusa il form e gestisce gli errori; i redirect restano pubblici. US08 non introduce proprietà dei link, elenco, eliminazione, scadenza o cache. Il contratto completo è documentato nel README.

**Come utente autenticato, voglio scegliere un codice personalizzato, per ottenere un link riconoscibile.**

Responsabili: P1 backend, P2 form.

Criteri di accettazione:

- L'alias è facoltativo.
- Rispetta regole documentate su lunghezza e caratteri consentiti.
- Alias già occupati vengono rifiutati con un errore comprensibile.
- In assenza di alias viene generato un codice univoco.

### US09 — Impostare una scadenza

**Come utente autenticato, voglio impostare una scadenza, per rendere temporaneo un link.**

Responsabili: P1 backend, P2 form.

Criteri di accettazione:

- La scadenza è facoltativa e deve essere futura.
- La scadenza viene mostrata nella dashboard.
- Dopo la scadenza il link restituisce `410 Gone`.
- La scadenza viene rispettata anche quando il link è presente nella cache.

### US10 — Consultare le statistiche

**Come proprietario di un link, voglio vedere quanti clic riceve e il loro andamento nel tempo, per capire quanto viene utilizzato.**

Responsabili: P1 backend, P2 interfaccia.

Criteri di accettazione:

- Ogni redirect valido registra un clic, compresi quelli serviti tramite cache.
- Sono disponibili il totale dei clic e i conteggi giornalieri.
- Soltanto il proprietario può consultare le statistiche del link.
- Un link senza clic mostra zero.
- Il raggruppamento giornaliero usa un fuso orario documentato.

### US11 — Avere redirect rapidi

**Come visitatore, voglio che i link si aprano rapidamente, per raggiungere subito la destinazione.**

Responsabile: P1.

Criteri di accettazione:

- I link risolti vengono memorizzati in Redis con un TTL definito.
- Le richieste successive possono risolvere il link dalla cache.
- Eliminazione e scadenza sono rispettate anche dalla cache.
- Se la cache non è disponibile, la risoluzione usa PostgreSQL.

### US12 — Limitare gli abusi

**Come gestore del servizio, voglio limitare le richieste eccessive, per mantenere il servizio disponibile.**

Responsabile: P2.

Criteri di accettazione:

- Gli endpoint protetti, il criterio di identificazione del chiamante, le soglie e le finestre temporali sono documentati.
- Soglie e finestre temporali sono configurabili.
- Il superamento del limite restituisce `429` con `Retry-After`.
- Al termine della finestra le richieste vengono nuovamente accettate.
- Il comportamento in caso di indisponibilità di Redis è definito e verificato.

## Ordine di implementazione

| Fase | Storie | P1 | P2 | Risultato verificabile |
|---|---|---|---|---|
| 1. Primo flusso completo | US01–US02 | Persistenza, creazione e redirect | Form, client API e configurazione degli accessi agli endpoint | Si inserisce un URL e il link breve apre la destinazione |
| 2. Account e gestione | US03–US07 | Elenco, eliminazione, proprietà dei link e dashboard | Registrazione, login, JWT e gestione della sessione | Ogni utente vede e gestisce soltanto i propri link |
| 3. Funzionalità avanzate | US08–US09, US11–US12 | Alias, scadenza e cache Redis | Campi nel form e rate limiting | Alias duplicati e link scaduti sono gestiti; i limiti restituiscono `429` |
| 4. Statistiche | US10 | Eventi di clic e API statistiche | Schermata statistiche | I redirect validi aggiornano statistiche visibili al proprietario |
| 5. Chiusura | Tutte | Test integrati con PostgreSQL e Redis | Test dei flussi completi nel browser | Applicazione verificata tramite Docker Compose |

### Dipendenze principali

- US02 dipende dalla persistenza dei link di US01.
- US05 e US07 richiedono l'identità autenticata di US03–US04.
- US06 può essere sviluppata appena il frontend riceve un URL breve da US01.
- US08 e US09 estendono il flusso di creazione e richiedono autenticazione.
- US10 dipende dai redirect di US02 e dalla proprietà dei link.
- US11 deve integrarsi con eliminazione, scadenza e registrazione dei clic.
- US12 richiede la definizione degli endpoint e delle regole di identificazione dei chiamanti.

## Definizione di completamento

Una User Story è completata quando:

- Tutti i criteri di accettazione sono verificati.
- I test pertinenti passano, includendo errori e controlli di autorizzazione applicabili.
- La documentazione delle API e le istruzioni di configurazione sono aggiornate.
- La funzionalità è utilizzabile attraverso Docker Compose e il proxy Nginx.
- L'interfaccia gestisce gli stati di caricamento, successo ed errore applicabili.

Lo script [scripts/verify.sh](scripts/verify.sh) verifica creazione anonima, redirect pubblici `302`, pagina `404`, registrazione e persistenza di link e utenti dopo la ricreazione dei container, inclusa l'apertura del link persistito. Le API private e Swagger/OpenAPI continuano a restituire `401` senza autenticazione; aggiornare le verifiche con l'implementazione delle storie successive.

Il progetto è completato quando due utenti possono registrarsi, creare e gestire separatamente i propri link; redirect, alias, scadenze, cache, limiti e statistiche funzionano; i test passano e il README descrive l'app reale.
