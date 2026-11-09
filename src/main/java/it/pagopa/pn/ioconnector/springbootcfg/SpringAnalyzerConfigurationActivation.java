package it.pagopa.pn.ioconnector.springbootcfg;

import it.pagopa.pn.commons.configs.SpringAnalyzerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Attiva MeterRegistry e MetricsEndpoint di pn-commons e lo scheduling dell'invio
 * delle metriche a CloudWatch (cron pn.analyzer.cloudwatch-metric-cron, "-" = disattivato)
 */
@Configuration
@EnableScheduling
public class SpringAnalyzerConfigurationActivation extends SpringAnalyzerConfiguration {
}
