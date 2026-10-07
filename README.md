# URL Shortener

Scheletro di un’applicazione per accorciare URL, con **backend Java/Spring Boot**, **frontend React/TypeScript**, **PostgreSQL** e **Redis**. Build ed esecuzione sono gestite interamente tramite **Docker Compose**.

## Stato del progetto

Il progetto contiene la struttura iniziale e le configurazioni necessarie per sviluppare le funzionalità in modo incrementale.

| Disponibile | Da implementare |
|---|---|
| Punto d’avvio Spring Boot | Creazione e redirect dei link |
| Pagina React statica | Dashboard e gestione dei link |
| Configurazione PostgreSQL e Redis | Cache applicativa e rate limiting |
| Flyway e Spring Data JPA configurati | Entità, repository e migrazioni di dominio |
| Spring Security con comportamento automatico | Registrazione, login e JWT |
| OpenAPI, Swagger UI e health check | Documentazione degli endpoint applicativi |
| Dockerfile multistadio e Docker Compose | Analytics, alias e scadenze |
| Script di verifica dell’infrastruttura | Test unitari e di integrazione delle funzionalità |

**Non sono ancora presenti endpoint applicativi.** L’unica classe Java è `ShortenerApplication`; le cartelle delle funzionalità contengono `.gitkeep`, senza classi vuote preventive.

## Stack tecnologico

| Area | Tecnologie configurate |
|---|---|
| Backend | Java 21, Spring Boot 3.5.7, Spring Web |
| Persistenza | Spring Data JPA, PostgreSQL 17.6, PostgreSQL JDBC |
| Migrazioni | Flyway con supporto PostgreSQL |
| Cache | Redis 7.4.6, Spring Data Redis |
| Sicurezza | Spring Security; JWT pianificato |
| Frontend | React 19.2.0, TypeScript 5.9.3, HTML e CSS |
| Build frontend | Vite 7.3.7, Node.js 22.21.1 e npm |
| Build backend | Maven 3.9.11 |
| Server e reverse proxy | Nginx 1.28.0 |
| Documentazione API | OpenAPI e Swagger UI tramite springdoc-openapi 2.8.13 |
| Stato del backend | Spring Boot Actuator |
| Dipendenze di test | JUnit 5, Mockito, Testcontainers |
| Containerizzazione | Docker e Docker Compose |

JUnit, Mockito e Testcontainers sono predisposti come dipendenze: non ci sono ancora test Java. La repository attuale **non contiene workflow GitHub Actions**; i controlli si eseguono manualmente con lo script incluso.

## Architettura

Il backend è un monolite organizzato per funzionalità. Frontend e backend hanno build e immagini indipendenti e comunicano attraverso Nginx.

```text
Browser
  |
  v
http://localhost:8080
  |
  v
frontend / Nginx :80
  |-- /                         -> React compilato
  |-- /api/*                    -> backend :8080
  |-- /r/*                      -> backend :8080
  |-- /swagger-ui, /swagger-ui/* -> backend :8080
  |-- /swagger-ui.html           -> backend :8080
  `-- /v3/api-docs, /v3/api-docs/* -> backend :8080
                                      |
                                      |-- PostgreSQL :5432
                                      `-- Redis :6379
```

### Servizi Docker

| Servizio | Responsabilità | Accesso dall’host |
|---|---|---|
| `frontend` | Nginx serve la SPA e inoltra le richieste al backend | Porta `8080`, configurabile |
| `backend` | Esegue Spring Boot e le future API | Nessuna porta pubblicata |
| `postgres` | Conserva i dati in un volume nominato | Nessuna porta pubblicata |
| `redis` | Cache ricostruibile, senza persistenza | Nessuna porta pubblicata |

La rete `ingress` collega frontend e backend. La rete `data`, configurata come interna, collega backend, PostgreSQL e Redis. Il frontend non accede direttamente ai servizi dati.

Gli health check attendono PostgreSQL e Redis prima del backend e il backend prima del frontend. Actuator espone internamente `/actuator/health`, includendo database e Redis senza dettagli sensibili; Nginx ne blocca l’accesso esterno. Nginx espone `/healthz` per il proprio health check.

I Dockerfile usano build multistadio: Maven/JDK produce il JAR eseguito con Java; Node/npm produce i file statici serviti da Nginx. Maven e npm non devono essere installati sull’host.

## Struttura del repository

```text
.
├── backend/
│   ├── src/main/java/com/example/shortener/
│   │   ├── ShortenerApplication.java
│   │   ├── link/
│   │   ├── auth/
│   │   ├── user/
│   │   ├── analytics/
│   │   ├── security/
│   │   ├── ratelimit/
│   │   └── shared/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   ├── src/test/java/
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   ├── features/
│   │   │   ├── links/
│   │   │   ├── auth/
│   │   │   └── analytics/
│   │   └── shared/
│   ├── index.html
│   ├── package.json
│   ├── package-lock.json
│   ├── tsconfig.json
│   ├── nginx.conf
│   ├── Dockerfile
│   └── .dockerignore
├── scripts/verify.sh
├── compose.yml
├── .env.example
├── .gitignore
└── README.md
```

### Confini delle funzionalità

| Modulo backend | Responsabilità futura |
|---|---|
| `link` | Creazione, gestione, alias, scadenza, cache e redirect |
| `auth` | Registrazione e login |
| `user` | Dati e persistenza degli utenti |
| `analytics` | Eventi click e statistiche |
| `security` | Verifica JWT e autorizzazioni |
| `ratelimit` | Limiti delle richieste tramite Redis |
| `shared` | Configurazioni comuni e gestione degli errori |

All’interno dei moduli, controller, servizi, repository, entità e DTO saranno aggiunti insieme alle funzionalità che li richiedono. Il frontend mantiene una separazione analoga in `features`; `app` contiene l’avvio e `shared` è riservata agli elementi riutilizzabili.

## Avvio

Occorre Docker Engine con Docker Compose oppure Docker Desktop **in esecuzione**. Il primo avvio richiede Internet per scaricare immagini e dipendenze.

Dalla radice del repository:

```bash
docker compose up --build
```

Aprire [http://localhost:8080](http://localhost:8080). Per avviare in background e attendere gli health check:

```bash
docker compose up --build -d --wait --wait-timeout 180
```

### Gestione

```bash
# Validazione della configurazione
docker compose config --quiet

# Compilazione delle immagini
docker compose build

# Stato dei servizi
docker compose ps

# Log dei servizi
docker compose logs -f

# Arresto e rimozione dei container, conservando il database
docker compose down
```

Il volume `postgres-data` conserva il database dopo `down` e dopo la ricreazione dei container. L’opzione `--volumes` elimina anche i dati: non usarla se devono essere conservati.

## Configurazione

L’avvio funziona con i valori locali predefiniti, senza creare `.env`. Per personalizzarli, copiare `.env.example` in `.env` e modificarlo. `.env` è escluso da Git.

| Variabile Compose | Default | Utilizzo |
|---|---|---|
| `PUBLIC_PORT` | `8080` | Porta pubblica di Nginx |
| `POSTGRES_DB` | `shortener` | Nome del database |
| `POSTGRES_USER` | `shortener` | Utente PostgreSQL |
| `POSTGRES_PASSWORD` | `shortener-local` | Password locale PostgreSQL |

Le credenziali predefinite sono solo per lo sviluppo locale. Compose passa al backend `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_HOST` e `REDIS_PORT`. I nomi `postgres` e `redis` sono indirizzi interni Docker. Il futuro frontend userà percorsi relativi `/api/v1/*`.

Flyway è abilitato, ma non sono presenti migrazioni applicative. Hibernate non genera tabelle; Flyway può creare la propria tabella tecnica di cronologia.

Spring Security conserva la configurazione automatica. Le richieste API con `Accept: application/json` ricevono `401`; Swagger richiede autenticazione. L’utente automatico è `user`, con password temporanea generata nei log del backend a ogni avvio. Questo comportamento non costituisce un sistema di account applicativi.

## Verifiche

Dopo aver avviato i servizi:

```bash
bash scripts/verify.sh
```

Lo script usa Docker per verificare:

- Stato del backend, che dipende anche da PostgreSQL e Redis.
- Pagina HTML e presenza del contenuto React compilato.
- Inoltro di API, redirect, Swagger e OpenAPI al backend, con `401` atteso nello scheletro.
- Blocco dell’accesso esterno ad Actuator.
- Persistenza del cluster PostgreSQL dopo la ricreazione dei container.

La verifica ricrea tutti i container, causando una breve interruzione. Non aggiunge tabelle o dati applicativi.

La build backend esegue Maven `verify`; quella frontend esegue TypeScript e Vite. Quando saranno introdotti test Testcontainers, il container di test dovrà avere accesso al motore Docker.

Per rigenerare il lockfile senza installare npm sull’host:

```bash
docker run --rm --mount "type=bind,source=$PWD/frontend,target=/workspace" -w /workspace node:22.21.1-alpine npm install --package-lock-only --ignore-scripts
```

## Contratti API pianificati

Questi endpoint **non sono ancora implementati**:

| Metodo | Percorso | Funzione futura |
|---|---|---|
| `POST` | `/api/v1/links` | Creare un link breve |
| `GET` | `/api/v1/links` | Elencare i propri link |
| `DELETE` | `/api/v1/links/{shortCode}` | Eliminare un link |
| `GET` | `/api/v1/links/{shortCode}/stats` | Consultare le statistiche |
| `POST` | `/api/v1/auth/register` | Registrarsi |
| `POST` | `/api/v1/auth/login` | Accedere |
| `GET` | `/r/{shortCode}` | Redirect verso l’URL originale |

Il prefisso `/r/` separa i link brevi dalle pagine della SPA. Nginx riserva già questi percorsi: richieste API e redirect non ricevono il fallback HTML del frontend.

## Roadmap

1. **Link di base:** creazione, codici Base62 univoci, persistenza e redirect `302`.
2. **Consolidamento:** migrazioni di dominio, validazione, gestione errori, test e documentazione API.
3. **Prestazioni:** cache Redis con TTL e rate limiting.
4. **Account:** registrazione, login JWT, proprietà dei link e alias personalizzati.
5. **Analytics:** eventi click, scadenze e statistiche.

Hot reload, workflow CI, deployment pubblico, Kafka, Kubernetes e monitoraggio avanzato non fanno parte dello scheletro attuale.
