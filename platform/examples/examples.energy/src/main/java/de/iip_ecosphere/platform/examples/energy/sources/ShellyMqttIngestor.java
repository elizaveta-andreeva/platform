package de.iip_ecosphere.platform.examples.energy.sources;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.iip_ecosphere.platform.examples.energy.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.model.Quality;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * MQTT ingestor for the Shelly Pro3EM energy meter.
 *
 * <p>Subscribes to the Shelly's emeter topic and converts each JSON payload
 * into a {@link MachineEnergyMeasurement}. Uses Eclipse Paho MQTT v3, which
 * is the same client library used by oktoflow's connectors.mqttv3 module.</p>
 *
 * <p>Connection/error handling: the ingestor reconnects with exponential
 * backoff on connection loss. Errors are reported to the error consumer.</p>
 */
public class ShellyMqttIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(ShellyMqttIngestor.class);

    private final String sourceId;
    private final String brokerUrl;
    private final String clientId;
    private final String topic;
    private final int qos;

    private final Consumer<MachineEnergyMeasurement> measurementConsumer;
    private final Consumer<Throwable> errorConsumer;

    private MqttClient client;
    private volatile boolean running = false;

    // Reconnect backoff
    private static final long RECONNECT_INITIAL_MS = 5_000;
    private static final long RECONNECT_MAX_MS = 60_000;
    private static final double RECONNECT_MULTIPLIER = 2.0;

    public ShellyMqttIngestor(String sourceId,
                              String brokerUrl,
                              String clientId,
                              String topic,
                              int qos,
                              Consumer<MachineEnergyMeasurement> measurementConsumer,
                              Consumer<Throwable> errorConsumer) {
        this.sourceId = sourceId;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topic = topic;
        this.qos = qos;
        this.measurementConsumer = measurementConsumer;
        this.errorConsumer = errorConsumer;
    }

    /** Establishes the MQTT connection and subscribes to the topic. */
    public void connect() throws MqttException {
        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);       // Paho handles reconnection
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(60);

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                LOG.info("[{}] MQTT connected (reconnect={}) to {}", sourceId, reconnect, serverURI);
                try {
                    client.subscribe(topic, qos, (t, msg) -> handleMessage(msg));
                } catch (MqttException e) {
                    notifyError(e);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                LOG.warn("[{}] MQTT connection lost", sourceId, cause);
                notifyError(cause);
            }

            @Override
            public void messageArrived(String t, MqttMessage msg) {
                // Handled by the lambda in connectComplete; kept for compatibility.
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used (we only subscribe).
            }
        });

        client.connect(opts);
    }

    /** Starts producing measurements. Assumes {@link #connect()} succeeded. */
    public void start() {
        running = true;
    }

    /** Stops producing measurements but keeps the connection. */
    public void stop() {
        running = false;
    }

    /** Disconnects from the broker. */
    public void disconnect() {
        running = false;
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                LOG.warn("[{}] Error during MQTT disconnect", sourceId, e);
            }
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    // -----------------------------------------------------------------
    // Payload handling
    // -----------------------------------------------------------------

    private void handleMessage(MqttMessage msg) {
        if (!running) {
            return;
        }
        try {
            String payload = new String(msg.getPayload(), StandardCharsets.UTF_8);
            MachineEnergyMeasurement m = parseShellyPayload(payload);
            if (m != null) {
                measurementConsumer.accept(m);
            }
        } catch (Exception e) {
            LOG.warn("[{}] Failed to parse MQTT payload", sourceId, e);
            notifyError(e);
        }
    }

    /**
     * Parses a Shelly Pro3EM JSON payload.
     *
     * <p>Expected shape (verify against actual Shelly firmware):
     * <pre>
     * { "power": 1500.5, "total": 123456.7 }
     * </pre>
     * If the Shelly emits a different schema (e.g. per-phase values under
     * "emeters": [...]), adapt this method.</p>
     */
    private MachineEnergyMeasurement parseShellyPayload(String json) {
        // NOTE: Replace with your preferred JSON parser (Jackson, Gson, etc.).
        // The oktoflow platform bundles Jackson via support.json-jackson.
        com.fasterxml.jackson.databind.JsonNode node =
            de.iip_ecosphere.platform.support.json.JsonUtils.parseJson(json);

        if (node == null || !node.has("power")) {
            return null;
        }

        MachineEnergyMeasurement m = new MachineEnergyMeasurement();
        m.setSourceId(sourceId);
        m.setTimestamp(Instant.now().toEpochMilli());
        m.setPowerWatts(node.get("power").asDouble());
        m.setEnergyWh(node.has("total") ? node.get("total").asDouble() : 0.0);
        m.setMachineState("UNKNOWN");
        m.setQuality(Quality.GOOD);
        return m;
    }

    private void notifyError(Throwable t) {
        if (errorConsumer != null) {
            errorConsumer.accept(t);
        }
    }
}
