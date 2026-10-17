package de.iip_ecosphere.platform.examples.energy.sources;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementQuality;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * MQTT ingestor for the Shelly Pro3EM.
 *
 * <p>Each Shelly payload may contain multiple measurements (power, total energy).
 * This ingestor emits <b>one {@link MachineEnergyMeasurement} per measurement type</b>,
 * matching P4's event-based model. It does not group values.</p>
 */
public class ShellyMqttIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(ShellyMqttIngestor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String sourceId;      // e.g. "mqtt"
    private final String deviceId;      // e.g. "ShellyPro3EM"
    private final String brokerUrl;
    private final String clientId;
    private final String topic;
    private final int qos;
    private final Consumer<MachineEnergyMeasurement> consumer;
    private final Consumer<Throwable> errorConsumer;

    private MqttClient client;
    private volatile boolean running = false;

    public ShellyMqttIngestor(String sourceId,
                              String deviceId,
                              String brokerUrl,
                              String clientId,
                              String topic,
                              int qos,
                              Consumer<MachineEnergyMeasurement> consumer,
                              Consumer<Throwable> errorConsumer) {
        this.sourceId = sourceId;
        this.deviceId = deviceId;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topic = topic;
        this.qos = qos;
        this.consumer = consumer;
        this.errorConsumer = errorConsumer;
    }

    public void connect() throws MqttException {
        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(60);

        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect, String serverURI) {
                LOG.info("[{}] MQTT connected (reconnect={})", deviceId, reconnect);
                try {
                    client.subscribe(topic, qos, (t, msg) -> handleMessage(msg));
                } catch (MqttException e) {
                    notifyError(e);
                }
            }
            @Override public void connectionLost(Throwable cause) {
                LOG.warn("[{}] MQTT connection lost", deviceId, cause);
                notifyError(cause);
            }
            @Override public void messageArrived(String t, MqttMessage msg) { }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });

        client.connect(opts);
    }

    public void start() { running = true; }

    public void disconnect() {
        running = false;
        if (client != null && client.isConnected()) {
            try { client.disconnect(); } catch (MqttException e) { LOG.warn("disconnect failed", e); }
        }
    }

    public boolean isConnected() { return client != null && client.isConnected(); }

    // -----------------------------------------------------------------
    // Payload handling — ONE event per measurement
    // -----------------------------------------------------------------

    private void handleMessage(MqttMessage msg) {
        if (!running) return;
        try {
            String payload = new String(msg.getPayload(), StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(payload);
            Instant now = Instant.now();

            if (node.has("power")) {
                emit(MeasurementType.CURRENT_POWER, node.get("power").asDouble(), "W", now);
            }
            if (node.has("total")) {
                emit(MeasurementType.CUMULATIVE_ENERGY, node.get("total").asDouble(), "Wh", now);
            }
        } catch (Exception e) {
            LOG.warn("[{}] Failed to parse Shelly payload", deviceId, e);
            notifyError(e);
        }
    }

    
    private void emit(MeasurementType type, double value, String unit, Instant timestamp) {
        consumer.accept(new MachineEnergyMeasurement(
            sourceId, deviceId, type, value, unit, timestamp, MeasurementQuality.VALID));
    }

    private void notifyError(Throwable t) {
        if (errorConsumer != null) errorConsumer.accept(t);
    }
}
 
