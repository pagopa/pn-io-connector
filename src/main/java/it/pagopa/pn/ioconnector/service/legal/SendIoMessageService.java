package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.utils.LogUtils;
import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
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

import static it.pagopa.pn.ioconnector.utils.LogUtils.MANAGE_OPT_IN;
import static it.pagopa.pn.ioconnector.utils.LogUtils.SEND_LEGAL_IO_MESSAGE;

@CustomLog
@Service
@RequiredArgsConstructor
public class SendIoMessageService {

    private static final String IO_LOCALE_IT_IT = "it_IT";

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
        log.logStartingProcess(SEND_LEGAL_IO_MESSAGE);
        try {
            log.info("[enter] sendIoMessage taxId={} iun={}", LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun());

            UserStatusInternal userStatus = getUserStatusInternal(request.getRecipientTaxID());
            SendMessageResponse response = switch (userStatus.status()) {
                case APPIO_NOT_ACTIVE -> {
                    log.info("IO is not available for user, not sending message taxId={} iun={}",
                            LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun());
                    yield result(SendMessageResponse.ResultEnum.NOT_SENT_APPIO_UNAVAILABLE);
                }
                case PN_ACTIVE -> sendCourtesyMessage(request, isPreferredLanguageIT(userStatus.preferredLanguages()));
                case PN_NOT_ACTIVE -> manageOptIn(request);
                case ERROR -> {
                    log.info("Error in get user status, not sending message taxId={} iun={}",
                            LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun());
                    yield result(SendMessageResponse.ResultEnum.ERROR_USER_STATUS);
                }
            };
            response.setRequestId(requestId);

            log.logEndingProcess(SEND_LEGAL_IO_MESSAGE);
            return response;
        } catch (Exception e) {
            log.logEndingProcess(SEND_LEGAL_IO_MESSAGE, false, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove(MDCUtils.MDC_PN_IUN_KEY);
            MDC.remove(MDCUtils.MDC_CX_ID_KEY);
            MDC.remove(MDCUtils.MDC_PN_CTX_RECIPIENT_INDEX);
            MDC.remove(MDCUtils.MDC_PN_CTX_REQUEST_ID);
        }
    }

    private SendMessageResponse sendCourtesyMessage(SendMessageRequest request, boolean localeIsIT) {
        log.info("Submit message taxId={} iun={} internalId={} recIndex={} carbonCopy={}",
                LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun(), request.getRecipientInternalID(),
                request.getRecipientIndex(), request.getCarbonCopyToDeliveryPush());
        if (!config.isEnableIoMessage()) {
            log.warn("Send IO message is disabled!!!");
            return result(SendMessageResponse.ResultEnum.NOT_SENT_COURTESY_DISABLED_BY_CONF);
        }

        try {
            log.info("Proceeding with send message iun={}", request.getIun());
            String id = ioLegalClient.sendCourtesyMessage(ioLegalMessageBuilder.buildCourtesyMessage(request, localeIsIT));
            log.info("Sent message iun={}", request.getIun());

            if (Boolean.TRUE.equals(request.getCarbonCopyToDeliveryPush())) {
                sendIOSentMessageService.sendIOSentMessageNotification(request.getIun(), request.getRecipientIndex(),
                        request.getRecipientInternalID(), Instant.now());
            } else {
                log.info("Send CC to delivery push not required");
            }

            return result(SendMessageResponse.ResultEnum.SENT_COURTESY).id(id);
        } catch (Exception e) {
            log.error("Error in submitMessageforUserWithFiscalCodeInBody iun={}", request.getIun());
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
        log.info("Submit activation message taxId={}", LogUtils.maskTaxId(request.getRecipientTaxID()));
        if (!config.isEnableIoActivationMessage()) {
            log.info("Send IO message is disabled");
            return result(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_DISABLED_BY_CONF);
        }

        try {
            log.info("Proceeding with send activation message taxId={}", LogUtils.maskTaxId(request.getRecipientTaxID()));
            String id = ioLegalClient.sendOptInMessage(ioLegalMessageBuilder.buildOptInMessage(request));
            log.info("Sent activation message");
            return result(SendMessageResponse.ResultEnum.SENT_OPTIN).id(id);
        } catch (Exception e) {
            log.error("Error in submitActivationMessageforUserWithFiscalCodeInBody iun={}", request.getIun());
            return result(SendMessageResponse.ResultEnum.ERROR_OPTIN);
        }
    }

    private static SendMessageResponse result(SendMessageResponse.ResultEnum result) {
        return new SendMessageResponse().result(result);
    }
}
