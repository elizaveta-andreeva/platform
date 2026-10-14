package de.iip_ecosphere.platform.examples.energy.model;

/**
 * Unified internal representation of a single energy measurement.
 *
 * <p>This is the P3/P4 handover contract. Every source ingestor
 * (MQTT, ModbusTCP, OPC UA) must produce this type.</p>
 *
 * <p>PROPOSED — fields are subject to alignment with P4.</p>
 */
public class MachineEnergyMeasurement {

    private String sourceId;
    private long timestamp;          // epoch millis
    private double powerWatts;
    private double energyWh;
    private String machineState;     // RUNNING, IDLE, OFF, UNKNOWN
    private Quality quality;

    // getters and setters ...

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getPowerWatts() { return powerWatts; }
    public void setPowerWatts(double powerWatts) { this.powerWatts = powerWatts; }

    public double getEnergyWh() { return energyWh; }
    public void setEnergyWh(double energyWh) { this.energyWh = energyWh; }

    public String getMachineState() { return machineState; }
    public void setMachineState(String machineState) { this.machineState = machineState; }

    public Quality getQuality() { return quality; }
    public void setQuality(Quality quality) { this.quality = quality; }

    @Override
    public String toString() {
        return "MachineEnergyMeasurement{" +
               "sourceId='" + sourceId + '\'' +
               ", timestamp=" + timestamp +
               ", powerWatts=" + powerWatts +
               ", energyWh=" + energyWh +
               ", machineState='" + machineState + '\'' +
               ", quality=" + quality +
               '}';
    }
}
