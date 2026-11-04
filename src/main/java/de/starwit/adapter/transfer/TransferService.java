package de.starwit.adapter.transfer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.starwit.adapter.analytics.AnalyticsRepository;
import de.starwit.adapter.config.AdapterProperties;
import de.starwit.adapter.config.AdapterProperties.FinalStatus;
import de.starwit.adapter.cronist.CronistClient;
import de.starwit.adapter.cronist.CronistCounting;
import de.starwit.adapter.cronist.CronistCounting.ObservationArea;
import de.starwit.adapter.cronist.CronistCounting.ObservatoryJob;
import de.starwit.adapter.dave.DaveClient;
import de.starwit.adapter.dave.DaveDtos.Detection;
import de.starwit.adapter.dave.DaveDtos.ExistingZaehlung;
import de.starwit.adapter.transfer.CountingMapper.TransferPlan;

/**
 * Transfers the completed countings of Cronist to DAVe. Has no state of its own: DAVe is the source of truth what is
 * already transferred. The Zaehlung of a counting is found by its projektNummer and stays CREATED until all its data is
 * transferred. So a transfer that fails at any point is resumed by the next run.
 */
@Service
public class TransferService {

    private static final Logger LOG = LoggerFactory.getLogger(TransferService.class);

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_ACCOMPLISHED = FinalStatus.ACCOMPLISHED.name();

    private final CronistClient cronist;
    private final DaveClient dave;
    private final AnalyticsRepository analytics;
    private final CountingMapper mapper;
    private final FinalStatus finalStatus;

    public TransferService(CronistClient cronist, DaveClient dave, AnalyticsRepository analytics,
            CountingMapper mapper, AdapterProperties properties) {
        this.cronist = cronist;
        this.dave = dave;
        this.analytics = analytics;
        this.mapper = mapper;
        this.finalStatus = properties.dave().finalStatus();
    }

    @Scheduled(fixedDelayString = "${adapter.transfer-interval}")
    public void transferCompletedCountings() {
        try {
            List<CronistCounting> countings = cronist.findCompletedCountings();
            if (!countings.isEmpty()) {
                transfer(countings);
            }
        } catch (RuntimeException e) {
            // e.g. Cronist or DAVe not reachable, try again next run
            LOG.warn("Transfer failed, retrying next run: {}", e.toString());
        }
    }

    private void transfer(List<CronistCounting> countings) {
        Map<String, String> zaehlstellenIds = new HashMap<>(dave.findZaehlstellenIdsByNummer());
        Map<Long, List<CronistCounting>> countingsByArea = countings.stream()
                .collect(Collectors.groupingBy(counting -> counting.observationArea().id()));
        countingsByArea.values().forEach(countingsOfArea -> {
            Map<String, ExistingZaehlung> zaehlungen = findZaehlungen(
                    zaehlstellenIds.get(countingsOfArea.getFirst().observationArea().name()));
            for (CronistCounting counting : countingsOfArea) {
                try {
                    transfer(counting, zaehlstellenIds, zaehlungen.get(CountingMapper.projektNummer(counting)));
                } catch (RuntimeException e) {
                    // one counting failing must not block the others
                    LOG.warn("Transfer of counting {} failed, retrying next run: {}", counting.name(), e.toString());
                }
            }
        });
    }

    /** @return the Zaehlungen of the Zaehlstelle by their projektNummer, empty if there is no Zaehlstelle yet */
    private Map<String, ExistingZaehlung> findZaehlungen(String zaehlstelleId) {
        if (zaehlstelleId == null) {
            return Map.of();
        }
        return Objects.requireNonNullElse(dave.getZaehlstelle(zaehlstelleId).zaehlungen(), List.<ExistingZaehlung>of())
                .stream()
                .filter(zaehlung -> zaehlung.projektNummer() != null)
                .collect(Collectors.toMap(ExistingZaehlung::projektNummer, Function.identity(),
                        (first, second) -> first));
    }

    private void transfer(CronistCounting counting, Map<String, String> zaehlstellenIds, ExistingZaehlung existing) {
        String zaehlungId = existing == null ? null : existing.id();
        String status = existing == null ? STATUS_CREATED : existing.status();
        if (STATUS_CREATED.equals(status)) {
            Optional<String> transferred = transferData(counting, zaehlstellenIds, zaehlungId);
            if (transferred.isEmpty()) {
                return;
            }
            zaehlungId = transferred.get();
            status = STATUS_ACCOMPLISHED;
        }
        if (STATUS_ACCOMPLISHED.equals(status) && finalStatus == FinalStatus.ACTIVE) {
            dave.updateStatus(zaehlungId, FinalStatus.ACTIVE.name());
            LOG.info("Counting {}: released in DAVe", counting.name());
        }
    }

    /**
     * @param zaehlungId the Zaehlung of an earlier, unfinished attempt, null to create it
     * @return id of the then ACCOMPLISHED Zaehlung, empty if the counting has no data to transfer
     */
    private Optional<String> transferData(CronistCounting counting, Map<String, String> zaehlstellenIds,
            String zaehlungId) {
        List<String> jobNames = Objects.requireNonNullElse(counting.observatoryJobs(), List.<ObservatoryJob>of())
                .stream().map(ObservatoryJob::name).toList();
        Optional<TransferPlan> maybePlan = mapper.plan(counting,
                jobNames.isEmpty() ? List.of() : analytics.countCrossings(jobNames));
        if (maybePlan.isEmpty()) {
            return Optional.empty();
        }
        TransferPlan plan = maybePlan.get();
        if (mapper.spansSeveralDays(plan)) {
            LOG.warn("Counting {} spans several days, DAVe treats a Zaehlung that is no Dauerzaehlung as a single day",
                    counting.name());
        }
        String id = zaehlungId != null
                ? zaehlungId
                : dave.createZaehlung(mapper.toZaehlung(counting, plan), findOrCreateZaehlstelle(counting, zaehlstellenIds));
        List<List<Detection>> detections = mapper.toDetections(UUID.fromString(id), plan);
        detections.forEach(dave::saveDetections);
        // DAVe calculates the sums and peak hours of the Zaehlung on this status change
        dave.updateStatus(id, STATUS_ACCOMPLISHED);
        LOG.info("Counting {}: transferred {} intervals to Zaehlung {}", counting.name(), detections.size(), id);
        return Optional.of(id);
    }

    private String findOrCreateZaehlstelle(CronistCounting counting, Map<String, String> zaehlstellenIds) {
        ObservationArea area = counting.observationArea();
        return zaehlstellenIds.computeIfAbsent(area.name(), name -> {
            String id = dave.createZaehlstelle(mapper.toZaehlstelle(area));
            LOG.info("Created Zaehlstelle {} for observation area {}", id, name);
            return id;
        });
    }
}
