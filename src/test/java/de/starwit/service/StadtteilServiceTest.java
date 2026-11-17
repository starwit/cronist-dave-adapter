package de.starwit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Requests and responses are the ones of the GeoServer of the city of Wolfsburg. */
class StadtteilServiceTest {

    private static final String REQUEST = "http://geoserver/wfs?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature"
            + "&typeNames=opendata_stadtteile%3Astadtteile&outputFormat=application/json&propertyName=name"
            + "&CQL_FILTER=INTERSECTS%28geo%2CSRID%3D4326%3BPOINT%2810.7865%2052.4227%29%29";

    private MockRestServiceServer server;
    private StadtteilService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://geoserver/wfs");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new StadtteilService(builder.build(), "opendata_stadtteile:stadtteile");
    }

    @Test
    void findsStadtteilContainingLocation() {
        server.expect(requestTo(REQUEST))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"type": "FeatureCollection", "features": [
                          {"type": "Feature", "id": "stadtteile.282", "geometry": null,
                           "properties": {"name": "Stadtmitte"}}],
                         "totalFeatures": 1, "numberMatched": 1, "numberReturned": 1}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.findStadtteil(52.4227, 10.7865)).contains("Stadtmitte");
    }

    @Test
    void findsNoStadtteilOutsideOfLayer() {
        server.expect(requestTo(REQUEST)).andRespond(withSuccess("""
                {"type": "FeatureCollection", "features": [], "numberMatched": 0, "numberReturned": 0}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.findStadtteil(52.4227, 10.7865)).isEmpty();
    }

    @Test
    void asksNothingIfLocationIsUnknown() {
        assertThat(service.findStadtteil(null, 10.7865)).isEmpty();
        server.verify();
    }

    @Test
    void asksNothingIfNoGeoserverIsConfigured() {
        assertThat(new StadtteilService((RestClient) null, null).findStadtteil(52.4227, 10.7865)).isEmpty();
    }
}
