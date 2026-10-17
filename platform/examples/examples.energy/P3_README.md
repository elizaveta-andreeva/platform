# P3 Source Ingestion — Integration Notes for P4

## What This Provides

Source ingestion for four protocols, converting each into a unified
`MachineEnergyMeasurement`, and an OPC UA server (`EnergyAggregatorServer`)
that exposes the values.

## Handover Boundary

    MQTT / Modbus / OPC UA
            ↓
    MachineEnergyMeasurement     ← P3 owns this type (proposed contract)
            ↓
    MappedModelUpdate            ← P3 owns this type
            ↓
    EnergyAggregatorServer       ← P3 owns the server
            ↓
    OPC UA namespace (browsable) ← P4 will replace / extend this with the
                                    full OPC 40001-4 model

## Key Classes for P4

| Class | Package | Purpose |
|-------|---------|---------|
| `MachineEnergyMeasurement` | `...energy.model` | P3/P4 handover contract (PROPOSED — fields TBD) |
| `MappedModelUpdate` | `...energy.model` | Server update message |
| `EnergyAggregatorServer` | `...energy.server` | OPC UA server lifecycle |
| `EnergyNamespace` | `...energy.server` | Address-space builder — P4 replaces this |

## Integration Points for P4

1. **Replace `EnergyNamespace.initialize()`** with the full OPC 40001-4
   address space. The current implementation creates a minimal
   `Machines/<source>/<var>` tree as a placeholder.

2. **Replace `EnergyNamespace.applyUpdate()`** if the mapping from
   `MappedModelUpdate` to OPC 40001-4 nodes needs a different scheme.

3. **Agree on `MachineEnergyMeasurement` fields** — the current set is:
   - `sourceId` (String)
   - `timestamp` (long, epoch ms)
   - `powerWatts` (double)
   - `energyWh` (double)
   - `machineState` (String: RUNNING / IDLE / OFF / UNKNOWN)
   - `quality` (enum: GOOD / BAD / UNCERTAIN / STALE)

## How to Run the Server Standalone (Smoke Test)

    mvn -U compile
    mvn exec:java -Dexec.mainClass=\
      de.iip_ecosphere.platform.examples.energy.server.ServerSmokeTest

Then connect with UaExpert or `opcua-client` to:
    opc.tcp://127.0.0.1:4840/energy-aggregator

Browse: `Objects → EnergyAggregator → Machines → ShellyPro3EM → Power`

## Dependencies Added

- `org.eclipse.milo:sdk-server:0.6.4`
- `org.eclipse.milo:sdk-core:0.6.4`
- `org.eclipse.paho:org.eclipse.paho.client.mqttv3:<platform version>`
- Modbus client (pending — see pom.xml)

## Not Included (Out of Scope for P3)

- Aggregator logic (P4)
- OPC 40001-4 information model (P4)
- Persistent storage (P4)
- Real device validation (later phase)

## Status

- Compiles: yes / no (update this)
- Server smoke test: passing / pending
- MQTT ingestion: passing / pending
- Modbus ingestion: passing / pending

## Contact

P3 
