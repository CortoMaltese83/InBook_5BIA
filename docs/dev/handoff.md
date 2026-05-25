# InBook - Handoff tecnico

Data fotografia: 2026-05-25.

Questo documento usa come fonte di verita solo la codebase attuale e la fotografia prodotta nella sessione corrente. Non incorpora decisioni prese in thread precedenti se non verificabili nei file presenti.

## Stato verificato dal codice

### Struttura repository

- Progetto Maven/Spring Boot con entrypoint in `src/main/java/com/inbook/ServingWebContentApplication.java`.
- Backend Java in `src/main/java/com/inbook`:
  - `controller/`: controller MVC e endpoint JSON.
  - `service/`: logica applicativa.
  - `repository/`: repository Spring Data JPA.
  - `repository/entity/`: entity JPA.
  - `dto/`: DTO e record usati dai controller/service.
- Frontend server-rendered/statico:
  - template Thymeleaf in `src/main/resources/templates`.
  - CSS, JS e immagini in `src/main/resources/static`.
- Configurazione principale in `src/main/resources/application.properties`.
- Database H2 locale su file in `data/appdb.mv.db` e `data/appdb.trace.db`.
- Test unitari in `src/test/java/com/inbook/service`.
- Artefatti build presenti in `target/`, ignorati da Git.
- Stato Git osservato nella fotografia:
  - `data/appdb.mv.db` modificato.
  - `docs/` non tracciata.
  - `config/` ignorata da Git.

### Stack

- Spring Boot `3.5.10`.
- Java target `17`.
- Dipendenze principali:
  - Spring Web.
  - Thymeleaf.
  - Spring Data JPA.
  - Spring Security.
  - Spring Mail.
  - H2 runtime.
  - Spring Boot Test.

### Configurazione runtime

- Datasource configurato su H2 file: `jdbc:h2:file:./data/appdb;AUTO_SERVER=TRUE;MODE=MySQL`.
- Hibernate e' configurato con `spring.jpa.hibernate.ddl-auto=update`.
- SQL logging abilitato (`spring.jpa.show-sql=true`).
- Console H2 abilitata su `/h2-console`.
- Lookup libri remoto abilitato di default.
- Import schedulato MIM disabilitato di default, ma le sorgenti CSV MIM sono configurate in `application.properties`.
- Esiste un file locale ignorato `config/application.properties` con configurazione SMTP reale.

### Modello dati rilevato

- `AppUser`: utenti applicativi, ruoli stringa, stato, verifica email, istituto associato, invito di registrazione.
- `Institution`: istituti con codice univoco e stato.
- `InstitutionDomain`: domini email associati a istituti.
- `TeacherInvitation`: inviti docente con token e scadenza.
- `SchoolClass`: classi scolastiche, stato, docente opzionale, istituto opzionale.
- `Subject`: materie, classe, docente e libro opzionale.
- `Book`: libri manuali/locali, ISBN univoco e dati editoriali.
- `BookLookupCache`: cache catalogo libri indicizzata per ISBN.
- `BookImportRun`, `BookImportRunSource`, `BookImportRunItem`, `BookImportRunErrorGroup`: tracciamento import e fallback.
- `AdminAuditEvent`: registro attivita amministrative.

### Funzionalita implementate

- Login con form custom su `/login`.
- Redirect post-login verso `/admin/classes`.
- Registrazione docente via `/signin` e `/auth/register`.
- Verifica email via `/auth/verify`.
- Accettazione invito via `/invite/accept`.
- Gestione istituti admin:
  - creazione e modifica istituto.
  - gestione domini email.
  - inviti docente.
  - approvazione, sospensione, riattivazione ed eliminazione docenti.
  - registro attivita.
  - export XLSX libri attivi per istituto.
- Gestione classi:
  - pagina `/admin/classes`.
  - dati paginati/filterable da `/classe-data`.
  - creazione classe per admin o docente associato a istituto attivo.
  - modifica/eliminazione solo admin.
- Gestione materie:
  - pagina `/subjects`.
  - dati paginati/filterable da `/materia-data`.
  - creazione/modifica/eliminazione materia con controlli per docente proprietario o admin.
  - associazione libro a materia.
- Gestione/lookup libri:
  - lookup ISBN da `/book/lookup`.
  - cache locale `book_lookup_cache`.
  - fallback remoto Google Books e Open Library.
  - caching dei libri inseriti manualmente quando possibile.
- Import libri:
  - pagina admin `/admin/books/import`.
  - import Open Data MIM in background.
  - batch fallback ISBN sui libri locali.
  - dettaglio run, scarti raggruppati e interruzione manuale.
- Email:
  - invio verifica account e inviti docente via `NotificationService`.
  - se `JavaMailSender` non e' disponibile o fallisce, viene loggato il link.

### Test presenti

- `BookLookupServiceTest`: normalizzazione ISBN, cache, fallback remoto, errori remoti, caching manuale.
- `ClasseServiceTest`: regole base creazione classe e cancellazione classe/libri non referenziati.
- `SubjectServiceTest`: accesso classi per istituto, permessi modifica materia, admin.
- Report `target/surefire-reports` presenti dalla run precedente indicano:
  - `BookLookupServiceTest`: 13 test passati.
  - `SubjectServiceTest`: 4 test passati.
  - `ClasseServiceTest`: 5 test, 2 errori legati a inizializzazione Mockito/Byte Buddy su JDK 23.
  - Report anche per `AieBookLookupServiceTest`, ma il sorgente non e' presente nella codebase attuale.

### TODO rilevati

- Nessun marker `TODO`, `FIXME`, `XXX`, `HACK`, `BUG` trovato nei sorgenti.
- Sono presenti stampe `DEBUG` via `System.out`/`System.err` nei controller `ClasseController`, `SubjectController`, `BookController`.

## Decisioni tecniche attuali

- Architettura Spring MVC server-rendered con Thymeleaf, piu endpoint JSON usati dai JS statici.
- Persistenza via JPA/Hibernate su H2 file locale.
- Schema gestito implicitamente da Hibernate con `ddl-auto=update`; non ci sono migrazioni versionate.
- Security:
  - `/api/**` usa HTTP Basic stateless e richiede ruolo `API`.
  - il resto dell'app usa form login.
  - molte rotte MVC mutating sono escluse da CSRF.
- Ruoli e stati sono stringhe libere, non enum:
  - esempi ruoli: `TYPE_ADMIN`, `TYPE_DOCENTE`.
  - esempi stati classe: `active`, `archived`, `hidden`.
  - esempi stati docente: `EMAIL_PENDING`, `PENDING_APPROVAL`, `ACTIVE`, `SUSPENDED`.
- Un admin di default viene creato all'avvio se manca `admin@gmail.com`.
- Il docente puo creare classi solo se associato a un istituto attivo; l'admin puo gestire tutte le classi.
- Una materia e' modificabile dal docente proprietario o da un admin.
- Le classi e materie sono filtrate per istituto per utenti non admin.
- La relazione `Subject -> Book` usa `book_id` referenziato a `Book.isbn`, mentre `Book` ha anche un `id` generato.
- L'import MIM usa un executor single-thread con coda zero, cosi viene impedito piu di un import asincrono contemporaneo.
- Il lookup ISBN privilegia cache locale, poi provider remoti in ordine Google Books e Open Library.
- L'export XLSX e' implementato manualmente con ZIP/XML, senza libreria esterna per Excel.

## Problemi aperti

### Sicurezza

- Credenziali SMTP reali sono presenti nel file locale ignorato `config/application.properties`.
- Admin seed hardcoded con password nota: `admin@gmail.com` / `admin123`.
- Console H2 abilitata e permessa.
- CSRF disabilitato su molte rotte che modificano stato applicativo.
- I pulsanti social Google/Apple in login/registrazione sono UI statiche, senza integrazione OAuth.

### Persistenza e dati

- Database H2 locale e' parte del repository ed e' modificato nel working tree.
- Non esistono migrazioni DB versionate.
- `ddl-auto=update` rende lo schema dipendente dall'avvio applicativo.
- Relazione `Subject.book` basata su ISBN modificabile: rischio inconsistenza se un libro cambia ISBN.
- `SchoolClass.institution` e `SchoolClass.docente` sono nullable per migrazione; il codice contiene fallback per classi non backfillate.

### Qualita codice

- Controller con `System.out`/`System.err` invece di logging.
- Uso diffuso di reflection per getter/fallback in `BookController`, `SubjectController`, `ClasseService`, segnale di contratto dati non stabilizzato.
- `BookController` sembra in parte legacy:
  - ritorna template `BookManager`, non presente tra i template.
  - usa DTO `com.inbook.dto.Book` senza campo `id`, ma l'edit richiede `getId()` via reflection.
- `DocenteRepository` estende `JpaRepository<SchoolClass, Long>` nonostante il nome suggerisca gestione docenti.
- `README.md` contiene solo il titolo e non documenta setup, run, credenziali o flussi.

### Test e delivery

- Copertura test limitata ai service principali.
- Mancano test controller, security, repository query, template, import MIM, export XLSX, registrazione/email.
- I report esistenti indicano errori Mockito su JDK 23 per parte dei test.
- Non e' presente una workflow CI di build/test; esiste solo `.github/workflows/discord.yml` per notifica Discord.

### Operativita

- L'import MIM dipende da URL esterni configurati direttamente in `application.properties`.
- Le date sono salvate come `Long` epoch e visualizzate spesso grezze nei template admin/import.
- Non esiste documentazione di configurazione locale o produzione.

## Prossimi passi

### Priorita alta

1. Mettere in sicurezza la configurazione:
   - rimuovere o ruotare le credenziali SMTP locali esposte nel workspace.
   - eliminare o rendere configurabile l'admin seed hardcoded.
   - disabilitare H2 console fuori da profili locali.
   - riabilitare CSRF sulle POST MVC o introdurre token corretti in form/JS.
2. Stabilizzare persistenza:
   - introdurre migrazioni versionate.
   - decidere se il DB H2 deve restare nel repository; in caso contrario rimuoverlo dal versionamento.
   - sostituire la FK `Subject.book -> Book.isbn` con una relazione su id oppure bloccare formalmente la modifica ISBN.
3. Pulire flussi legacy libri:
   - decidere se `BookController` e `/book/view` servono ancora.
   - se servono, aggiungere template mancante e DTO con id.
   - se non servono, rimuovere o disabilitare le rotte non usate.

### Priorita media

4. Rendere espliciti i contratti applicativi:
   - sostituire stringhe stato/ruolo duplicate con costanti condivise o enum.
   - rimuovere reflection dove i getter sono ormai noti.
   - allineare nomi repository/service al dominio reale.
5. Migliorare osservabilita:
   - sostituire `System.out`/`System.err` con logger.
   - standardizzare messaggi errore e redirect flash.
6. Estendere test:
   - sistemare Mockito/Byte Buddy o fissare JDK/test config.
   - aggiungere test per security e permessi controller.
   - aggiungere test repository per query filtrate per istituto.
   - aggiungere test import MIM con CSV fixture locale.
   - aggiungere test export XLSX almeno su contenuto ZIP/XML.

### Priorita bassa

7. Documentare uso locale:
   - prerequisiti Java/Maven.
   - comando di avvio.
   - profili/config locali.
   - gestione SMTP.
   - credenziali/dev seed sicure.
8. Migliorare UI/operativita:
   - formattare epoch millis in date leggibili.
   - chiarire pulsanti social se non implementati.
   - verificare accessibilita e stati vuoti/errori nelle pagine admin.
9. Aggiungere CI:
   - build Maven.
   - test.
   - eventuale controllo che file segreti e DB locali non vengano committati.
