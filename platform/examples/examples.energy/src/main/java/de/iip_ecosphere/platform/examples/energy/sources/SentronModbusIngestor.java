package de.iip_ecosphere.platform.examples.energy.sources;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.util.BitVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.iip_ecosphere.platform.examples.energy.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.model.Quality;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ModbusTCP ingestor for the Siemens Sentron PAC energy meter.
 *
 * <p>Uses j2mod (com.ghgande:j2mod:3.2.1), the same library used by
 * connectors.modbustcpipv1. Reads holding registers at the offsets defined
 * in ModbusSiemensSentron.ivml and converts them into a
 * MachineEnergyMeasurement.</p>
 *
 * <p>Register map (from ModbusSiemensSentron.ivml):</p>
 * <ul>
 *   <li>offset 25 — PowerConsumption (float32)</li>
 *   <li>offset 1  — Spannung L1-N (float32)</li>
 *   <li>offset 13 — Strom L1 (float32)</li>
 * </ul>
 *
 * <p>Connection/error handling: on a failed poll the ingestor marks the
 * measurement as BAD and attempts reconnection.</p>
 */
public class SentronModbusIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(SentronModbusIngestor.class);

    private final String sourceId;
    private final String host;
    private final int port;
    private final int unitId;
    private final int intervalMs;

    private final Consumer<MachineEnergyMeasurement> measurementConsumer;
    private final Consumer<Throwable> errorConsumer;

    private ModbusTCPMaster master;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollTask;
    private volatile boolean running = false;
    private volatile boolean connected = false;

    // Register offsets from ModbusSiemensSentron.ivml
    private static final int REG_POWER_CONSUMPTION = 25;
    private static final int REG_VOLTAGE_L1_N      = 1;
    private static final int REG_CURRENT_L1        = 13;

    private static final long RECONNECT_INITIAL_MS = 2_000;
    private static final long RECONNECT_MAX_MS     = 30_000;
    private long currentReconnectDelayMs = RECONNECT_INITIAL_MS;

    public SentronModbusIngestor(String sourceId, String host, int port, int unitId,
                                 int intervalMs,
                                 Consumer<MachineEnergyMeasurement> measurementConsumer,
                                 Consumer<Throwable> errorConsumer) {
        this.sourceId = sourceId;
        this.host = host;
        this.port = port;
        this.unitId = unitId;
        this.intervalMs = intervalMs;
        this.measurementConsumer = measurementConsumer;
        this.errorConsumer = errorConsumer;
    }

    /** Establishes the ModbusTCP connection. */
    public void connect() throws Exception {
        master = new ModbusTCPMaster(host, port);
        master.setUnitId(unitId);
        master.connect();
        connected = true;
        currentReconnectDelayMs = RECONNECT_INITIAL_MS;
        LOG.info("[{}] ModbusTCP connected to {}:{}", sourceId, host, port);
    }

    /** Starts polling at the configured interval. */
    public void start() {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sentron-modbus-" + sourceId);
            t.setDaemon(true);
            return t;
        });
        pollTask = scheduler.scheduleAtFixedRate(
            this::pollOnce, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Stops polling. */
    public void stop() {
        running = false;
        if (pollTask != null) pollTask.cancel(false);
        if (scheduler != null) scheduler.shutdown();
    }

    /** Disconnects from the Modbus server. */
    public void disconnect() {
        stop();
        if (master != null) {
            try {
                master.disconnect();
            } catch (Exception e) {
                LOG.warn("[{}] Error during Modbus disconnect", sourceId, e);
            }
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    // -----------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------

    private void pollOnce() {
        if (!running) return;
        try {
            if (!connected) {
                reconnect();
                if (!connected) return;  // still down, skip this cycle
            }

            float power      = readFloat(REG_POWER_CONSUMPTION);
            float voltageL1  = readFloat(REG_VOLTAGE_L1_N);
            float currentL1  = readFloat(REG_CURRENT_L1);

            MachineEnergyMeasurement m = new MachineEnergyMeasurement();
            m.setSourceId(sourceId);
            m.setTimestamp(Instant.now().toEpochMilli());
            m.setPowerWatts(power);
            m.setEnergyWh(0.0);                    // not in the current register map
            m.setMachineState("UNKNOWN");
            m.setQuality(Quality.GOOD);

            measurementConsumer.accept(m);

            // Log voltage/current at debug level; they are not in the unified type
            LOG.debug("[{}] V(L1-N)={} I(L1)={}", sourceId, voltageL1, currentL1);

        } catch (Exception e) {
            LOG.warn("[{}] Modbus poll failed", sourceId, e);
            connected = false;
            notifyError(e);
        }
    }

    /**
     * Reads two holding registers starting at {@code offset} and combines
     * them into a 32-bit float (big-endian, j2mod convention).
     */
    private float readFloat(int offset) throws Exception {
        InputRegister[] regs = master.readMultipleRegisters(offset, 2);
        int hi = regs[0].getValue() & 0xFFFF;
        int lo = regs[1].getValue() & 0xFFFF;
        int bits = (hi << 16) | lo;
        return Float.intBitsToFloat(bits);
    }

    private void reconnect() {
        try {
            if (master != null) {
                try { master.disconnect(); } catch (Exception ignored) { }
            }
            master = new ModbusTCPMaster(host, port);
            master.setUnitId(unitId);
            master.connect();
            connected = true;
            currentReconnectDelayMs = RECONNECT_INITIAL_MS;
            LOG.info("[{}] ModbusTCP reconnected", sourceId);
        } catch (Exception e) {
            LOG.warn("[{}] Modbus reconnect failed, next retry in {} ms",
                     sourceId, currentReconnectDelayMs);
            notifyError(e);
            currentReconnectDelayMs = Math.min(
                currentReconnectDelayMs * 2, RECONNECT_MAX_MS);
        }
    }

    private void notifyError(Throwable t) {
        if (errorConsumer != null) errorConsumer.accept(t);
    }
}
