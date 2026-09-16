package it.pagopa.pn.ioconnector.config;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IoServiceConfigurationResolver {

    private final Map<String, IoServiceConfiguration> configurations;

    public IoServiceConfigurationResolver(Map<String, IoServiceConfiguration> configurations) {
        this.configurations = configurations == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(configurations));
    }

    public IoServiceConfiguration getConfiguration(String serviceId) {
        IoServiceConfiguration configuration = configurations.get(serviceId);
        if (configuration == null) {
            throw new PnInternalException("No configuration specified for serviceId: " + serviceId,
                    PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_SERVICE_NOT_CONFIGURED);
        }
        return configuration;
    }

    public String getConfigurationId(String serviceId) {
        String configurationId = getConfiguration(serviceId).configurationId();
        if (!StringUtils.hasText(configurationId)) {
            throw new PnInternalException("Missing configurationId for serviceId: " + serviceId,
                    PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_IO_CONFIGURATION_ID);
        }
        return configurationId;
    }

    public String getOrganizationFiscalCode(String serviceId) {
        String organizationFiscalCode = getConfiguration(serviceId).organizationFiscalCode();
        if (!StringUtils.hasText(organizationFiscalCode)) {
            throw new PnInternalException("Missing organizationFiscalCode for serviceId: " + serviceId,
                    PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_ORGANIZATION_FISCAL_CODE);
        }
        return organizationFiscalCode;
    }
}
