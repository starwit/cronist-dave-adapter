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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import de.starwit.config.AdapterProperties.FinalStatus;
import de.starwit.dto.cronist.CountingDto;
import de.starwit.dto.cronist.ImageDto;
import de.starwit.dto.cronist.ObservationAreaDto;
import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.dto.dave.DetectionDto;
import de.starwit.dto.dave.SearchResultDto;
import de.starwit.dto.dave.ZaehlungDto;
import de.starwit.mapper.DetectionMapper;
import de.starwit.mapper.TransferPlanMapper;
import de.starwit.mapper.ZaehlstelleMapper;
import de.starwit.mapper.ZaehlungMapper;
import de.starwit.repository.CrossingCountProjection;
import de.starwit.repository.LineCrossingRepository;

@ExtendWith(OutputCaptureExtension.class)
class TransferServiceTest {

    private static final String ZAEHLUNG_ID = "339f992e-0925-4f6d-9e75-099bc520ad2c";

    private CronistService cronist;
    private DaveService dave;
    private LineCrossingRepository lineCrossings;
    private StadtteilService stadtteile;
    private TransferService service;

    @BeforeEach
    void setUp() {
        cronist = mock(CronistService.class);
        dave = mock(DaveService.class);
        lineCrossings = mock(LineCrossingRepository.class);
        stadtteile = mock(StadtteilService.class);
        when(stadtteile.findStadtteil(48.1, 11.5)).thenReturn(Optional.of("Stadtmitte"));
        service = service(FinalStatus.ACCOMPLISHED);
        when(dave.createZaehlstelle(any())).thenReturn("area-id");
        when(dave.createZaehlung(any(), anyString())).thenReturn(ZAEHLUNG_ID);
        when(lineCrossings.countCrossings(anyList())).thenReturn(List.of(
                new CrossingCountProjection(Instant.parse("2026-10-05T10:00:00Z"), 2, "N", "E", 3)));
    }

    private TransferService service(FinalStatus finalStatus) {
        var properties = TestData.properties(finalStatus, true);
        return new TransferService(cronist, dave, stadtteile, lineCrossings, new TransferPlanMapper(properties),
                new ZaehlstelleMapper(properties), new ZaehlungMapper(properties), new DetectionMapper(properties),
                properties);
    }

    /** A Zaehlstelle at the location of {@link TestData#AREA}, a bit off as DAVe stores it rounded. */
    private void existingZaehlstelle() {
        when(dave.findZaehlstellen()).thenReturn(List.of(
                new SearchResultDto("existing-id", "123", "zaehlstelle", 48.10001, 11.49999)));
    }

    /** The Zaehlung of counting 1, wherever its Zaehlstelle is. */
    private void existingZaehlung(String status) {
        when(dave.findZaehlungenByProjektNummerPrefix("cronist-")).thenReturn(
                List.of(new ZaehlungDto(ZAEHLUNG_ID, "cronist-1", status)));
    }

    @Test
    void createsZaehlstelleAndZaehlungAndSendsIntervalsInOrder() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        ArgumentCaptor<CreateZaehlstelleDto> zaehlstelle = ArgumentCaptor.captor();
        verify(dave).createZaehlstelle(zaehlstelle.capture());
        assertThat(zaehlstelle.getValue().kommentar()).isEqualTo("Marienplatz - Cronist");
        assertThat(zaehlstelle.getValue().stadtbezirkNummer()).isEqualTo(5);
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
    void createsNoZaehlstelleIfStadtteilCannotBeLookedUp() {
        when(stadtteile.findStadtteil(48.1, 11.5)).thenThrow(new IllegalStateException("GeoServer down"));
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave, never()).createZaehlstelle(any());
        verify(dave, never()).createZaehlung(any(), anyString());
    }

    @Test
    void reusesZaehlstelleAtSameLocation() {
        existingZaehlstelle();
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave, never()).createZaehlstelle(any());
        verify(dave).createZaehlung(any(), eq("existing-id"));
    }

    @Test
    void createsZaehlstelleIfExistingOneIsFurtherAway() {
        // about 11 m to the north
        when(dave.findZaehlstellen()).thenReturn(List.of(
                new SearchResultDto("other-id", "123", "zaehlstelle", 48.1001, 11.5)));
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave).createZaehlung(any(), eq("area-id"));
    }

    @Test
    void findsZaehlungAlsoIfAreaMoved() {
        // no Zaehlstelle at the location of the area anymore, the Zaehlung is found anyway
        existingZaehlung("CREATED");
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave, never()).createZaehlstelle(any());
        verify(dave, never()).createZaehlung(any(), anyString());
        verify(dave).updateStatus(ZAEHLUNG_ID, "ACCOMPLISHED");
    }

    @Test
    void readsNoZaehlstellenIfAllZaehlungenExist() {
        existingZaehlung("ACCOMPLISHED");
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(dave, never()).findZaehlstellen();
    }

    @Test
    void createsNoZaehlstelleForAreaWithoutLocation() {
        when(cronist.findCompletedCountings())
                .thenReturn(List.of(countingIn(new ObservationAreaDto(8L, "Nowhere", null, null, null))));

        service.transferCompletedCountings();

        verify(dave, never()).createZaehlstelle(any());
        verify(dave, never()).createZaehlung(any(), anyString());
    }

    private static CountingDto countingIn(ObservationAreaDto area) {
        CountingDto counting = TestData.counting(1);
        return new CountingDto(counting.id(), counting.name(), counting.startDateTime(), counting.endDateTime(),
                counting.state(), area, counting.observatoryJobs(), counting.polygon());
    }

    private static final ObservationAreaDto AREA_WITH_IMAGE = new ObservationAreaDto(7L, "Marienplatz", 48.1, 11.5,
            5L);

    @Test
    void sendsImageOfAreaWithNewZaehlstelle() {
        ImageDto image = new ImageDto(new byte[] { 1, 2 }, MediaType.IMAGE_PNG);
        when(cronist.getImage(5L)).thenReturn(image);
        when(cronist.findCompletedCountings()).thenReturn(List.of(countingIn(AREA_WITH_IMAGE)));

        service.transferCompletedCountings();

        verify(dave).saveZaehlstelleImage("area-id", image);
    }

    @Test
    void sendsNoImageIfAreaHasNone() {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));

        service.transferCompletedCountings();

        verify(cronist, never()).getImage(any());
        verify(dave, never()).saveZaehlstelleImage(anyString(), any());
    }

    @Test
    void sendsNoImageToExistingZaehlstelle() {
        existingZaehlstelle();
        when(cronist.findCompletedCountings()).thenReturn(List.of(countingIn(AREA_WITH_IMAGE)));

        service.transferCompletedCountings();

        verify(dave, never()).saveZaehlstelleImage(anyString(), any());
    }

    @Test
    void transfersCountingEvenIfImageFails(CapturedOutput output) {
        when(cronist.getImage(5L)).thenThrow(new IllegalStateException("image gone"));
        when(cronist.findCompletedCountings()).thenReturn(List.of(countingIn(AREA_WITH_IMAGE)));

        service.transferCompletedCountings();

        verify(dave).updateStatus(ZAEHLUNG_ID, "ACCOMPLISHED");
        assertThat(output).contains("Image of observation area Marienplatz not transferred to Zaehlstelle area-id");
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
    void logsCauseIfCountingFails(CapturedOutput output) {
        when(cronist.findCompletedCountings()).thenReturn(List.of(TestData.counting(1)));
        when(lineCrossings.countCrossings(anyList())).thenThrow(new IllegalStateException("Failed to obtain connection",
                new java.io.IOException("password authentication failed")));

        service.transferCompletedCountings();

        assertThat(output).contains(
                "Failed to obtain connection, caused by java.io.IOException: password authentication failed");
    }

    @Test
    void logsWholeCauseChainIfTransferFails(CapturedOutput output) {
        Throwable refused = new java.net.ConnectException().initCause(new java.nio.channels.ClosedChannelException());
        when(cronist.findCompletedCountings()).thenThrow(new IllegalStateException("I/O error: null", refused));

        service.transferCompletedCountings();

        assertThat(output).contains("I/O error: null, caused by java.net.ConnectException"
                + ", caused by java.nio.channels.ClosedChannelException");
    }

    @Test
    void logsExceptionItselfIfItHasNoCause(CapturedOutput output) {
        when(cronist.findCompletedCountings()).thenThrow(new IllegalStateException("Cronist down"));

        service.transferCompletedCountings();

        assertThat(output).contains("retrying next run: java.lang.IllegalStateException: Cronist down")
                .doesNotContain("caused by");
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

        verify(dave, never()).findZaehlstellen();
    }
}
