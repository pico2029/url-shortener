# Piano di lavoro e User Story

## Stato del progetto

Docker Compose, PostgreSQL, Redis e Nginx sono configurati. Sono presenti registrazione con conferma password, persistenza utenti, login JWT e navigazione tra `/register`, `/login` e una dashboard inglese con creazione autenticata, elenco personale paginato e pulsante Log out. US01 aggiunge creazione anonima di link, validazione HTTP/HTTPS, codici Base62 univoci e persistenza PostgreSQL tramite API e form React. US04 e US05 includono logout locale, scadenza della sessione e isolamento dei link per proprietario. US02 aggiunge redirect pubblici `302` e pagine HTML inglesi per codici inesistenti (`404`) e persistenza indisponibile (`503`). US06 aggiunge copia con un clic, conferma ed errore accessibili nel risultato e nell’elenco personale. US07 aggiunge eliminazione definitiva riservata al proprietario e conferma inline nella dashboard. US08 aggiunge alias personalizzati facoltativi alla creazione autenticata. US09 aggiunge scadenza facoltativa, visualizzazione locale e redirect 410 alla scadenza. US10 aggiunge statistiche best effort dei GET, con totale storico e conteggi giornalieri UTC; verifica Nginx/browser ancora da completare.

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

Implementata: `GET` e `HEAD /r/{shortCode}` pubblici, ricerca esatta e sensibile alle maiuscole su PostgreSQL, `Location` con destinazione originale e `Cache-Control: no-store`. US11 aggiunge cache Redis facoltativa con fallback PostgreSQL; US09 aggiunge scadenza e risposte 410; US10 registra i GET validi in modo asincrono best effort, escludendo HEAD. Gli errori `404`, `410` e `503` restituiscono pagine HTML inglesi accessibili senza JavaScript. Parametri aggiunti al link breve non vengono trasferiti alla destinazione.

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

**Implementata:** logout locale, annullamento richieste, verifica server, scadenza e protezione da risposte tardive.

**Come utente registrato, voglio accedere al mio account e uscire dalla sessione, per utilizzare le funzionalità personali.**

Responsabile: P2.

Criteri di accettazione:

- Credenziali valide consentono l'accesso tramite JWT.
- Credenziali errate mostrano un errore.
- Un token scaduto richiede un nuovo accesso.
- Il logout rimuove la sessione dal client.

### US05 — Visualizzare i propri link

**Implementata:** proprietà derivata dal JWT, migrazione V4 senza assegnare i link anonimi, elenco paginato e dashboard inglese. Stato attuale `SAVED` (“Saved”), con redirect pubblici forniti da US02.

**Come utente autenticato, voglio vedere i link che ho creato, per consultarli e gestirli.**

Responsabile: P1.

Criteri di accettazione:

- L'elenco mostra soltanto i link dell'utente autenticato.
- Ogni elemento mostra destinazione, link breve, data di creazione e stato.
- Sono gestiti caricamento, errori ed elenco vuoto.
- Le API impediscono l'accesso ai link di altri utenti.

### US06 — Copiare un link

**Implementata; verifica browser finale da completare:** pulsante riutilizzabile nel risultato di creazione anonima/autenticata e in ogni riga della dashboard. Copia il `shortUrl` completo tramite Clipboard API, mostra conferma dopo il successo e segnala API assente o copia rifiutata indicando la copia manuale. Ogni pulsante ha stato indipendente, impedisce tentativi duplicati e ignora completamenti dopo cambio URL o smontaggio. Contesto sicuro richiesto: localhost in sviluppo, HTTPS per hosting pubblico; nessun fallback legacy.

**Verifica US06:** build frontend e 17 test della feature link superati, inclusi 10 nuovi test di copia. Frontend aggiornato sano attraverso Docker Compose con il backend locale esistente. Test di registrazione allineati ai limiti attuali: suite frontend completa superata (41 test), build backend superata (57 test unitari). Incolla dagli appunti reali e verifica visiva desktop/mobile restano da eseguire: gli strumenti browser non erano disponibili/autorizzati nella sessione. I test simulano la Clipboard API e non certificano gli appunti reali.

**Come utente, voglio copiare un link breve con un clic, per condividerlo rapidamente.**

Responsabile: P1.

Criteri di accettazione:

- Il pulsante copia l'URL breve completo.
- Viene mostrata una conferma della copia.
- Un eventuale errore di copia viene segnalato.

### US07 — Eliminare un link

**Implementata per l’architettura attuale; verifica browser finale da completare:** `DELETE /api/v1/links/{shortCode}` autenticato, cancellazione transazionale condizionata da codice e proprietario JWT, `204` dopo commit e `404 LINK_NOT_FOUND` indistinguibile per link inesistenti, anonimi, altrui o già eliminati. Dashboard con conferma inline, gestione focus, errore/riprova e aggiornamento della pagina valida dopo eliminazione. I redirect eliminati restituiscono `404`.

**Integrazione US11:** dopo il commit, eliminazione e risoluzione condividono lock locali per impedire reinserimenti obsoleti. La chiave Redis viene invalidata prima del 204; se Redis non risponde, il namespace interessato viene abbandonato. Richieste iniziate dopo il 204 restituiscono 404; redirect già in corso possono completarsi. La garanzia riguarda il singolo backend e le modifiche eseguite tramite applicazione.

**Verifica storica US09:** sei scenari mirati aggiunti (tre backend, uno parametrizzato; tre frontend). Superati 23 test unitari backend, 13 verifiche con PostgreSQL, 31 test frontend pertinenti e build frontend. Test di registrazione allineati ai limiti attuali: suite frontend completa superata (41 test), build Docker standard superata (57 test unitari backend) e servizi aggiornati avviati sani nello stack normale. Verifica nel browser di conferma, tastiera e layout da completare: strumenti browser indisponibili/non autorizzati nella sessione.

**Come utente autenticato, voglio eliminare un mio link, per impedirne ulteriori utilizzi.**

Responsabile: P1.

Criteri di accettazione:

- L'interfaccia richiede una conferma prima dell'eliminazione.
- Soltanto il proprietario può eliminare il link.
- Il link eliminato scompare dall'elenco.
- Il link non funziona più, anche se era presente nella cache.

### US08 — Scegliere un alias

Implementata: `POST /api/v1/links` accetta `alias` facoltativo. Dopo `String.strip()`, valori omessi, null o vuoti generano un codice Base62 di 8 caratteri; un alias non vuoto richiede JWT e 3–32 caratteri ASCII tra lettere, numeri, `-` e `_`. Maiuscole e minuscole restano distinte, senza parole riservate. Alias e codici generati condividono l'unicità globale PostgreSQL: collisioni esplicite restituiscono `409 ALIAS_ALREADY_TAKEN`, alias invalidi `400 INVALID_ALIAS`, assenza di login `401`. La dashboard riusa il form e gestisce gli errori; i redirect restano pubblici. Gli alias si integrano con proprietà ed elenco di US05: il JWT determina il proprietario e la dashboard aggiorna la lista dopo la creazione. Eliminazione, scadenza e cache Redis sono integrate; la verifica combinata della scadenza in cache è superata. Il contratto completo è documentato nel README.

**Come utente autenticato, voglio scegliere un codice personalizzato, per ottenere un link riconoscibile.**

Responsabili: P1 backend, P2 form.

Criteri di accettazione:

- L'alias è facoltativo.
- Rispetta regole documentate su lunghezza e caratteri consentiti.
- Alias già occupati vengono rifiutati con un errore comprensibile.
- In assenza di alias viene generato un codice univoco.

### US09 — Impostare una scadenza

**Implementata:** `expiresAt` facoltativo nella creazione autenticata, timestamp ISO-8601 con offset/Z e validazione futura sul server alla precisione dei microsecondi. Migrazione V5 con `TIMESTAMPTZ` nullable: link esistenti senza scadenza. Form e dashboard mostrano ora locale e fuso del browser, `No expiration` oppure scadenza, e stato `EXPIRED` al caricamento/refresh dell’elenco. GET/HEAD restituiscono `410` dall’istante esatto di scadenza, senza Location e con no-store; GET mostra una pagina HTML inglese accessibile. I link scaduti restano copiabili/eliminabili e non liberano l’alias.

**Verifica storica US09:** sei scenari mirati aggiunti; 56 test frontend, 72 test unitari backend e 22 integrazioni PostgreSQL superati. Le integrazioni sono passate prima del ripristino dell’ordine delle migrazioni compatibile con il database locale; la riesecuzione è stata rifiutata. Build Docker standard e migrazione/avvio locale successivi verificano V3 proprietari, V4 alias e V5 scadenza, senza modificare lo storico o cancellare dati.

**Verifica browser storica US09:** su localhost verificati creazione senza scadenza e con alias/scadenza, ora locale Europe/Rome nel risultato e nell’elenco, errore accessibile con conservazione dei dati e focus, invio e annullamento da tastiera. Verificato il passaggio reale dalla destinazione alla pagina Link expired e lo stato Expired dopo refresh, con copia del link scaduto negli appunti reali. Le intestazioni GET/HEAD sono coperte dalle integrazioni automatiche; l’ulteriore controllo HTTP locale da terminale non è stato autorizzato. La verifica combinata della cache è riportata in US11.

**Integrazione US11:** le voci Redis ricevono expiresAt dalla persistenza; la risoluzione verifica la scadenza prima del redirect, anche dopo la scrittura in cache. Il TTL è limitato alla durata residua. I test combinati della scadenza in cache sono superati, inclusa una chiave Redis ancora presente alla scadenza.

**Come utente autenticato, voglio impostare una scadenza, per rendere temporaneo un link.**

Responsabili: P1 backend, P2 form.

Criteri di accettazione:

- La scadenza è facoltativa e deve essere futura.
- La scadenza viene mostrata nella dashboard.
- Dopo la scadenza il link restituisce `410 Gone`.
- La scadenza viene rispettata anche quando il link è presente nella cache.

### US10 — Consultare le statistiche

**Implementata; verifica Nginx/browser da completare:** registrazione asincrona best effort dei GET validi su PostgreSQL, inclusi hit Redis. HEAD ed errori non contano; bot e visite ripetute contano separatamente. Coda limitata configurabile (1000 eventi), senza retry: guasti/saturazione/arresti possono perdere clic senza interrompere il redirect. Ogni evento conserva ID persistente e timestamp della richiesta, senza IP/user agent/referrer. Cache v2 con ID protegge il riutilizzo degli alias. Migrazione V6 con FK cascade: eliminazione rimuove gli eventi e impedisce inserimenti tardivi sul nuovo link.

`GET /api/v1/links/{shortCode}/stats` autenticato restituisce totale storico e 30 giorni UTC inclusivi di oggi, con zeri nei giorni vuoti. Proprietario derivato dal JWT; link anonimi/altrui/inesistenti condividono 404, sessione invalida 401, persistenza indisponibile 503. Link scaduti restano consultabili. Proprietà, totale e giorni condividono uno snapshot repeatable-read. Dashboard inglese con un solo pannello Statistics aperto, grafico SVG, tabella Daily counts, refresh, caricamento, zero, retry e cancellazione delle richieste obsolete.

**Decisioni US10 confermate:** “ogni redirect valido” indica qui ogni GET risolto validamente; HEAD è escluso. La registrazione è best effort, quindi il criterio non garantisce consegna durevole durante guasti. Totale e giorni riflettono soltanto eventi salvati dall’attivazione di US10; i clic precedenti non sono ricostruibili. Nessuna deduplicazione, filtro temporale o statistica dei visitatori unici.

**Verifica del 10 ottobre 2026:** superati 138 test backend (93 unitari, 9 integrazioni registrazione, 36 integrazioni PostgreSQL/Redis), senza fallimenti/errori/test saltati, con `docker compose -p url-shortener-us10-check run --rm --no-deps backend-tests mvn -B -ntp -Pintegration verify`. Superati 63 test frontend, build frontend e validazione Compose. Coperti GET miss/hit, HEAD/errori, proprietà, UTC, zero, concorrenti, guasti, coda piena, cascade/alias ed eventi pendenti, snapshot coerente, cancellazione/sessione/tastiera. La build delle immagini Docker non è stata autorizzata: stack US10, smoke Nginx e verifica visiva desktop/mobile restano da eseguire. Nessun volume database o storico Flyway modificato; verifiche storiche distinte.

**Come proprietario di un link, voglio vedere quanti clic riceve e il loro andamento nel tempo, per capire quanto viene utilizzato.**

Responsabili: P1 backend, P2 interfaccia.

Criteri di accettazione:

- Ogni GET risolto validamente tenta la registrazione asincrona di un clic, compresi gli hit cache; HEAD escluso e consegna best effort come concordato.
- Sono disponibili il totale dei clic e i conteggi giornalieri.
- Soltanto il proprietario può consultare le statistiche del link.
- Un link senza clic mostra zero.
- Il raggruppamento giornaliero usa un fuso orario documentato.

### US11 — Avere redirect rapidi

**Implementata e verificata nel singolo backend, inclusa la scadenza:** cache dei redirect validi con TTL configurabile (5 minuti), hit senza query PostgreSQL e senza rinnovo del TTL, fallback su miss o errore Redis. Coordinamento locale in 256 gruppi di lock e namespace per processo/generazione proteggono eliminazione, recupero Redis e riavvio backend. Timeout Redis predefiniti: 100 ms per connessione e comando. Redis non blocca avvio o salute del backend. Nessuna cache negativa. US09 fornisce persistenza, controllo PostgreSQL e risposta 410. Il record cache conserva expiresAt, il TTL è limitato alla durata residua e la risoluzione controlla la scadenza prima di restituire la destinazione. Voci scadute o con durata inferiore a un millisecondo causano fallback PostgreSQL. Nessuna modifica frontend o migrazione US11. Verifica con test unitari, concorrenza deterministica, PostgreSQL/Redis Testcontainers e smoke test Nginx; nessun obiettivo numerico di latenza.

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

Lo script [scripts/verify.sh](scripts/verify.sh) verifica creazione anonima, registrazione, login, isolamento di due account e persistenza di link e utenti dopo la ricreazione dei container. Verifica anche redirect pubblici `302`, pagina `404` e apertura del link persistito. Le API private e Swagger/OpenAPI restano protetti con `401`; aggiornare le verifiche con l'implementazione delle storie successive.

Il progetto è completato quando due utenti possono registrarsi, creare e gestire separatamente i propri link; redirect, alias, scadenze, cache, limiti e statistiche funzionano; i test passano e il README descrive l'app reale.

**Verifica del merge US09/US11:** 87 test unitari backend, 38 integrazioni (9 registrazione e 29 PostgreSQL/Redis) e 56 test frontend superati, senza errori o test saltati. `docker compose run --rm --no-deps backend-tests mvn -B -ntp -Pintegration clean verify`, `npm test`, `npm run build`, validazione Compose e build delle due immagini superati. Verificati TTL limitato, scadenza durante la scrittura in cache, GET/HEAD 410 con chiave Redis ancora presente e un solo Clock condiviso. Le verifiche storiche precedenti restano distinte; nessuno smoke test sullo stack attivo è stato eseguito e nessun volume dati è stato rimosso.
