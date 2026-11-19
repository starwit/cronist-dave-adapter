# Cronist-DAVe-Adapter

The adapter transfers completed countings from Cronist, along with their line crossings from Observatory, to DAVe. It has no database of its own: on every run it reads from DAVe what has already been transferred.

## How the data flows

```mermaid
sequenceDiagram
    autonumber
    participant C as Cronist
    participant O as Observatory database
    participant A as cronist-dave-adapter
    participant G as GeoServer Wolfsburg
    participant D as DAVe backend

    Note over A: every 5 minutes
    A->>C: CronistService: GET /api/counting, COMPLETED only
    A->>D: DaveService: GET /zaehlung/byProjektNummerPrefix?prefix=cronist-
    loop each counting still open
        A->>O: LineCrossingRepository: linecrossing, metadata by job name
        Note over A: TransferPlanMapper: Knotenarme, intervals, counts per 15 min
        opt no Zählung in DAVe yet
            A->>D: POST /search: Zählstellen with location, once per run
            opt no Zählstelle within 5 m
                A->>G: StadtteilService: WFS GetFeature, Stadtteil at location
                A->>D: ZaehlstelleMapper: POST /zaehlstelle/save
            end
            A->>D: ZaehlungMapper: POST /zaehlung/save with status CREATED
        end
        loop each quarter hour, oldest first
            A->>D: DetectionMapper: POST /detector/save-latest-detections
        end
        A->>D: POST /zaehlung/updateStatus: ACCOMPLISHED, maybe ACTIVE
    end
```

The numbers show the order. The adapter finds the Zählungen with one call per run by their projektNummer, whatever Zählstelle they are in. It only asks for Zählstellen and the GeoServer when a Zählung is created. Quarter hours are sent one at a time, oldest first, because only then does DAVe fill missing Fahrbeziehungen with zeros and skip intervals it already has.

## Where a counting enters the process

The status of the Zählung in DAVe is found by its `projektNummer` `cronist-<id>`, whatever its Zählstelle.

```mermaid
flowchart TD
    other(["any other status"]) --> nothing["<b>Do nothing</b><br/>e.g. already released"]
    none(["no Zählung"]) --> create["<b>Create</b><br/>Zählstelle at location, Zählung"]
    create --> send["<b>Send data</b><br/>existing intervals are kept"]
    created(["CREATED"]) --> send
    send --> finish["<b>Finish</b><br/>DAVe calculates, sends email"]
    finish --> release["<b>Release</b><br/>only if final-status=ACTIVE"]
    accomplished(["ACCOMPLISHED"]) --> release

    send -.-> failed["On failure the Zählung stays CREATED"]
    finish -.-> failed
    failed -. "next run" .-> created
```

The status in DAVe decides where the transfer continues, so a run can stop at any point without the adapter having to remember anything. Skipping existing intervals only exists in the starwit/dave-backend version.

## What becomes what

| Source | In DAVe | Rule |
|---|---|---|
| Observation area | Zählstelle | Identified by location: a Zählstelle within 5 m is reused and never changed, otherwise one is created. DAVe assigns the `nummer`. The name goes into `kommentar` as `<name> - Cronist` and into the search words. |
| Location of the area | Stadtbezirk | The GeoServer of the city of Wolfsburg returns the Stadtteil, `adapter.dave.stadtbezirk-mapping` maps it to the number in DAVe. Without a match `adapter.dave.stadtbezirk-nummer` applies, 130 (Stadtmitte) by default. |
| Counting | Zählung | `projektNummer = cronist-<id>` links it to the counting, even if the area was moved. |
| Counting line with direction | Knotenarm | N=1, E=2, S=3, W=4, NE=5, SE=6, SW=7, NW=8. At least two directions are needed. |
| Line crossings of an object | Detection | An object counts if it enters through one line and leaves through another. It counts in the quarter hour it leaves in. |
