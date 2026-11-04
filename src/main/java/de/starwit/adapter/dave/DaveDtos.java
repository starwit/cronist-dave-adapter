package de.starwit.adapter.dave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The parts of the DAVe backend API used by the adapter. */
public final class DaveDtos {

    private DaveDtos() {
    }

    /** To create a Zaehlstelle, {@code BearbeiteZaehlstelleDTO} in DAVe. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NewZaehlstelle(String nummer, int stadtbezirkNummer, double lat, double lng, String kommentar,
            List<String> customSuchwoerter, boolean sichtbarDatenportal) {
    }

    /** To create a Zaehlung, {@code BearbeiteZaehlungDTO} in DAVe. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NewZaehlung(LocalDate datum, String zaehlart, double lat, double lng, String projektNummer,
            String projektName, String kreuzungsname, boolean sonderzaehlung, boolean dauerzaehlung,
            boolean kreisverkehr, List<String> kategorien, int zaehlIntervall, String wetter, String status,
            String quelle, String zaehldauer, List<Knotenarm> knotenarme, List<Fahrbeziehung> fahrbeziehungen) {
    }

    public record Knotenarm(int nummer, String strassenname) {
    }

    public record Fahrbeziehung(boolean isKreuzung, int von, int nach) {
    }

    /** Entry of a search result, {@code ZaehlstelleKarteDTO} in DAVe. */
    public record SearchResult(String id, String fachId, String type) {
    }

    /** {@code SearchAndFilterOptionsDTO} in DAVe. */
    public record SearchOptions(boolean searchInMessstellen, boolean searchInZaehlstellen,
            List<String> messstelleVerkehrsart) {
    }

    /** {@code BearbeiteZaehlstelleDTO} in DAVe, reduced to its Zaehlungen. */
    public record ExistingZaehlstelle(String id, List<ExistingZaehlung> zaehlungen) {
    }

    public record ExistingZaehlung(String id, String projektNummer, String status) {
    }

    /** Counts of one Fahrbeziehung in one interval, {@code DetectionDTO} in DAVe. */
    public record Detection(UUID zaehlungId, Instant startUhrzeit, Instant endeUhrzeit, int von, int nach, int pkw,
            int lkw, int lastzuege, int busse, int kraftraeder, int fahrradfahrer, int fussgaenger) {
    }

    public record UpdateStatus(String zaehlungId, String status) {
    }

    public record BackendId(String id) {
    }
}
