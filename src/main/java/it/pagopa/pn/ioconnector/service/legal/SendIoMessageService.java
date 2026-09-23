package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.utils.LogUtils;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnNotImplementedException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorOptInDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static it.pagopa.pn.ioconnector.utils.LogUtils.MANAGE_OPT_IN;

@CustomLog
@Service
@RequiredArgsConstructor
public class SendIoMessageService {

    private final PnIoConnectorConfig config;
    private final IOConnectorOptInDao ioConnectorOptInDAO;

    public UserStatusResponse getUserStatus(UserStatusRequest request) {
        throw new PnNotImplementedException("getIoUserStatus");
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        throw new PnNotImplementedException("sendLegalIOMessage");
    }

    /**
     * Valuta il cooldown sugli invii di messaggi di opt-in,
     * se trascorso invia il messaggio, altrimenti ritorna NOT_SENT_OPTIN_ALREADY_SENT
     *
     * @return SendMessageResponse
     */
    public SendMessageResponse manageOptIn(SendMessageRequest request) {
        log.logStartingProcess(MANAGE_OPT_IN);

        String hashedTaxId = DigestUtils.sha256Hex(request.getRecipientTaxID());

        Instant lastSendDate = ioConnectorOptInDAO.get(hashedTaxId)
                .map(IOConnectorOptInEntity::getLastModified)
                .orElse(Instant.EPOCH);

        if (lastSendDate.isBefore(Instant.now().minus(config.getIoOptinMinDays(), ChronoUnit.DAYS))) {
            log.info("Cooldown scaduto, opt-in inviabile a taxId={} iun={} lastModified={} requestId={}",
                    LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun(), lastSendDate, request.getRequestId());
            // TODO: implementare invio del messaggio di opt-in
            log.logEndingProcess(MANAGE_OPT_IN);
            return sendMessage(request);
        } else {
            log.info("Cooldown attivo, opt-in già inviato a taxId={} iun={} lastModified={} requestId={}",
                    LogUtils.maskTaxId(request.getRecipientTaxID()), request.getIun(), lastSendDate, request.getRequestId());
            SendMessageResponse res = new SendMessageResponse();
            res.setResult(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_ALREADY_SENT);
            log.logEndingProcess(MANAGE_OPT_IN);
            return res;
        }
    }
}
