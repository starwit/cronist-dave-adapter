# cronist-dave-adapter

Transfers completed countings from [Cronist](https://github.com/starwit/cronist) to [DAVe](https://github.com/starwit/dave-backend). It is the batch counterpart of [dave-adapter](https://github.com/starwit/dave-adapter), which transfers live data of a fixed mapping every 15 minutes.

```
Cronist REST API ──── completed countings ────┐
                                              ├─► adapter ──► DAVe REST API
Observatory analytics DB ── line crossings ───┘
```

## What it does

Every `adapter.transfer-interval`, for each counting in state `COMPLETED`:

1. Finds the Zählung of the counting by its `projektNummer` `cronist-<counting id>`, in whatever Zählstelle it is.
2. If there is none, finds the Zählstelle at the location of the observation area or creates it, and creates the Zählung there.
3. Sends the line crossings to the detector API, one quarter hour at a time, oldest first.
4. Sets the Zählung to `ACCOMPLISHED`, DAVe then calculates its sums and peak hours. With `adapter.dave.final-status=ACTIVE`, the default, it is then set to `ACTIVE` and so released. With `ACCOMPLISHED` a Fachadmin releases it in DAVe.

The adapter has no database. DAVe's data decides what is left to do, so a transfer that fails at any point is resumed by the next run:

| Zählung of the counting | Next run |
|---|---|
| none | transfers the counting |
| `CREATED` | sends the data again, DAVe skips intervals it already has, then finishes the Zählung |
| `ACCOMPLISHED` | nothing, or releases it if the final status is `ACTIVE` |
| any other status | nothing |

Resuming relies on the duplicate check in `save-latest-detections` of [starwit/dave-backend](https://github.com/starwit/dave-backend). It is not in the [it-at-m](https://github.com/it-at-m/dave-backend) version, which would save the intervals twice.

The Zählungen are read once per run with `GET /zaehlung/byProjektNummerPrefix?prefix=cronist-`, from branch `feature/ab#2325-get-values-from-video-upload` of dave-backend. As they are found independent of their Zählstelle, a moved area or a second Zählstelle close by doesn't create a Zählung twice.

## Project structure

One Maven project, its packages are horizontal layers as in [observatory-config](https://github.com/starwit/observatory-config).

| Package | Content | Naming |
|---|---|---|
| `de.starwit.application` | Spring Boot main class | |
| `de.starwit.application.config` | Security configuration | `*Config` |
| `de.starwit.service` | Transfer logic and calls of the Cronist, DAVe and GeoServer APIs | `*Service` |
| `de.starwit.mapper` | Conversion from the Cronist and Observatory model to the DAVe model | `*Mapper` |
| `de.starwit.dto.cronist` | Data objects read from Cronist | `*Dto` |
| `de.starwit.dto.dave` | Data objects read from and sent to DAVe | `*Dto` |
| `de.starwit.dto.geoserver` | Data objects read from the GeoServer | `*Dto` |
| `de.starwit.dto` | Data objects of the transfer itself, belonging to neither application | `*Dto` |
| `de.starwit.repository` | Read access to the Observatory analytics database | `*Repository`, `*Projection` |
| `de.starwit.config` | Adapter properties and authentication of API calls, used by all layers | |

## Mapping

### Observation area to Zählstelle

A Zählstelle is identified by its location, not by its `nummer`: the `nummer` follows a numbering scheme of DAVe (Stadtbezirk, Stadtbezirksviertel, running number) the adapter can't fill, so it sends none. An area belongs to the Zählstelle within 5 m of its `latitude`, `longitude`. Only when a Zählung is to be created, the adapter reads the locations of all Zählstellen from the search of DAVe, and only creates a Zählstelle if there is none at the location of the area. An existing Zählstelle is never changed, whoever created it.

The comparison is the one DAVe does when saving a new Zählstelle (branch `feature/ab#2325-get-values-from-video-upload` of dave-backend): DAVe updates an existing Zählstelle within 5 m instead of creating one. The adapter does it beforehand, as DAVe overwrites all fields of the existing Zählstelle with the ones sent.

| DAVe | Cronist |
|---|---|
| `nummer` | none |
| `lat`, `lng` | `latitude`, `longitude`, an area without them is not transferred |
| `stadtbezirkNummer` | Stadtteil of `latitude`, `longitude` mapped by `adapter.dave.stadtbezirk-mapping`, see below |
| `kommentar` | area name + ` - Cronist` |
| `customSuchwoerter` | area name |
| `sichtbarDatenportal` | `false` |

Areas in Cronist have no Stadtbezirk. The adapter asks the WFS `adapter.geoserver.url` for the Stadtteil containing the area, by default the [Stadt- und Ortsteile of Wolfsburg](https://gdiservices.stadt.wolfsburg.de/geoserver/opendata_stadtteile/wfs) (layer `opendata_stadtteile:stadtteile`, attribute `name`). The layer has no official numbers, so `adapter.dave.stadtbezirk-mapping` maps the name of the Stadtteil to the number of the Stadtbezirk in DAVe:

```properties
adapter.dave.stadtbezirk-mapping[Stadtmitte]=130
adapter.dave.stadtbezirk-mapping[Alt-Wolfsburg]=101
```

The numbers are the ones of the Stadtbezirke in DAVe, table `city_district`. `adapter.dave.stadtbezirk-nummer`, by default `130` (Stadtmitte of Wolfsburg), is used if the area is in no Stadtteil, its Stadtteil is not mapped (a warning is logged) or `adapter.geoserver.url` is empty. If the GeoServer is not reachable, no Zählstelle is created and the transfer is retried next run, so a Zählstelle doesn't end up in a wrong Stadtbezirk. The Stadtbezirk of an existing Zählstelle is not changed.

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
| `adapter.geoserver.url` | `GEOSERVER_URL` | WFS of the Stadtteile of Wolfsburg, empty to disable |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/analytics` |
| `spring.datasource.username` | `DB_USERNAME` | `analytics` |
| `spring.datasource.password` | `DB_PASSWORD` | `analytics` |
| `adapter.zone` | | `Europe/Berlin`, has to be the time zone DAVe runs in |
| `adapter.dave.final-status` | | `ACTIVE`, the Zählung is released right away. With `ACCOMPLISHED` a Fachadmin releases it in DAVe |

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

The health check is available at <http://localhost:8089/monitoring/health>. Every
`adapter.credential-check-interval` (default 5 minutes) the adapter calls `adapter.cronist.test-url` and
`adapter.dave.test-url` (`CRONIST_TEST_URL`, `DAVE_TEST_URL`) with its credentials. The results are reported as the
components `cronist` and `dave`: if a service is not reachable or the credentials are rejected, the health check is
`DOWN` and a warning with the reason is logged. An empty test URL disables the check of that service.

To build the image:

```bash
docker build -f dockerfile -t cronist-dave-adapter .
```
