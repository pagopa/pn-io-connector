package it.pagopa.pn.ioconnector.utils;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;

import java.util.regex.Pattern;

public class LogUtils {
    public static final String HANDLE_SEND_REQUEST = "handleSendRequest";
    public static final String GET_IO_PROFILE = "getIOProfile";
    public static final String GET_MESSAGE_DETAILS = "getMessageDetails";
    public static final String MANAGE_OPT_IN = "manageOptIn";
    public static final String UPSERT_IO_ACTIVATION = "upsertIoActivation";
    public static final String GET_IO_ACTIVATION = "getIoActivation";
    public static final String CHECK_IO_USER_STATUS = "checkIoUserStatus";
    public static final String CHECK_ENABLE_IO_MESSAGE = "enableIoMessage";
    public static final String CHECK_ENABLE_IO_ACTIVATION_MESSAGE = "enableIoActivationMessage";
    public static final String SEND_WORKER_PROCESS = "sendWorkerProcess";
    public static final String POLLING_WORKER_PROCESS = "pollingWorkerProcess";
    public static final String CHECK_ATTACHMENTS = "attachments validity";
    public static final String CHECK_SENDER_ALLOWED = "sender allowed";

    private static final Pattern TAX_ID_IN_MESSAGES_PATH = Pattern.compile("(/messages/)[^/\\s\"?]+");

    public static String describeError(Throwable throwable) {
        String name = throwable.getClass().getSimpleName();
        return throwable instanceof PnHttpResponseException httpException
                ? name + " statusCode=" + httpException.getStatusCode()
                : name;
    }

    public static String maskTaxIdInPath(String message) {
        return message == null ? null : TAX_ID_IN_MESSAGES_PATH.matcher(message).replaceAll("$1***");
    }
}
