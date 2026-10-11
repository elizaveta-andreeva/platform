package test.de.iip_ecosphere.platform.examples.energy.aggregator.mapping;

import static org.junit.Assert.assertEquals;

import java.time.Instant;

import org.junit.Test;

import de.iip_ecosphere.platform.examples.energy.aggregator.mapping.OpcEnergyModelMapper;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementQuality;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

public class OpcEnergyModelMapperTest {

    @Test
    public void testCurrentPowerInWatts() {
        OpcEnergyModelMapper mapper = new OpcEnergyModelMapper();

        MachineEnergyMeasurement measurement =
            new MachineEnergyMeasurement(
                "shelly-1",
                "lathe",
                MeasurementType.CURRENT_POWER,
                500.0,
                "W",
                Instant.parse("2026-10-11T14:00:00Z"),
                MeasurementQuality.VALID
            );

        MappedModelUpdate result = mapper.map(measurement);

        assertEquals("AC_ACTIVE_POWER_TOTAL", result.target());
        assertEquals(500.0, result.value(), 0.0);
        assertEquals("W", result.unit());
        assertEquals(measurement.timestamp(), result.timestamp());
        assertEquals(MeasurementQuality.VALID, result.quality());
    }

    @Test
    public void testCurrentPowerInKilowattsIsConvertedToWatts() {
        OpcEnergyModelMapper mapper = new OpcEnergyModelMapper();

        MachineEnergyMeasurement measurement =
            new MachineEnergyMeasurement(
                "shelly-1",
                "lathe",
                MeasurementType.CURRENT_POWER,
                1.25,
                "kW",
                Instant.parse("2026-10-11T14:05:00Z"),
                MeasurementQuality.VALID
            );

        MappedModelUpdate result = mapper.map(measurement);

        assertEquals("AC_ACTIVE_POWER_TOTAL", result.target());
        assertEquals(1250.0, result.value(), 0.0);
        assertEquals("W", result.unit());
    }
}