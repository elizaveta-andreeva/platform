package de.iip_ecosphere.platform.examples.energy.aggregator.source;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MachineEnergyMeasurement;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementQuality;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.MeasurementType;

import net.wimpi.modbus.io.ModbusTCPTransaction;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadInputRegistersResponse;
import net.wimpi.modbus.net.TCPMasterConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ModbusTCP ingestor for the Siemens Sentron PAC.
 */
public class SentronModbusIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(SentronModbusIngestor.class);

    private final String sourceId;
    private final String deviceId;
    private final String host;
    private final int port;
    private final int unitId;
    private final int intervalMs;
    private final Consumer<MachineEnergyMeasurement> consumer;
    private final Consumer<Throwable> errorConsumer;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private volatile boolean running = false;
    private volatile boolean connected = false;

    private static final int REG_POWER_CONSUMPTION = 25;

    public SentronModbusIngestor(String sourceId,
                                 String deviceId,
                                 String host,
                                 int port,
                                 int unitId,
                                 int intervalMs,
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

    /** Verifies connectivity with a single probe connection. */
    public void connect() throws Exception {
        TCPMasterConnection probe = openConnection();
        try {
            connected = true;
            LOG.info("[{}] ModbusTCP reachable at {}:{}", deviceId, host, port);
        } finally {
            probe.close();
        }
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
        if (task != null) {
            task.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public void disconnect() {
        stop();
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    private void pollOnce() {
        if (!running) {
            return;
        }
        TCPMasterConnection con = null;
        try {
            con = openConnection();
            connected = true;

            ModbusTCPTransaction trans = new ModbusTCPTransaction(con);
            ReadInputRegistersRequest req = new ReadInputRegistersRequest(REG_POWER_CONSUMPTION, 2);
            req.setUnitID(unitId);
            trans.setRequest(req);
            trans.execute();

            ReadInputRegistersResponse res = (ReadInputRegistersResponse) trans.getResponse();

            int high = res.getRegisterValue(0);
            int low = res.getRegisterValue(1);
            float power = Float.intBitsToFloat((high << 16) | (low & 0xFFFF));

            Instant now = Instant.now();
            consumer.accept(new MachineEnergyMeasurement(
                sourceId, deviceId,
                MeasurementType.CURRENT_POWER, power, "W",
                now, MeasurementQuality.VALID));

        } catch (Exception e) {
            connected = false;
            LOG.warn("[{}] Modbus poll failed", deviceId, e);
            emitBadQuality();
            notifyError(e);
        } finally {
            if (con != null) {
                con.close();
            }
        }
    }

    private TCPMasterConnection openConnection() throws Exception {
        TCPMasterConnection con = new TCPMasterConnection(InetAddress.getByName(host));
        con.setPort(port);
        con.connect();
        return con;
    }

    private void emitBadQuality() {
        consumer.accept(new MachineEnergyMeasurement(
            sourceId, deviceId,
            MeasurementType.CURRENT_POWER, 0.0, "W",
            Instant.now(), MeasurementQuality.UNAVAILABLE));
    }

    private void notifyError(Throwable t) {
        if (errorConsumer != null) {
            errorConsumer.accept(t);
        }
    }
}
