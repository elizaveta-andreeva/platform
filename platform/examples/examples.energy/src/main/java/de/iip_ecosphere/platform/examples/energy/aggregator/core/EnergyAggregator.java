package de.iip_ecosphere.platform.examples.energy.aggregator.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

public class EnergyAggregator {

    private final Map<MeasurementKey, MachineEnergyMeasurement> latestMeasurements
        = new ConcurrentHashMap<>();

    public void updateMeasurement(MachineEnergyMeasurement measurement) {
        MeasurementKey key = new MeasurementKey(
            measurement.deviceId(),
            measurement.measurementType()
        );

        latestMeasurements.compute(key, (k, existingMeasurement) -> {
            if (existingMeasurement == null
                    || measurement.timestamp().isAfter(existingMeasurement.timestamp())) {
                return measurement;
            }

            return existingMeasurement;
        });
    }
    
    public Optional<MachineEnergyMeasurement> getLatestMeasurement(
    	    String deviceId,
    	    MeasurementType measurementType
    	) {
    	    MeasurementKey key = new MeasurementKey(deviceId, measurementType);
    	    return Optional.ofNullable(latestMeasurements.get(key));
    	}

    private record MeasurementKey(
        String deviceId,
        MeasurementType measurementType
    ) {
    }

}