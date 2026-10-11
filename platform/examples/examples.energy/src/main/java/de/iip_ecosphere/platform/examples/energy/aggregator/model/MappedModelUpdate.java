package de.iip_ecosphere.platform.examples.energy.aggregator.model;

import java.time.Instant;

public record MappedModelUpdate(
    String target,
    double value,
    String unit,
    Instant timestamp,
    MeasurementQuality quality
) {
}