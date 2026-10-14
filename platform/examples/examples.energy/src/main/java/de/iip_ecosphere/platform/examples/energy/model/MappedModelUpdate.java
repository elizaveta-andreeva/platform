package de.iip_ecosphere.platform.examples.energy.model;

/**
 * A mapped update ready to be applied to the OPC UA server's address space.
 *
 * <p>Produced by the mapping layer (P3) and consumed by the
 * EnergyAggregatorServer (P3/P4 boundary). Carries the target node identifier,
 * the value, the source timestamp, and a status code.</p>
 */
public class MappedModelUpdate {

    private String targetNodeId;      // e.g. "ns=2;s=Machine.Energy.Power"
    private Object value;             // Double, Float, Integer, String, ...
    private long sourceTimestamp;     // epoch millis
    private int statusCode;           // OPC UA StatusCode (0 = Good)

    public MappedModelUpdate() { }

    public MappedModelUpdate(String targetNodeId, Object value,
                             long sourceTimestamp, int statusCode) {
        this.targetNodeId = targetNodeId;
        this.value = value;
        this.sourceTimestamp = sourceTimestamp;
        this.statusCode = statusCode;
    }

    // getters and setters ...

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public long getSourceTimestamp() { return sourceTimestamp; }
    public void setSourceTimestamp(long sourceTimestamp) { this.sourceTimestamp = sourceTimestamp; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
}
