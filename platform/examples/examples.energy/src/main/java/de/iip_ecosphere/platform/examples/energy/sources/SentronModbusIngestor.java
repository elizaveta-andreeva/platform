package de.iip_ecosphere.platform.examples.energy.sources;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementQuality;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

import de.re.easymodbus.modbusclient.ModbusClient;  // verify against repo

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * ModbusTCP ingestor for the Siemens Sentron PAC.
 *
 * <p>Polls configured registers at a fixed interval and emits <b>one
 * {@link MachineEnergyMeasurement} per register</b>.</p>
 */
public class SentronModbusIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(SentronModbusIngestor.class);

    private final String sourceId;   // e.g. "modbus"
    private final String deviceId;   // e.g. "SentronPAC"
    private final String host;
    private final int port;
    private final int unitId;
    private final int intervalMs;
    private final Consumer<MachineEnergyMeasurement> consumer;
    private final Consumer<Throwable> errorConsumer;

    private ModbusClient client;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private volatile boolean running = false;
    private volatile boolean connected = false;

    // Register offsets — from ModbusSiemensSentron.ivml
    private static final int REG_POWER_CONSUMPTION = 25;

    public SentronModbusIngestor(String sourceId, String deviceId,
                                 String host, int port, int unitId, int intervalMs,
                                 Consumer<MachineEnergyMeasurement> consumer,
                                 Consumer<Throwable> errorConsumer) {
        this.sourceId = sourceId;
        this.deviceId = deviceId;
        this.host = host;
        this.port = port;
        this.unitId = unitId;
        this.intervalMs = intervalMs;
        this.consumer = consumer;
        this.errorConsumer = errorConsumer;
    }

    public void connect() throws Exception {
        client = new ModbusClient(host, port);
        client.setUnitIdentifier((byte) unitId);
        client.Connect();
        connected = true;
        LOG.info("[{}] ModbusTCP connected to {}:{}", deviceId, host, port);
    }

    public void start() {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sentron-modbus-" + deviceId);
            t.setDaemon(true);
            return t;
        });
        task = scheduler.scheduleAtFixedRate(this::pollOnce, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdown();
    }

    public void disconnect() {
        stop();
        if (client != null && client.isConnected()) {
            try { client.Disconnect(); } catch (Exception e) { LOG.warn("disconnect failed", e); }
        }
        connected = false;
    }

    public boolean isConnected() { return connected; }

    // -----------------------------------------------------------------
    // Polling — ONE event per register
    // -----------------------------------------------------------------

    private void pollOnce() {
        if (!running) return;
        try {
            if (!client.isConnected()) {
                client.Connect();
                connected = true;
            }

            float power = client.ReadInputRegisters(REG_POWER_CONSUMPTION, 2)[0];
            Instant now = Instant.now();

            consumer.accept(new MachineEnergyMeasurement(
                sourceId, deviceId,
                MeasurementType.CURRENT_POWER, power, "W",
                now, MeasurementQuality.GOOD));

        } catch (Exception e) {
            connected = false;
            LOG.warn("[{}] Modbus poll failed", deviceId, e);
            emitBadQuality();
            notifyError(e);
        }
    }

    
    private void emitBadQuality() {
    consumer.accept(new MachineEnergyMeasurement(
        sourceId, deviceId,
        MeasurementType.CURRENT_POWER, 0.0, "W",
        Instant.now(), MeasurementQuality.UNAVAILABLE));
    }

    private void notifyError(Throwable t) {
        if (errorConsumer != null) errorConsumer.accept(t);
    }
}
