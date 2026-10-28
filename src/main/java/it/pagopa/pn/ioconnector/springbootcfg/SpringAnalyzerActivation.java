package it.pagopa.pn.ioconnector.springbootcfg;

import io.micrometer.core.instrument.MeterRegistry;
import it.pagopa.pn.commons.utils.metrics.SpringAnalyzer;
import it.pagopa.pn.commons.utils.metrics.cloudwatch.CloudWatchMetricHandler;
import lombok.CustomLog;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

import java.util.Arrays;
import java.util.Collection;

/**
 * Metriche CloudWatch sugli invii dei messaggi legali verso IO,
 * replicate da pn-external-registries (stessi nomi e stessa dimensione)
 */
@Component
@CustomLog
@Import(CloudWatchMetricHandler.class)
public class SpringAnalyzerActivation extends SpringAnalyzer {

    public static final String IO_SENT_SUCCESSFULLY = "NumberOfIOMessageSentSuccessfully";
    public static final String IO_SENT_FAILURE = "NumberOfIOMessageSentFailure";

    protected static final Collection<String> BUSINESS_METRICS = Arrays.asList(IO_SENT_SUCCESSFULLY, IO_SENT_FAILURE);

    private final MeterRegistry meterRegistry;

    public SpringAnalyzerActivation(CloudWatchMetricHandler cloudWatchMetricHandler, MetricsEndpoint metricsEndpoint, MeterRegistry meterRegistry) {
        super(cloudWatchMetricHandler, metricsEndpoint);
        this.meterRegistry = meterRegistry;
        this.meterRegistry.counter(IO_SENT_SUCCESSFULLY);
        this.meterRegistry.counter(IO_SENT_FAILURE);
    }

    @Override
    public void init() {
        super.init();
        this.getMetrics().addAll(BUSINESS_METRICS);
    }

    @Override
    protected Dimension customizedDimension(Dimension dimension, String metricName) {
        if (BUSINESS_METRICS.contains(metricName)) {
            return Dimension.builder()
                    .name("events")
                    .value("courtesy-messages")
                    .build();
        }
        return super.customizedDimension(dimension, metricName);
    }

    /**
     * Dopo l'invio a CloudWatch i contatori di business vengono azzerati
     */
    @Override
    protected void metricSuccessfullSendListener(String metricName) {
        if (BUSINESS_METRICS.contains(metricName)) {
            this.meterRegistry.remove(this.meterRegistry.get(metricName).meter());
            this.meterRegistry.counter(metricName);
        } else {
            super.metricSuccessfullSendListener(metricName);
        }
    }
}
