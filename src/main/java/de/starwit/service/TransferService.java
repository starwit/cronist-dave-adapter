package de.starwit.service;

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

import de.starwit.config.AdapterProperties;
import de.starwit.config.AdapterProperties.FinalStatus;
import de.starwit.dto.cronist.CountingDto;
import de.starwit.dto.dave.DetectionDto;
import de.starwit.dto.cronist.ObservationAreaDto;
import de.starwit.dto.cronist.ObservatoryJobDto;
import de.starwit.dto.TransferPlanDto;
import de.starwit.dto.dave.ZaehlungDto;
import de.starwit.mapper.DetectionMapper;
import de.starwit.mapper.TransferPlanMapper;
import de.starwit.mapper.ZaehlstelleMapper;
import de.starwit.mapper.ZaehlungMapper;
import de.starwit.repository.LineCrossingRepository;

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

    private final CronistService cronist;
    private final DaveService dave;
    private final LineCrossingRepository lineCrossings;
    private final TransferPlanMapper transferPlanMapper;
    private final ZaehlstelleMapper zaehlstelleMapper;
    private final ZaehlungMapper zaehlungMapper;
    private final DetectionMapper detectionMapper;
    private final AdapterProperties properties;

    public TransferService(CronistService cronist, DaveService dave, LineCrossingRepository lineCrossings,
            TransferPlanMapper transferPlanMapper, ZaehlstelleMapper zaehlstelleMapper, ZaehlungMapper zaehlungMapper,
            DetectionMapper detectionMapper, AdapterProperties properties) {
        this.cronist = cronist;
        this.dave = dave;
        this.lineCrossings = lineCrossings;
        this.transferPlanMapper = transferPlanMapper;
        this.zaehlstelleMapper = zaehlstelleMapper;
        this.zaehlungMapper = zaehlungMapper;
        this.detectionMapper = detectionMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${adapter.transfer-interval}")
    public void transferCompletedCountings() {
        try {
            List<CountingDto> countings = cronist.findCompletedCountings();
            if (!countings.isEmpty()) {
                transfer(countings);
            }
        } catch (RuntimeException e) {
            // e.g. Cronist or DAVe not reachable, try again next run
            LOG.warn("Transfer failed, retrying next run: {}", e.toString());
        }
    }

    private void transfer(List<CountingDto> countings) {
        Map<String, String> zaehlstellenIds = new HashMap<>(dave.findZaehlstellenIdsByNummer());
        Map<Long, List<CountingDto>> countingsByArea = countings.stream()
                .collect(Collectors.groupingBy(counting -> counting.observationArea().id()));
        countingsByArea.values().forEach(countingsOfArea -> {
            Map<String, ZaehlungDto> zaehlungen = findZaehlungen(
                    zaehlstellenIds.get(countingsOfArea.getFirst().observationArea().name()));
            for (CountingDto counting : countingsOfArea) {
                try {
                    transfer(counting, zaehlstellenIds, zaehlungen.get(ZaehlungMapper.toProjektNummer(counting)));
                } catch (RuntimeException e) {
                    // one counting failing must not block the others
                    LOG.warn("Transfer of counting {} failed, retrying next run: {}", counting.name(), e.toString());
                }
            }
        });
    }

    /** @return the Zaehlungen of the Zaehlstelle by their projektNummer, empty if there is no Zaehlstelle yet */
    private Map<String, ZaehlungDto> findZaehlungen(String zaehlstelleId) {
        if (zaehlstelleId == null) {
            return Map.of();
        }
        return Objects.requireNonNullElse(dave.getZaehlstelle(zaehlstelleId).zaehlungen(), List.<ZaehlungDto>of())
                .stream()
                .filter(zaehlung -> zaehlung.projektNummer() != null)
                .collect(Collectors.toMap(ZaehlungDto::projektNummer, Function.identity(),
                        (first, second) -> first));
    }

    private void transfer(CountingDto counting, Map<String, String> zaehlstellenIds, ZaehlungDto existing) {
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
        if (STATUS_ACCOMPLISHED.equals(status) && properties.dave().finalStatus() == FinalStatus.ACTIVE) {
            dave.updateStatus(zaehlungId, FinalStatus.ACTIVE.name());
            LOG.info("Counting {}: released in DAVe", counting.name());
        }
    }

    /**
     * @param zaehlungId the Zaehlung of an earlier, unfinished attempt, null to create it
     * @return id of the then ACCOMPLISHED Zaehlung, empty if the counting has no data to transfer
     */
    private Optional<String> transferData(CountingDto counting, Map<String, String> zaehlstellenIds,
            String zaehlungId) {
        List<String> jobNames = Objects.requireNonNullElse(counting.observatoryJobs(), List.<ObservatoryJobDto>of())
                .stream().map(ObservatoryJobDto::name).toList();
        Optional<TransferPlanDto> maybePlan = transferPlanMapper.convertToDto(counting,
                jobNames.isEmpty() ? List.of() : lineCrossings.countCrossings(jobNames));
        if (maybePlan.isEmpty()) {
            return Optional.empty();
        }
        TransferPlanDto plan = maybePlan.get();
        if (plan.spansSeveralDays(properties.zone())) {
            LOG.warn("Counting {} spans several days, DAVe treats a Zaehlung that is no Dauerzaehlung as a single day",
                    counting.name());
        }
        String id = zaehlungId != null
                ? zaehlungId
                : dave.createZaehlung(zaehlungMapper.convertToDto(counting, plan),
                        findOrCreateZaehlstelle(counting, zaehlstellenIds));
        List<List<DetectionDto>> detections = detectionMapper.convertToDtos(UUID.fromString(id), plan);
        detections.forEach(dave::saveDetections);
        // DAVe calculates the sums and peak hours of the Zaehlung on this status change
        dave.updateStatus(id, STATUS_ACCOMPLISHED);
        LOG.info("Counting {}: transferred {} intervals to Zaehlung {}", counting.name(), detections.size(), id);
        return Optional.of(id);
    }

    private String findOrCreateZaehlstelle(CountingDto counting, Map<String, String> zaehlstellenIds) {
        ObservationAreaDto area = counting.observationArea();
        return zaehlstellenIds.computeIfAbsent(area.name(), name -> {
            String id = dave.createZaehlstelle(zaehlstelleMapper.convertToDto(area));
            LOG.info("Created Zaehlstelle {} for observation area {}", id, name);
            return id;
        });
    }
}
