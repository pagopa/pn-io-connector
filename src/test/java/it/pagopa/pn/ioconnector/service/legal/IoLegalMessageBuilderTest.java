package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.config.IoLegalMessageTemplates;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ThirdPartyData;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class IoLegalMessageBuilderTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";
    private static final String IUN = "IUN-001";
    private static final String SENDER_DENOMINATION = "Comune di Milano";
    private static final String CFG_ID = "01HMVMHCZZ8D0VTFWMRHBM5D6F";
    private static final String URL_CITTADINI = "https://cittadini.test/cittadini";
    private static final String URL_TOS = "https://send.test/tos.html";
    private static final String URL_PRIVACY = "https://send.test/privacy.html";

    private final IoLegalMessageTemplates templates = new IoLegalMessageTemplates();
    private IoLegalMessageBuilder builder;

    @BeforeEach
    void setUp() {
        PnIoConnectorConfig config = new PnIoConnectorConfig();
        config.setIoRemoteContentCfgId(CFG_ID);
        config.setPiattaformanotificheurlCittadini(URL_CITTADINI);
        config.setPiattaformanotificheurlTos(URL_TOS);
        config.setPiattaformanotificheurlPrivacy(URL_PRIVACY);
        builder = new IoLegalMessageBuilder(config, templates);
    }

    private SendMessageRequest buildRequest() {
        return new SendMessageRequest(IUN, TAX_ID, "internal-id-001", 0, SENDER_DENOMINATION)
                .subject("Comune di Milano - avviso di pagamento");
    }

    @Nested
    class CourtesyMessage {

        @Test
        void buildCourtesyMessage_italian_buildsAdvancedMessage() {
            NewMessage message = builder.buildCourtesyMessage(buildRequest(), true);

            assertThat(message.getFeatureLevelType()).isEqualTo("ADVANCED");
            assertThat(message.getFiscalCode()).isEqualTo(TAX_ID);
            assertThat(message.getContent().getSubject()).isEqualTo("Comunicazione a valore legale da " + SENDER_DENOMINATION);
            assertThat(message.getContent().getMarkdown()).isEqualTo(templates.getMarkdownCourtesyIt());
            assertThat(message.getContent().getDueDate()).isNull();
        }

        @Test
        void buildCourtesyMessage_notItalian_usesEnglishMarkdown() {
            NewMessage message = builder.buildCourtesyMessage(buildRequest(), false);

            assertThat(message.getContent().getMarkdown()).isEqualTo(templates.getMarkdownCourtesyEn());
        }

        @Test
        void buildCourtesyMessage_longSenderDenomination_truncatesSubjectTo120() {
            SendMessageRequest request = buildRequest().senderDenomination("X".repeat(200));

            NewMessage message = builder.buildCourtesyMessage(request, true);

            String subject = message.getContent().getSubject();
            assertThat(subject).hasSize(120);
            assertThat(subject).isEqualTo(("Comunicazione a valore legale da " + "X".repeat(200)).substring(0, 120));
            assertThat(message.getContent().getThirdPartyData().getOriginalSender()).isEqualTo("X".repeat(200));
        }

        @Test
        void buildCourtesyMessage_subjectExactly120_isNotTruncated() {
            String prefix = "Comunicazione a valore legale da ";
            String denomination = "Y".repeat(120 - prefix.length());

            NewMessage message = builder.buildCourtesyMessage(buildRequest().senderDenomination(denomination), true);

            assertThat(message.getContent().getSubject()).isEqualTo(prefix + denomination);
        }

        @Test
        void buildCourtesyMessage_setsThirdPartyData() {
            NewMessage message = builder.buildCourtesyMessage(buildRequest(), true);

            ThirdPartyData thirdPartyData = message.getContent().getThirdPartyData();
            assertThat(thirdPartyData.getId()).isEqualTo(IUN);
            assertThat(thirdPartyData.getOriginalSender()).isEqualTo(SENDER_DENOMINATION);
            assertThat(thirdPartyData.getSummary()).isEqualTo("Comune di Milano - avviso di pagamento");
            assertThat(thirdPartyData.getConfigurationId()).isEqualTo(CFG_ID);
            assertThat(thirdPartyData.getOriginalReceiptDate()).isNull();
        }

        static Stream<Arguments> dates() {
            return Stream.of(
                    arguments(Instant.parse("2026-03-30T22:00:00Z"), "2026-03-30T22:00:00Z"),
                    arguments(Instant.parse("2026-03-01T10:15:30.123Z"), "2026-03-01T10:15:30Z"),
                    arguments(Instant.parse("2026-03-01T10:15:30.999999Z"), "2026-03-01T10:15:30Z"),
                    // input di pn-user-attributes: mezzanotte ora italiana, già convertita in UTC da Jackson
                    arguments(OffsetDateTime.parse("2026-03-31T00:00:00+02:00").toInstant(), "2026-03-30T22:00:00Z")
            );
        }

        @ParameterizedTest
        @MethodSource("dates")
        void buildCourtesyMessage_formatsDatesInUtcWithoutFraction(Instant input, String expected) {
            SendMessageRequest request = buildRequest().dueDate(input).requestAcceptedDate(input);

            NewMessage message = builder.buildCourtesyMessage(request, true);

            assertThat(message.getContent().getDueDate()).isEqualTo(expected);
            assertThat(message.getContent().getThirdPartyData().getOriginalReceiptDate()).isEqualTo(expected);
        }
    }

    @Nested
    class OptInMessage {

        @ParameterizedTest
        @NullSource
        @EnumSource(value = SendMessageRequest.DeliveryModeEnum.class, names = "ANALOG")
        void buildOptInMessage_nullOrAnalog_usesAnalogTemplate(SendMessageRequest.DeliveryModeEnum deliveryMode) {
            NewMessage message = builder.buildOptInMessage(buildRequest().deliveryMode(deliveryMode));

            assertThat(message.getContent().getMarkdown()).isEqualTo(expectedMarkdown(templates.getMarkdownOptInAnalog()));
            assertThat(message.getContent().getMarkdown()).contains("attiva il servizio SEND entro 5 giorni");
        }

        @Test
        void buildOptInMessage_digital_usesDigitalTemplate() {
            NewMessage message = builder.buildOptInMessage(
                    buildRequest().deliveryMode(SendMessageRequest.DeliveryModeEnum.DIGITAL));

            assertThat(message.getContent().getMarkdown()).isEqualTo(expectedMarkdown(templates.getMarkdownOptInDigital()));
            assertThat(message.getContent().getMarkdown()).contains("Hai il domicilio digitale attivo");
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(SendMessageRequest.DeliveryModeEnum.class)
        void buildOptInMessage_replacesUrlPlaceholders(SendMessageRequest.DeliveryModeEnum deliveryMode) {
            String markdown = builder.buildOptInMessage(buildRequest().deliveryMode(deliveryMode))
                    .getContent().getMarkdown();

            assertThat(markdown)
                    .contains("(" + URL_TOS + ")")
                    .contains("(" + URL_PRIVACY + ")")
                    .doesNotContain("${piattaformaNotificheURL");
        }

        @Test
        void buildOptInMessage_buildsAdvancedMessageWithoutThirdPartyData() {
            SendMessageRequest request = buildRequest()
                    .dueDate(Instant.parse("2026-03-30T22:00:00Z"))
                    .requestAcceptedDate(Instant.parse("2026-03-01T10:15:30Z"));

            NewMessage message = builder.buildOptInMessage(request);

            assertThat(message.getFeatureLevelType()).isEqualTo("ADVANCED");
            assertThat(message.getFiscalCode()).isEqualTo(TAX_ID);
            assertThat(message.getContent().getSubject()).isEqualTo("Hai una comunicazione a valore legale da SEND");
            assertThat(message.getContent().getThirdPartyData()).isNull();
            assertThat(message.getContent().getDueDate()).isNull();
        }

        private String expectedMarkdown(String template) {
            return template
                    .replace("${piattaformaNotificheURLCittadini}", URL_CITTADINI)
                    .replace("${piattaformaNotificheURLTOS}", URL_TOS)
                    .replace("${piattaformaNotificheURLPrivacy}", URL_PRIVACY);
        }
    }
}
