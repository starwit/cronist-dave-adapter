# Cronist-DAVe-Adapter

Der Adapter überträgt abgeschlossene Zählungen aus Cronist samt den Linecrossings aus Observatory nach DAVe. Er hat keine eigene Datenbank: Was schon übertragen ist, liest er bei jedem Lauf aus DAVe.

## Wie die Daten fließen

```mermaid
sequenceDiagram
    autonumber
    participant C as Cronist
    participant O as Observatory-Datenbank
    participant A as cronist-dave-adapter
    participant D as DAVe-Backend

    Note over A: alle 5 Minuten
    A->>C: CronistService: GET /api/counting, nur COMPLETED
    A->>D: DaveService: POST /search, GET /zaehlstelle/editZaehlstelle
    loop je Zählung, die noch offen ist
        A->>O: LineCrossingRepository: linecrossing, metadata per Jobname
        Note over A: TransferPlanMapper: Knotenarme, Intervalle, Anzahl je 15 min
        opt Name der Zählstelle fehlt
            A->>D: ZaehlstelleMapper: POST /zaehlstelle/save
        end
        A->>D: ZaehlungMapper: POST /zaehlung/save mit Status CREATED
        loop je Viertelstunde, die älteste zuerst
            A->>D: DetectionMapper: POST /detector/save-latest-detections
        end
        A->>D: POST /zaehlung/updateStatus: ACCOMPLISHED
    end
```

Die Nummern zeigen die Reihenfolge. Die Viertelstunden gehen einzeln und mit der ältesten zuerst an DAVe, weil DAVe nur so fehlende Fahrbeziehungen mit Nullen auffüllt und schon vorhandene Intervalle überspringt.

## Wo eine Zählung einsteigt

Der Status der Zählung in DAVe wird über die `projektNummer` `cronist-<id>` gefunden.

```mermaid
flowchart TD
    other(["anderer Status"]) --> nothing["<b>Nichts tun</b><br/>z. B. schon freigegeben"]
    none(["keine Zählung"]) --> create["<b>Anlegen</b><br/>Zählstelle bei Bedarf, Zählung"]
    create --> send["<b>Daten senden</b><br/>vorhandene Intervalle bleiben"]
    created(["CREATED"]) --> send
    send --> finish["<b>Abschließen</b><br/>DAVe rechnet und mailt"]
    finish --> release["<b>Freigeben</b><br/>nur bei final-status=ACTIVE"]
    accomplished(["ACCOMPLISHED"]) --> release

    send -.-> failed["Fehler: Zählung bleibt CREATED"]
    finish -.-> failed
    failed -. "nächster Lauf" .-> created
```

Der Status in DAVe entscheidet, wo der Transfer weitergeht. Deshalb kann ein Lauf an jeder Stelle abbrechen, ohne dass der Adapter sich etwas merken muss. Das Überspringen vorhandener Intervalle gibt es nur im Backend von starwit/dave-backend.

## Was woraus wird

| Quelle | In DAVe | Regel |
|---|---|---|
| Observation Area | Zählstelle | Der Name steht in `nummer`, weil DAVe kein Namensfeld hat. Eine gleichnamige Zählstelle wird wiederverwendet. |
| Counting | Zählung | `projektNummer = cronist-<id>` ordnet sie dem Counting zu. |
| Zähllinie mit Richtung | Knotenarm | N=1, E=2, S=3, W=4, NE=5, SE=6, SW=7, NW=8. Es braucht mindestens zwei Richtungen. |
| Linecrossings eines Objekts | Detection | Gezählt wird, wer über eine Linie herein- und über eine andere hinausfährt, in der Viertelstunde des Hinausfahrens. |
