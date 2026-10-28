package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.utils.LogUtils;
import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorOptInDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;
import it.pagopa.pn.ioconnector.middleware.msclient.IOLegalClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_ENABLE_IO_ACTIVATION_MESSAGE;
import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_ENABLE_IO_MESSAGE;
import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_IO_USER_STATUS;
import static it.pagopa.pn.ioconnector.utils.LogUtils.MANAGE_OPT_IN;

@CustomLog
@Service
@RequiredArgsConstructor
public class SendIoMessageService {

    private static final String IO_LOCALE_IT_IT = "it_IT";
    static final String MDC_TOPIC_SEND_IO_MESSAGE = "sendIOMessage";

    private final PnIoConnectorConfig config;
    private final IOConnectorOptInDao ioConnectorOptInDAO;
    private final IOLegalClient ioLegalClient;
    private final IoLegalMessageBuilder ioLegalMessageBuilder;
    private final SendIOSentMessageService sendIOSentMessageService;

    public UserStatusResponse getUserStatus(UserStatusRequest request) {
        String taxId = request.getTaxId();
        return new UserStatusResponse().taxId(taxId).status(getUserStatusInternal(taxId).status());
    }

    UserStatusInternal getUserStatusInternal(String taxId) {
        try {
            LimitedProfile profile = ioLegalClient.getProfile(new FiscalCodePayload().fiscalCode(taxId));
            UserStatusResponse.StatusEnum status = Boolean.TRUE.equals(profile.getSenderAllowed())
                    ? UserStatusResponse.StatusEnum.PN_ACTIVE
                    : UserStatusResponse.StatusEnum.PN_NOT_ACTIVE;
            return new UserStatusInternal(status, profile.getPreferredLanguages());
        } catch (PnHttpResponseException e) {
            if (e.getStatusCode() == 404) {
                return new UserStatusInternal(UserStatusResponse.StatusEnum.APPIO_NOT_ACTIVE, null);
            }
            log.warn("getUserStatus failed for taxId={} - status={}", LogUtils.maskTaxId(taxId), e.getStatusCode());
            return new UserStatusInternal(UserStatusResponse.StatusEnum.ERROR, null);
        }
    }

    static boolean isPreferredLanguageIT(List<String> preferredLanguages) {
        return CollectionUtils.isEmpty(preferredLanguages) || preferredLanguages.contains(IO_LOCALE_IT_IT);
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        String requestId = StringUtils.hasText(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString();
        request.setRequestId(requestId);

        MDC.put(MDCUtils.MDC_PN_IUN_KEY, request.getIun());
        MDC.put(MDCUtils.MDC_CX_ID_KEY, request.getRecipientInternalID());
        MDC.put(MDCUtils.MDC_PN_CTX_RECIPIENT_INDEX, String.valueOf(request.getRecipientIndex()));
        MDC.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, requestId);
        MDC.put(MDCUtils.MDC_PN_CTX_TOPIC, MDC_TOPIC_SEND_IO_MESSAGE);
        try {
            log.logChecking(CHECK_IO_USER_STATUS);
            UserStatusInternal userStatus = getUserStatusInternal(request.getRecipientTaxID());
            if (userStatus.status() == UserStatusResponse.StatusEnum.ERROR) {
                log.logCheckingOutcome(CHECK_IO_USER_STATUS, false, "ERROR, message not sent");
            } else {
                log.logCheckingOutcome(CHECK_IO_USER_STATUS, true);
            }

            SendMessageResponse response = switch (userStatus.status()) {
                case APPIO_NOT_ACTIVE -> {
                    log.info("IO user status=APPIO_NOT_ACTIVE, message not sent");
                    yield result(SendMessageResponse.ResultEnum.NOT_SENT_APPIO_UNAVAILABLE);
                }
                case PN_ACTIVE -> {
                    log.info("IO user status=PN_ACTIVE, courtesy flow");
                    yield sendCourtesyMessage(request, isPreferredLanguageIT(userStatus.preferredLanguages()));
                }
                case PN_NOT_ACTIVE -> {
                    log.info("IO user status=PN_NOT_ACTIVE, opt-in flow");
                    yield manageOptIn(request);
                }
                case ERROR -> result(SendMessageResponse.ResultEnum.ERROR_USER_STATUS);
            };
            response.setRequestId(requestId);
            return response;
        } finally {
            MDC.remove(MDCUtils.MDC_PN_IUN_KEY);
            MDC.remove(MDCUtils.MDC_CX_ID_KEY);
            MDC.remove(MDCUtils.MDC_PN_CTX_RECIPIENT_INDEX);
            MDC.remove(MDCUtils.MDC_PN_CTX_REQUEST_ID);
            MDC.remove(MDCUtils.MDC_PN_CTX_TOPIC);
        }
    }

    private SendMessageResponse sendCourtesyMessage(SendMessageRequest request, boolean localeIsIT) {
        log.info("Sending courtesy message carbonCopyToDeliveryPush={}", request.getCarbonCopyToDeliveryPush());
        log.logChecking(CHECK_ENABLE_IO_MESSAGE);
        if (!config.isEnableIoMessage()) {
            log.logCheckingOutcome(CHECK_ENABLE_IO_MESSAGE, false, "courtesy message disabled by configuration");
            return result(SendMessageResponse.ResultEnum.NOT_SENT_COURTESY_DISABLED_BY_CONF);
        }
        log.logCheckingOutcome(CHECK_ENABLE_IO_MESSAGE, true);

        NewMessage message = ioLegalMessageBuilder.buildCourtesyMessage(request, localeIsIT);
        try {
            String id = ioLegalClient.sendCourtesyMessage(message);

            if (Boolean.TRUE.equals(request.getCarbonCopyToDeliveryPush())) {
                sendIOSentMessageService.sendIOSentMessageNotification(request.getIun(), request.getRecipientIndex(),
                        request.getRecipientInternalID(), Instant.now());
            } else {
                log.info("Send CC to delivery push not required");
            }

            return result(SendMessageResponse.ResultEnum.SENT_COURTESY).id(id);
        } catch (Exception e) {
            log.error("Error sending courtesy message, result=ERROR_COURTESY: {}", e.getMessage(), e);
            return result(SendMessageResponse.ResultEnum.ERROR_COURTESY);
        }
    }

    /**
     * Valuta il cooldown sugli invii di messaggi di opt-in,
     * se trascorso invia il messaggio, altrimenti ritorna NOT_SENT_OPTIN_ALREADY_SENT
     *
     * @return SendMessageResponse
     */
    SendMessageResponse manageOptIn(SendMessageRequest request) {
        log.logStartingProcess(MANAGE_OPT_IN);

        String hashedTaxId = DigestUtils.sha256Hex(request.getRecipientTaxID());

        Instant lastSendDate = ioConnectorOptInDAO.get(hashedTaxId)
                .map(IOConnectorOptInEntity::getLastModified)
                .orElse(Instant.EPOCH);

        if (lastSendDate.isBefore(Instant.now().minus(config.getIoOptinMinDays(), ChronoUnit.DAYS))) {
            log.info("Cooldown scaduto, opt-in inviabile a taxId={} iun={} lastModified={} requestId={}",
                    LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun(), lastSendDate, request.getRequestId());
            SendMessageResponse res = sendOptInMessage(request);
            ioConnectorOptInDAO.save(IOConnectorOptInEntity.of(hashedTaxId, config.getIoOptinMinDays()));
            log.logEndingProcess(MANAGE_OPT_IN);
            return res;
        } else {
            log.info("Cooldown attivo, opt-in già inviato a taxId={} iun={} lastModified={} requestId={}",
                    LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun(), lastSendDate, request.getRequestId());
            SendMessageResponse res = new SendMessageResponse();
            res.setResult(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_ALREADY_SENT);
            log.logEndingProcess(MANAGE_OPT_IN);
            return res;
        }
    }

    private SendMessageResponse sendOptInMessage(SendMessageRequest request) {
        log.logChecking(CHECK_ENABLE_IO_ACTIVATION_MESSAGE);
        if (!config.isEnableIoActivationMessage()) {
            log.logCheckingOutcome(CHECK_ENABLE_IO_ACTIVATION_MESSAGE, false, "opt-in message disabled by configuration");
            return result(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_DISABLED_BY_CONF);
        }
        log.logCheckingOutcome(CHECK_ENABLE_IO_ACTIVATION_MESSAGE, true);

        NewMessage message = ioLegalMessageBuilder.buildOptInMessage(request);
        try {
            String id = ioLegalClient.sendOptInMessage(message);
            return result(SendMessageResponse.ResultEnum.SENT_OPTIN).id(id);
        } catch (Exception e) {
            log.error("Error sending opt-in message, result=ERROR_OPTIN: {}", e.getMessage(), e);
            return result(SendMessageResponse.ResultEnum.ERROR_OPTIN);
        }
    }

    private static SendMessageResponse result(SendMessageResponse.ResultEnum result) {
        return new SendMessageResponse().result(result);
    }
}
