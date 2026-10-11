package de.iip_ecosphere.platform.examples.energy.aggregator.mapping;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

public class OpcEnergyModelMapper implements EnergyModelMapper {

    @Override
    public MappedModelUpdate map(MachineEnergyMeasurement measurement) {

        if (measurement.measurementType() == MeasurementType.CURRENT_POWER) {
            return mapCurrentPower(measurement);
        }

        throw new IllegalArgumentException(
            "Unsupported measurement type: " + measurement.measurementType()
        );
    }

    private MappedModelUpdate mapCurrentPower(
    	    MachineEnergyMeasurement measurement
    	) {
    	    double normalizedValue = switch (measurement.unit()) {
    	    case "W" -> measurement.value();
    	    case "kW" -> measurement.value() * 1000.0;
    	    default -> throw new IllegalArgumentException(
    	        "Unsupported power unit: " + measurement.unit()
    	    );
    	    };

    	    return new MappedModelUpdate(
    	        "AC_ACTIVE_POWER_TOTAL",
    	        normalizedValue,
    	        "W",
    	        measurement.timestamp(),
    	        measurement.quality()
    	    );
    	}

}