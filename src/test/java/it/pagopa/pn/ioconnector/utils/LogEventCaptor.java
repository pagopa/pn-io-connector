package it.pagopa.pn.ioconnector.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LogEventCaptor implements AutoCloseable {

    private static final String APPLICATION_ROOT_LOGGER = "it.pagopa.pn";

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender;

    private LogEventCaptor(String loggerName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
        this.previousLevel = logger.getLevel();
        this.appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.getMDCPropertyMap();
                super.append(event);
            }
        };
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogEventCaptor attachTo(Class<?> loggedClass) {
        return new LogEventCaptor(loggedClass.getName());
    }

    public static LogEventCaptor attachToAllApplicationLoggers() {
        return new LogEventCaptor(APPLICATION_ROOT_LOGGER);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public boolean hasEvent(Level level, String... fragments) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == level && containsAll(e.getFormattedMessage(), fragments));
    }

    public long countEvents(Level level, String fragment) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment))
                .count();
    }

    private static boolean containsAll(String message, String[] fragments) {
        return Arrays.stream(fragments).allMatch(message::contains);
    }

    public Map<String, String> mdcAt(String fragment) {
        return appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains(fragment))
                .findFirst()
                .map(ILoggingEvent::getMDCPropertyMap)
                .orElseThrow(() -> new AssertionError("No log event containing: " + fragment));
    }

    public boolean anyMessageContains(String text) {
        return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(text)
                || (e.getThrowableProxy() != null
                && e.getThrowableProxy().getMessage() != null
                && e.getThrowableProxy().getMessage().contains(text)));
    }

    public boolean anyEventContains(String text) {
        return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(text)
                || e.getMDCPropertyMap().values().stream().anyMatch(v -> v != null && v.contains(text))
                || (e.getThrowableProxy() != null && throwableContains(e.getThrowableProxy(), text)));
    }

    private static boolean throwableContains(IThrowableProxy proxy, String text) {
        return proxy != null
                && ((proxy.getMessage() != null && proxy.getMessage().contains(text))
                || throwableContains(proxy.getCause(), text));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }
}
