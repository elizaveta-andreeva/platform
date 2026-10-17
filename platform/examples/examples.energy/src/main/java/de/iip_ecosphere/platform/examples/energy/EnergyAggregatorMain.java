package de.iip_ecosphere.platform.examples.energy;

import de.iip_ecosphere.platform.examples.energy.aggregator.mapping.OpcEnergyModelMapper;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;
import de.iip_ecosphere.platform.examples.energy.server.EnergyAggregatorServer;
import de.iip_ecosphere.platform.examples.energy.server.EnergyNamespace;

import de.iip_ecosphere.platform.examples.energy.sources.ShellyMqttIngestor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end smoke test:
 *
 * <pre>
 *   MQTT → MachineEnergyMeasurement → OpcEnergyModelMapper → MappedModelUpdate
 *        → EnergyAggregatorServer → OPC UA client reads
 * </pre>
 *
 * <p>Enable MQTT with {@code -Dp3.mqtt.enabled=true}. Without it, the harness
 * runs a server-only check using hand-crafted updates.</p>
 */
public class EnergyAggregatorMain {

    private static final Logger LOG = LoggerFactory.getLogger(EnergyAggregatorMain.class);

    public static void main(String[] args) throws Exception {
        EnergyAggregatorServer server = new EnergyAggregatorServer("127.0.0.1", 4840, "smoke");
        server.start();

        OpcEnergyModelMapper mapper = new OpcEnergyModelMapper();

        java.util.function.Consumer<de.iip_ecosphere.platform.examples.energy.aggregator.model
            .MachineEnergyMeasurement> pipeline = m -> {
            LOG.info("Measurement: {} {} = {} {} ({})",
                m.deviceId(), m.measurementType(), m.value(), m.unit(), m.quality());
            try {
                MappedModelUpdate update = mapper.map(m);
                server.applyUpdate(update);
                LOG.info("Applied update: target='{}' value={}",
                    update.target(), update.value());
            } catch (EnergyNamespace.UnknownTargetException e) {
                LOG.error("Mapper produced unknown target '{}' — is the namespace initialized?",
                    e.getTarget());
            } catch (IllegalArgumentException e) {
                LOG.error("Mapper rejected measurement: {}", e.getMessage());
            }
        };

        if (Boolean.parseBoolean(System.getProperty("p3.mqtt.enabled", "false"))) {
            ShellyMqttIngestor shelly = new ShellyMqttIngestor(
                "mqtt", "ShellyPro3EM",
                System.getProperty("p3.mqtt.broker", "tcp://localhost:1883"),
                "energy-agg-shelly",
                System.getProperty("p3.mqtt.topic", "shellies/shellypro3em/+/emeter/0"),
                1, pipeline, t -> LOG.error("Shelly error", t));
            shelly.connect();
            shelly.start();
            LOG.info("Shelly MQTT ingestor started");
        } else {
            LOG.info("MQTT ingestion disabled. Running server-only check.");
            runServerOnlyCheck(server);
        }

        LOG.info("Server running at {}", server.getEndpointUrl());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }

    private static void runServerOnlyCheck(EnergyAggregatorServer server) throws Exception {
        // Direct MappedModelUpdate — no device prefix because P4's mapper doesn't add one yet.
        // These targets must exist in the namespace. With the flat aliases we registered,
        // "AcActivePowerTotal" maps to ShellyPro3EM's node (first registered).
        var good = de.iip_ecosphere.platform.examples.energy.aggregator.model.OpcUaQuality.GOOD;
        var now  = java.time.Instant.now();

        server.applyUpdate(new MappedModelUpdate("AcActivePowerTotal",         1500.5, "W",  now, good));
        server.applyUpdate(new MappedModelUpdate("AcActiveEnergyTotalImportLp", 123456.7, "Wh", now, good));
        LOG.info("Server-only check: applied 2 updates. Browse with UaExpert.");
    }
}
