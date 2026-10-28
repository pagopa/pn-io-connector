package it.pagopa.pn.ioconnector.config;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class IoLegalMessageTemplatesTest {

    private final IoLegalMessageTemplates templates = new IoLegalMessageTemplates();

    static Stream<Arguments> templatesWithExtRegistriesHash() {
        return Stream.of(
                arguments(IoLegalMessageTemplates.MARKDOWN_COURTESY_IT,
                        (Function<IoLegalMessageTemplates, String>) IoLegalMessageTemplates::getMarkdownCourtesyIt,
                        "25d2cbbb6a47343903165112f71d1e199dffa504831d1176af2557183d14f03a"),
                arguments(IoLegalMessageTemplates.MARKDOWN_COURTESY_EN,
                        (Function<IoLegalMessageTemplates, String>) IoLegalMessageTemplates::getMarkdownCourtesyEn,
                        "2586e5948547c6f8e5b2b8f665eac6fd824656aeb8a922058b7c5c453bec847b"),
                arguments(IoLegalMessageTemplates.MARKDOWN_OPTIN_ANALOG,
                        (Function<IoLegalMessageTemplates, String>) IoLegalMessageTemplates::getMarkdownOptInAnalog,
                        "6210b040345e1a72c02f6177c14e15f831c21f5a15c6547f08ede0555369f9cc"),
                arguments(IoLegalMessageTemplates.MARKDOWN_OPTIN_DIGITAL,
                        (Function<IoLegalMessageTemplates, String>) IoLegalMessageTemplates::getMarkdownOptInDigital,
                        "8e1f04388e5a5bd6da0153a7c17b1e89dfd2b19871db6578f82682a2cb66dd3e"),
                arguments(IoLegalMessageTemplates.SUBJECT_OPTIN,
                        (Function<IoLegalMessageTemplates, String>) IoLegalMessageTemplates::getSubjectOptIn,
                        "cb5c3ad32ee06f4982548f9affbec311b5696e9a66d184a270baee34ec565363"),
                arguments(IoLegalMessageTemplates.SUBJECT_COURTESY,
                        (Function<IoLegalMessageTemplates, String>) IoLegalMessageTemplates::getSubjectCourtesy,
                        "d3f71be5302642341146763e1dd1c5381510e2b67f38a712b7f81f623f5a9e61")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templatesWithExtRegistriesHash")
    void template_isIdenticalToExtRegistries(String filename,
                                             Function<IoLegalMessageTemplates, String> getter,
                                             String expectedSha256) throws NoSuchAlgorithmException {
        String content = getter.apply(templates);

        assertThat(content).isNotEmpty();
        assertThat(sha256(content)).as(filename).isEqualTo(expectedSha256);
    }

    @Test
    void markdownCourtesyIt_keepsTrailingNewline() {
        assertThat(templates.getMarkdownCourtesyIt())
                .isEqualTo("Questo messaggio contiene una notifica, ovvero una **comunicazione a valore legale**.\n");
    }

    @Test
    void subjectCourtesy_containsSenderDenominationPlaceholder() {
        assertThat(templates.getSubjectCourtesy())
                .isEqualTo("Comunicazione a valore legale da {{senderDenomination}}");
    }

    @Test
    void missingTemplate_failsFast() {
        assertThatThrownBy(() -> new IoLegalMessageTemplates("not_existing_templates/"))
                .isInstanceOfSatisfying(PnInternalException.class, e -> {
                    assertThat(e.getProblem().getDetail()).contains(IoLegalMessageTemplates.MARKDOWN_COURTESY_IT);
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_TEMPLATE);
                });
    }

    private static String sha256(String content) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
