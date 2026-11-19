# Cronist-DAVe-Adapter

Der Adapter überträgt abgeschlossene Zählungen aus Cronist samt den Linecrossings aus Observatory nach DAVe. Er hat keine eigene Datenbank: Was schon übertragen ist, liest er bei jedem Lauf aus DAVe.

## Wie die Daten fließen

```mermaid
sequenceDiagram
    autonumber
    participant C as Cronist
    participant O as Observatory-Datenbank
    participant A as cronist-dave-adapter
    participant G as GeoServer Wolfsburg
    participant D as DAVe-Backend

    Note over A: alle 5 Minuten
    A->>C: CronistService: GET /api/counting, nur COMPLETED
    A->>D: DaveService: GET /zaehlung/byProjektNummerPrefix?prefix=cronist-
    loop je Zählung, die noch offen ist
        A->>O: LineCrossingRepository: linecrossing, metadata per Jobname
        Note over A: TransferPlanMapper: Knotenarme, Intervalle, Anzahl je 15 min
        opt noch keine Zählung in DAVe
            A->>D: POST /search: Zählstellen mit Standort, einmal je Lauf
            opt keine Zählstelle im Umkreis von 5 m
                A->>G: StadtteilService: WFS GetFeature, Stadtteil am Standort
                A->>D: ZaehlstelleMapper: POST /zaehlstelle/save
            end
            A->>D: ZaehlungMapper: POST /zaehlung/save mit Status CREATED
        end
        loop je Viertelstunde, die älteste zuerst
            A->>D: DetectionMapper: POST /detector/save-latest-detections
        end
        A->>D: POST /zaehlung/updateStatus: ACCOMPLISHED, ggf. ACTIVE
    end
```

Die Nummern zeigen die Reihenfolge. Die Zählungen findet der Adapter mit einem Aufruf je Lauf über ihre Projektnummer, egal an welcher Zählstelle sie hängen. Zählstellen und GeoServer fragt er nur ab, wenn eine Zählung neu angelegt wird. Die Viertelstunden gehen einzeln und mit der ältesten zuerst an DAVe, weil DAVe nur so fehlende Fahrbeziehungen mit Nullen auffüllt und schon vorhandene Intervalle überspringt.

## Wo eine Zählung einsteigt

Der Status der Zählung in DAVe wird über die `projektNummer` `cronist-<id>` gefunden, unabhängig von der Zählstelle.

```mermaid
flowchart TD
    other(["anderer Status"]) --> nothing["<b>Nichts tun</b><br/>z. B. schon freigegeben"]
    none(["keine Zählung"]) --> create["<b>Anlegen</b><br/>Zählstelle am Standort, Zählung"]
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
| Observation Area | Zählstelle | Erkannt am Standort: Eine Zählstelle im Umkreis von 5 m wird wiederverwendet und nie geändert, sonst wird eine angelegt. Die `nummer` vergibt DAVe. Der Name steht im `kommentar` als `<Name> - Cronist` und als Suchwort. |
| Standort der Area | Stadtbezirk | Der GeoServer der Stadt Wolfsburg liefert den Stadtteil, `adapter.dave.stadtbezirk-mapping` ordnet ihm die Nummer in DAVe zu. Ohne Treffer gilt `adapter.dave.stadtbezirk-nummer`, standardmäßig 130 (Stadtmitte). |
| Counting | Zählung | `projektNummer = cronist-<id>` ordnet sie dem Counting zu, auch wenn die Area verschoben wurde. |
| Zähllinie mit Richtung | Knotenarm | N=1, E=2, S=3, W=4, NE=5, SE=6, SW=7, NW=8. Es braucht mindestens zwei Richtungen. |
| Linecrossings eines Objekts | Detection | Gezählt wird, wer über eine Linie herein- und über eine andere hinausfährt, in der Viertelstunde des Hinausfahrens. |
