package de.starwit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import de.starwit.config.AdapterProperties.FinalStatus;
import de.starwit.dto.cronist.CountingDto;
import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.dto.dave.DetectionDto;
import de.starwit.dto.dave.ZaehlstelleDto;
import de.starwit.dto.dave.ZaehlungDto;
import de.starwit.mapper.DetectionMapper;
import de.starwit.mapper.TransferPlanMapper;
import de.starwit.mapper.ZaehlstelleMapper;
import de.starwit.mapper.ZaehlungMapper;
import de.starwit.repository.CrossingCountProjection;
import de.starwit.repository.LineCrossingRepository;

class TransferServiceTest {

    private static final String ZAEHLUNG_ID = "339f992e-0925-4f6d-9e75-099bc520ad2c";

    private CronistService cronist;
    private DaveService dave;
    private LineCrossingRepository lineCrossings;
    private TransferService service;

    @BeforeEach
    void setUp() {
        cronist = mock(CronistService.class);
        dave = mock(DaveService.class);
        lineCrossings = mock(LineCrossingRepository.class);
        service = service(FinalStatus.ACCOMPLISHED);
        when(dave.createZaehlstelle(any())).thenReturn("area-id");
        when(dave.createZaehlung(any(), anyString())).thenReturn(ZAEHLUNG_ID);
        when(lineCrossings.countCrossings(anyList())).thenReturn(List.of(
                new CrossingCountProjection(Instant.parse("2026-10-05T10:00:00Z"), 2, "N", "E", 3)));
    }

    private TransferService service(FinalStatus finalStatus) {
        var properties = TestData.properties(finalStatus, true);
        return new TransferService(cronist, dave, lineCrossings, new TransferPlanMapper(properties),
                new ZaehlstelleMapper(properties), new ZaehlungMapper(properties), new DetectionMapper(properties),
                properties);
    }

    private void existingZaehlung(String status) {
        when(dave.findZaehlstellenIdsByNummer()).thenReturn(Map.of("Marienplatz", "existing-id"));
        when(dave.getZaehlstelle("existing-id")).thenReturn(new ZaehlstelleDto("existing-id",
                List.of(new ZaehlungDto(ZAEHLUNG_ID, "cronist-1", status))));
    }

    @Test
    void createsZaehlstelleAndZaehlungAndSendsIntervalsInOrder() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        ArgumentCaptor<CreateZaehlstelleDto> zaehlstelle = ArgumentCaptor.captor();
        verify(dave).createZaehlstelle(zaehlstelle.capture());
        assertThat(zaehlstelle.getValue().nummer()).isEqualTo("Marienplatz");
        verify(lineCrossings).countCrossings(List.of("job-n", "job-e"));

        ArgumentCaptor<List<DetectionDto>> batches = ArgumentCaptor.captor();
        InOrder inOrder = inOrder(dave);
        inOrder.verify(dave).createZaehlung(any(), eq("area-id"));
        inOrder.verify(dave, times(2)).saveDetections(batches.capture());
        inOrder.verify(dave).updateStatus(ZAEHLUNG_ID, "ACCOMPLISHED");
        assertThat(batches.getAllValues().get(0).getFirst().pkw()).isEqualTo(3);
        assertThat(batches.getAllValues().get(0).getFirst().startUhrzeit())
                .isBefore(batches.getAllValues().get(1).getFirst().startUhrzeit());
    }

    @Test
    void reusesZaehlstelleWithSameName() {
        when(dave.findZaehlstellenIdsByNummer()).thenReturn(Map.of("Marienplatz", "existing-id"));
        when(dave.getZaehlstelle("existing-id")).thenReturn(new ZaehlstelleDto("existing-id", List.of()));
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave, never()).createZaehlstelle(any());
        verify(dave).createZaehlung(any(), eq("existing-id"));
    }

    @Test
    void createsZaehlstelleOnceForSeveralCountingsOfArea() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1), TestData.counting(2)));

        service.transferCompletedCountings();

        verify(dave, times(1)).createZaehlstelle(any());
        verify(dave, times(2)).createZaehlung(any(), eq("area-id"));
    }

    @Test
    void skipsCountingAlreadyTransferred() {
        existingZaehlung("ACCOMPLISHED");
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(lineCrossings, never()).countCrossings(anyList());
        verify(dave, never()).createZaehlung(any(), anyString());
        verify(dave, never()).saveDetections(anyList());
        verify(dave, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void leavesReleasedZaehlungAlone() {
        existingZaehlung("ACTIVE");
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service(FinalStatus.ACTIVE).transferCompletedCountings();

        verify(dave, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void resumesUnfinishedTransferWithExistingZaehlung() {
        existingZaehlung("CREATED");
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave, never()).createZaehlung(any(), anyString());
        verify(dave, times(2)).saveDetections(anyList());
        verify(dave).updateStatus(ZAEHLUNG_ID, "ACCOMPLISHED");
    }

    @Test
    void finalStatusActiveCalculatesAndReleases() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service(FinalStatus.ACTIVE).transferCompletedCountings();

        InOrder inOrder = inOrder(dave);
        inOrder.verify(dave).updateStatus(ZAEHLUNG_ID, "ACCOMPLISHED");
        inOrder.verify(dave).updateStatus(ZAEHLUNG_ID, "ACTIVE");
    }

    @Test
    void finalStatusActiveReleasesZaehlungLeftAccomplished() {
        existingZaehlung("ACCOMPLISHED");
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service(FinalStatus.ACTIVE).transferCompletedCountings();

        verify(dave, never()).saveDetections(anyList());
        verify(dave).updateStatus(ZAEHLUNG_ID, "ACTIVE");
    }

    @Test
    void createsNothingForCountingWithoutData() {
        CountingDto c = TestData.counting(1);
        when(cronist.findCompletedCountings()).thenReturn(List.of(new CountingDto(c.id(), c.name(), null, null,
                c.state(), c.observationArea(), List.of(), c.polygon())));

        service.transferCompletedCountings();

        verify(lineCrossings, never()).countCrossings(anyList());
        verify(dave, never()).createZaehlstelle(any());
        verify(dave, never()).createZaehlung(any(), anyString());
    }

    @Test
    void doesNotFinishZaehlungIfSendingFails() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));
        org.mockito.Mockito.doThrow(new IllegalStateException("DAVe down")).when(dave).saveDetections(anyList());

        service.transferCompletedCountings();

        verify(dave, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void failingCountingDoesNotBlockOthers() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1), TestData.counting(2)));
        when(dave.createZaehlung(any(), anyString()))
                .thenThrow(new IllegalStateException("rejected"))
                .thenReturn(ZAEHLUNG_ID);

        service.transferCompletedCountings();

        verify(dave).updateStatus(ZAEHLUNG_ID, "ACCOMPLISHED");
    }

    @Test
    void survivesCronistBeingDown() {
        when(cronist.findCompletedCountings()).thenThrow(new IllegalStateException("Cronist down"));

        service.transferCompletedCountings();

        verify(dave, never()).findZaehlstellenIdsByNummer();
    }
}
