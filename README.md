# cronist-dave-adapter

Transfers completed countings from [Cronist](https://github.com/starwit/cronist) to [DAVe](https://github.com/starwit/dave-backend). It is the batch counterpart of [dave-adapter](https://github.com/starwit/dave-adapter), which transfers live data of a fixed mapping every 15 minutes.

```
Cronist REST API ──── completed countings ────┐
                                              ├─► adapter ──► DAVe REST API
Observatory analytics DB ── line crossings ───┘
```

## What it does

Every `adapter.transfer-interval`, for each counting in state `COMPLETED`:

1. Finds or creates the Zählstelle of its observation area.
2. Finds or creates the Zählung of the counting.
3. Sends the line crossings to the detector API, one quarter hour at a time, oldest first.
4. Sets the Zählung to `ACCOMPLISHED`, DAVe then calculates its sums and peak hours. With `adapter.dave.final-status=ACTIVE` it is also released.

The adapter has no database. DAVe's data decides what is left to do, so a transfer that fails at any point is resumed by the next run:

| Zählung of the counting | Next run |
|---|---|
| none | transfers the counting |
| `CREATED` | sends the data again, DAVe skips intervals it already has, then finishes the Zählung |
| `ACCOMPLISHED` | nothing, or releases it if the final status is `ACTIVE` |
| any other status | nothing |

Resuming relies on the duplicate check in `save-latest-detections` of [starwit/dave-backend](https://github.com/starwit/dave-backend). It is not in the [it-at-m](https://github.com/it-at-m/dave-backend) version, which would save the intervals twice.

## Mapping

### Observation area to Zählstelle

A Zählstelle in DAVe has no name. Its `nummer` is the unique key shown to users, so it is used as the name.

| DAVe | Cronist |
|---|---|
| `nummer` | area name, compared exactly with existing Zählstellen |
| `lat`, `lng` | `latitude`, `longitude` |
| `stadtbezirkNummer` | `adapter.dave.stadtbezirk-nummer`, areas have none |
| `customSuchwoerter` | area name |
| `sichtbarDatenportal` | `false` |

### Counting to Zählung

| DAVe | Cronist |
|---|---|
| `projektNummer` | `cronist-<counting id>`, identifies the Zählung of a counting |
| `projektName` | counting name |
| `kreuzungsname` | area name |
| `lat`, `lng` | of the area |
| `datum` | local date of the first interval, in `adapter.zone` |
| `knotenarme` | one per direction of the counting lines (see below), line name as `strassenname` |
| `fahrbeziehungen` | all movements between the Knotenarme |
| `zaehldauer` | `DAUER_24_STUNDEN` for exactly one whole local day, otherwise `SONSTIGE` |
| `kategorien` | `adapter.dave.kategorien` |
| `zaehlart`, `quelle`, `wetter`, `zaehlIntervall` | `N`, `VIDEO`, `NO_INFORMATION`, `15` |

Direction of a counting line to Knotenarm, set by `adapter.intersection-mapping`:

| N | E | S | W | NE | SE | SW | NW |
|---|---|---|---|---|---|---|---|
| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |

Lines without a direction are ignored. A counting needs at least two directions to be transferred.

### Line crossings to detections

Crossings are found by the Observatory job names of the counting, `metadata.name` in the analytics database. The observation area is not enough, as an area has several countings.

The counting logic is the one of dave-adapter: an object is counted when it leaves (`out`) through another line than the one it first entered through (`in`). It is counted in the quarter hour it leaves in. Each quarter hour contains all Fahrbeziehungen, those without crossings with zeros.

| Object class | Detection field |
|---|---|
| 0 person | `fussgaenger`, minus bicycles if `adapter.correct-bicycle=true` |
| 1 bicycle | `fahrradfahrer` |
| 2 car | `pkw` |
| 3 motorcycle | `kraftraeder` |
| 5 bus | `busse` |
| 7 truck | `lkw` |

## Limitations

- **Countings over several days:** DAVe treats a Zählung that is no Dauerzählung as a single day. The data is transferred and a warning logged, but DAVe may not display it correctly.
- **Vehicle classes:** Cronist only counts cars for now, so `adapter.dave.kategorien` defaults to `PKW`. Extend it once Cronist counts more classes.
- **Corrections:** a counting that is reset and processed again in Cronist is not transferred again while its Zählung exists. Delete the Zählung in DAVe to transfer it again.

## Configuration

See [application.properties](src/main/resources/application.properties). The main settings:

| Property | Environment variable | Default |
|---|---|---|
| `adapter.cronist.url` | `CRONIST_URL` | `http://localhost:8081/cronist` |
| `adapter.dave.url` | `DAVE_URL` | `http://localhost:8080` |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/analytics` |
| `spring.datasource.username` | `DB_USERNAME` | `analytics` |
| `spring.datasource.password` | `DB_PASSWORD` | `analytics` |
| `adapter.zone` | | `Europe/Berlin`, has to be the time zone DAVe runs in |
| `adapter.dave.final-status` | | `ACCOMPLISHED`, a Fachadmin releases the Zählung in DAVe |

### Authentication

Without further configuration, requests are sent without a token. To authenticate with client credentials, configure the Spring Security OAuth2 client registrations and name them in `adapter.cronist.client-registration-id` and `adapter.dave.client-registration-id`. There is a commented example in [application.properties](src/main/resources/application.properties).

The clients need these roles:

- **DAVe:** `FACHADMIN`
- **Cronist:** `reader`, `user` or `admin`

## Build and run

Requires Java 25 and Maven 3. The tests of the analytics query need Docker and are skipped without it.

```bash
mvn clean install
java -jar target/cronist-dave-adapter-0.0.1-SNAPSHOT.jar
```

The health check is available at <http://localhost:8089/monitoring/health>.

To build the image:

```bash
docker build -f dockerfile -t cronist-dave-adapter .
```
