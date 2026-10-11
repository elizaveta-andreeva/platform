package test.de.iip_ecosphere.platform.examples.energy.aggregator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.Test;

import de.iip_ecosphere.platform.examples.energy.aggregator.core.EnergyAggregator;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementQuality;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

public class EnergyAggregatorTest {

    @Test
    public void testFirstMeasurementIsStored() {
        EnergyAggregator aggregator = new EnergyAggregator();

        MachineEnergyMeasurement measurement = new MachineEnergyMeasurement(
            "shelly-1",
            "lathe",
            MeasurementType.CURRENT_POWER,
            500.0,
            "W",
            Instant.parse("2026-10-08T10:00:00Z"),
            MeasurementQuality.VALID
        );

        aggregator.updateMeasurement(measurement);

        Optional<MachineEnergyMeasurement> result =
            aggregator.getLatestMeasurement(
                "lathe",
                MeasurementType.CURRENT_POWER
            );

        assertTrue(result.isPresent());
        assertEquals(500.0, result.get().value(), 0.0);
    }

    @Test
    public void testNewerMeasurementReplacesExistingMeasurement() {
        EnergyAggregator aggregator = new EnergyAggregator();

        MachineEnergyMeasurement olderMeasurement =
            new MachineEnergyMeasurement(
                "shelly-1",
                "lathe",
                MeasurementType.CURRENT_POWER,
                500.0,
                "W",
                Instant.parse("2026-10-08T10:00:00Z"),
                MeasurementQuality.VALID
            );

        MachineEnergyMeasurement newerMeasurement =
            new MachineEnergyMeasurement(
                "shelly-1",
                "lathe",
                MeasurementType.CURRENT_POWER,
                650.0,
                "W",
                Instant.parse("2026-10-08T10:05:00Z"),
                MeasurementQuality.VALID
            );

        aggregator.updateMeasurement(olderMeasurement);
        aggregator.updateMeasurement(newerMeasurement);

        Optional<MachineEnergyMeasurement> result =
            aggregator.getLatestMeasurement(
                "lathe",
                MeasurementType.CURRENT_POWER
            );

        assertTrue(result.isPresent());
        assertEquals(650.0, result.get().value(), 0.0);
    }

    @Test
    public void testOlderMeasurementDoesNotReplaceNewerMeasurement() {
        EnergyAggregator aggregator = new EnergyAggregator();

        MachineEnergyMeasurement newerMeasurement =
            new MachineEnergyMeasurement(
                "shelly-1",
                "lathe",
                MeasurementType.CURRENT_POWER,
                650.0,
                "W",
                Instant.parse("2026-10-08T10:05:00Z"),
                MeasurementQuality.VALID
            );

        MachineEnergyMeasurement olderMeasurement =
            new MachineEnergyMeasurement(
                "shelly-1",
                "lathe",
                MeasurementType.CURRENT_POWER,
                500.0,
                "W",
                Instant.parse("2026-10-08T10:00:00Z"),
                MeasurementQuality.VALID
            );

        aggregator.updateMeasurement(newerMeasurement);
        aggregator.updateMeasurement(olderMeasurement);

        Optional<MachineEnergyMeasurement> result =
            aggregator.getLatestMeasurement(
                "lathe",
                MeasurementType.CURRENT_POWER
            );

        assertTrue(result.isPresent());
        assertEquals(650.0, result.get().value(), 0.0);
    }
}