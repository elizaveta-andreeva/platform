package de.iip_ecosphere.platform.examples.energy.aggregator.server;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.OpcUaQuality;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.milo.opcua.sdk.core.Reference;
/**
 * OPC UA address space for the Energy Aggregator.
 */
public class EnergyNamespace extends ManagedNamespaceWithLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(EnergyNamespace.class);

    public static final String NAMESPACE_URI = "urn:iip:energy:aggregator";

    public static final String POWER_TARGET  = "AcActivePowerTotal";
    public static final String ENERGY_TARGET = "AcActiveEnergyTotalImportLp";

    private static final String[] KNOWN_MACHINES = {
        "LatheNEF400",
        "MarkingLaser"
    };

    // OPC UA specification base status values.
    // Milo 0.6.4 does not expose these as named constants.
    private static final long STATUS_GOOD      = 0x00000000L;
    private static final long STATUS_UNCERTAIN = 0x40000000L;

    private final Map<String, UaVariableNode> nodesByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastAppliedTimestamps = new ConcurrentHashMap<>();
    private final SubscriptionModel subscriptionModel;

    public EnergyNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);
        this.subscriptionModel = new SubscriptionModel(server, this);
    }

    @Override public void onDataItemsCreated(List<DataItem> d)  { subscriptionModel.onDataItemsCreated(d); }
    @Override public void onDataItemsModified(List<DataItem> d) { subscriptionModel.onDataItemsModified(d); }
    @Override public void onDataItemsDeleted(List<DataItem> d)  { subscriptionModel.onDataItemsDeleted(d); }
    @Override public void onMonitoringModeChanged(List<MonitoredItem> m) { subscriptionModel.onMonitoringModeChanged(m); }

    // -----------------------------------------------------------------
    // Address space
    // -----------------------------------------------------------------

    public void initialize() {
        UaNodeContext ctx = getNodeContext();

        UaFolderNode root = folder(ctx, "EnergyAggregator", "Energy Aggregator");
     // Make the top folder visible under the server's standard Objects folder
     root.addReference(new Reference(
         root.getNodeId(),
         Identifiers.Organizes,
         Identifiers.ObjectsFolder.expanded(),
         false));
	     UaFolderNode machines = folder(ctx, "Machines", "Machines");
	     root.addOrganizes(machines);              // EnergyAggregator contains Machines
	     for (String machineId : KNOWN_MACHINES) {
	         UaFolderNode machineFolder = folder(ctx, machineId, machineId);
	         machines.addOrganizes(machineFolder); // Machines contains the machine
	
	            addVariable(machineFolder, machineId, POWER_TARGET, "Power");
	            addVariable(machineFolder, machineId, ENERGY_TARGET, "Energy");
	        }

        LOG.info("EnergyNamespace initialized with {} variables", nodesByKey.size());
    }

    private UaFolderNode folder(UaNodeContext ctx, String id, String name) {
        NodeId nodeId = newNodeId(id);
        UaFolderNode f = new UaFolderNode(
            ctx,
            nodeId,
            newQualifiedName(id),
            LocalizedText.english(name)
        );
        getNodeManager().addNode(f);
        return f;
    }

    private void addVariable(UaFolderNode parent, String deviceId, String target, String displayName) {
        UaNodeContext ctx = getNodeContext();
        String nodeKey = deviceId + "." + target;
        NodeId nodeId = newNodeId(nodeKey);

        UaVariableNode node = UaVariableNode.builder(ctx)
            .setNodeId(nodeId)
            .setBrowseName(newQualifiedName(nodeKey))
            .setDisplayName(LocalizedText.english(displayName))
            .setDataType(Identifiers.Double)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_ONLY))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_ONLY))
            .setMinimumSamplingInterval(0.0)
            .build();

        // Initial state: no value yet. Clients see Bad_WaitingForInitialData
        // until a real measurement arrives — no fake 0.0 / GOOD seed.
        node.setValue(new DataValue(
            new Variant(0.0),
            new StatusCode(StatusCodes.Bad_WaitingForInitialData),
            DateTime.MIN_VALUE, DateTime.now()));

        getNodeManager().addNode(node);
        parent.addOrganizes(node);

        nodesByKey.put(nodeKey, node);
    }

    // -----------------------------------------------------------------
    // Update application — atomic per node
    // -----------------------------------------------------------------

    public void applyUpdate(MappedModelUpdate update) throws UnknownTargetException {
        if (update == null) throw new IllegalArgumentException("update must not be null");

        String nodeKey = update.deviceId() + "." + update.target();
        UaVariableNode node = nodesByKey.get(nodeKey);
        if (node == null) {
            throw new UnknownTargetException(nodeKey);
        }

        Instant incoming = update.timestamp();

        lastAppliedTimestamps.compute(nodeKey, (key, previous) -> {
            if (previous == null || incoming.isAfter(previous)) {
                DataValue value = new DataValue(
                    new Variant(update.value()),
                    toStatusCode(update.quality()),
                    new DateTime(incoming.toEpochMilli()),
                    DateTime.now());
                node.setValue(value);
                LOG.debug("Applied '{}' = {} (ts={})", key, update.value(), incoming);
                return incoming;
            }
            LOG.warn("Rejecting older update for '{}': {} <= {}", key, incoming, previous);
            return previous;
        });
    }

    public int size() {
        return nodesByKey.size();
    }

    // -----------------------------------------------------------------
    // Quality translation
    // -----------------------------------------------------------------

    private static StatusCode toStatusCode(OpcUaQuality q) {
        if (q == null) {
            return new StatusCode(StatusCodes.Bad_NoData);
        }
        return switch (q) {
            case GOOD              -> new StatusCode(STATUS_GOOD);
            case UNCERTAIN         -> new StatusCode(STATUS_UNCERTAIN);
            case BAD_NOT_CONNECTED -> new StatusCode(StatusCodes.Bad_NotConnected);
            case BAD_INVALID_DATA  -> new StatusCode(StatusCodes.Bad_InvalidArgument);
        };
    }

    // -----------------------------------------------------------------
    // Exception
    // -----------------------------------------------------------------

    public static class UnknownTargetException extends Exception {
        private final String target;
        public UnknownTargetException(String target) {
            super("Unknown target: " + target);
            this.target = target;
        }
        public String getTarget() { return target; }
    }
}
