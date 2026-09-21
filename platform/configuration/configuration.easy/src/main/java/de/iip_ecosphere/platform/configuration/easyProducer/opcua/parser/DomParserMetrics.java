package de.iip_ecosphere.platform.configuration.easyProducer.opcua.parser;

import java.util.concurrent.TimeUnit;

/**
 * Timing information for one complete {@link DomParser} invocation.
 *
 * @author Elizaveta Andreeva
 */
public final class DomParserMetrics {

    private long totalNanos;
    private long loadAndIndexNanos;
    private long parseNanos;
    private long collectorNanos;
    private long generateNanos;

    /**
     * Returns the duration of the complete generation process.
     *
     * @return the total duration in milliseconds
     */
    public long getTotalMs() {
        return toMilliseconds(totalNanos);
    }

    /**
     * Returns the duration of loading XML documents, resolving required models and creating lookup indexes.
     *
     * @return the load and index duration in milliseconds
     */
    public long getLoadAndIndexMs() {
        return toMilliseconds(loadAndIndexNanos);
    }

    /**
     * Returns the duration of translating the loaded DOM into the internal hierarchy.
     *
     * @return the parser duration in milliseconds
     */
    public long getParseMs() {
        return toMilliseconds(parseNanos);
    }

    /**
     * Returns the duration of collecting model statistics.
     *
     * @return the collector duration in milliseconds
     */
    public long getCollectorMs() {
        return toMilliseconds(collectorNanos);
    }

    /**
     * Returns the duration of generating the IVML model and connector settings.
     *
     * @return the generation duration in milliseconds
     */
    public long getGenerateMs() {
        return toMilliseconds(generateNanos);
    }

    private static long toMilliseconds(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    void setTotalNanos(long totalNanos) {
        this.totalNanos = totalNanos;
    }

    void setLoadAndIndexNanos(long loadAndIndexNanos) {
        this.loadAndIndexNanos = loadAndIndexNanos;
    }

    void setParseNanos(long parseNanos) {
        this.parseNanos = parseNanos;
    }

    void setCollectorNanos(long collectorNanos) {
        this.collectorNanos = collectorNanos;
    }

    void setGenerateNanos(long generateNanos) {
        this.generateNanos = generateNanos;
    }
}
