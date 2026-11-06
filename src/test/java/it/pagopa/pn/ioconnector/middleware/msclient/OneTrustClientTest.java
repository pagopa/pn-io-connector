package it.pagopa.pn.ioconnector.middleware.msclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.pnclients.RestClientResponseErrorHandler;
import it.pagopa.pn.ioconnector.config.IoLegalSecrets;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.middleware.msclient.onetrust.OneTrustClient;
import it.pagopa.pn.ioconnector.middleware.msclient.onetrust.PrivacyNoticeOneTrustResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OneTrustClientTest {

    private static final String BASE_URL = "http://onetrust.test";
    private static final String TOKEN = "secret-token";
    private static final String NOTICE_ID = "notice-guid-1";

    private static final String RESPONSE_BODY = """
            {
              "guid": "notice-guid-1",
              "name": "Termini di servizio",
              "description": "desc",
              "orgGroup": {"id": "org-1", "name": "PagoPA"},
              "owners": [],
              "approvers": [{"id": "user-1", "name": "Mario", "email": "m@example.org"}],
              "effectiveDate": "2026-01-01",
              "expirationDate": null,
              "id": 10,
              "defaultLanguageCode": "it",
              "unknownField": "ignored",
              "versions": [{
                "id": "ver-1",
                "attachmentId": null,
                "descriptionOfChanges": "changes",
                "publishedDate": "2026-02-01T10:00:00",
                "createdDate": "2026-01-15T10:00:00",
                "versionStatus": "ACTIVE",
                "policyContentType": "HTML",
                "sections": [],
                "majorVersion": 4,
                "minorVersion": 0
              }]
            }""";

    private MockRestServiceServer server;
    private OneTrustClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultStatusHandler(HttpStatusCode::isError, new RestClientResponseErrorHandler(new ObjectMapper()));
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OneTrustClient(builder.build(), new IoLegalSecrets("k", "a", "*", TOKEN));
    }

    @Test
    void getPrivacyNoticeVersionByPrivacyNoticeId_callsPublishedVersionUrlWithDateAndBearerToken() {
        String expectedUrl = BASE_URL + "/api/enterprise-policy/v1/privacynotices/" + NOTICE_ID
                + "/published-version?date=" + LocalDate.now().plusDays(1);
        server.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(RESPONSE_BODY, MediaType.APPLICATION_JSON));

        client.getPrivacyNoticeVersionByPrivacyNoticeId(NOTICE_ID);

        server.verify();
    }

    @Test
    void getPrivacyNoticeVersionByPrivacyNoticeId_mapsResponseToResult() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess(RESPONSE_BODY, MediaType.APPLICATION_JSON));

        PrivacyNoticeOneTrustResult result = client.getPrivacyNoticeVersionByPrivacyNoticeId(NOTICE_ID);

        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    void getPrivacyNoticeVersionByPrivacyNoticeId_responseWithoutOrgGroupAndApprovers_mapsVersion() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("{\"versions\":[{\"majorVersion\":6}]}", MediaType.APPLICATION_JSON));

        assertThat(client.getPrivacyNoticeVersionByPrivacyNoticeId(NOTICE_ID).version()).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"guid\":\"g\",\"orgGroup\":{\"id\":\"o\"},\"approvers\":[{\"id\":\"u\"}]}",
            "{\"versions\":[]}",
            "{\"versions\":[null]}",
            "{\"versions\":[{\"id\":\"ver-1\"}]}"
    })
    void getPrivacyNoticeVersionByPrivacyNoticeId_responseWithoutPublishedVersion_throwsMappingError(String body) {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getPrivacyNoticeVersionByPrivacyNoticeId(NOTICE_ID))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR));
    }

    @Test
    void getPrivacyNoticeVersionByPrivacyNoticeId_serverError_throwsHttpResponseException() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getPrivacyNoticeVersionByPrivacyNoticeId(NOTICE_ID))
                .isInstanceOfSatisfying(PnHttpResponseException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(500));
    }
}
