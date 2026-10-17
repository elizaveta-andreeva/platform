package de.iip_ecosphere.platform.examples.energy.server;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;
import de.iip_ecosphere.platform.examples.energy.aggregator.model.OpcUaQuality;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.enumerated.AccessLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OPC UA address space for the Energy Aggregator.
 *
 * <p>Targets are the OPC 40001-4 variable names produced by P4's
 * {@code OpcEnergyModelMapper}: {@code AcActivePowerTotal} and
 * {@code AcActiveEnergyTotalImportLp}.</p>
 *
 * <p>The server registers both the flat target name (as P4's mapper emits today)
 * and a device-qualified alias (e.g. {@code ShellyPro3EM.AcActivePowerTotal}).
 * The alias is what the mapper should emit once it includes the device ID —
 * see the issue raised with P4. Until then, the flat names work for a single
 * device under test.</p>
 */
public class EnergyNamespace extends ManagedNamespaceWithLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(EnergyNamespace.class);

    public static final String NAMESPACE_URI = "urn:iip:energy:aggregator";

    // The exact target names produced by P4's OpcEnergyModelMapper
    public static final String POWER_TARGET   = "AcActivePowerTotal";
    public static final String ENERGY_TARGET  = "AcActiveEnergyTotalImportLp";

    // Devices the server knows about. P4's mapper will eventually prefix targets with these.
    private static final String[] KNOWN_DEVICES = {
        "ShellyPro3EM", "SentronPAC", "LatheNEF400", "MarkingLaser"
    };

    private final Map<String, UaVariableNode> nodesByKey = new ConcurrentHashMap<>();
    private final Map<UaVariableNode, AtomicLong> lastSourceTimestampMs = new ConcurrentHashMap<>();
    private final SubscriptionModel subscriptionModel;

    public EnergyNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);
        this.subscriptionModel = new SubscriptionModel(server, this);
    }

    @Override public void onDataItemsCreated(List<DataItem> d)   { subscriptionModel.onDataItemsCreated(d); }
    @Override public void onDataItemsModified(List<DataItem> d)  { subscriptionModel.onDataItemsModified(d); }
    @Override public void onDataItemsDeleted(List<DataItem> d)   { subscriptionModel.onDataItemsDeleted(d); }
    @Override public void onMonitoringModeChanged(List<MonitoredItem> m) { subscriptionModel.onMonitoringModeChanged(m); }

    // -----------------------------------------------------------------
    // Address space
    // -----------------------------------------------------------------

    public void initialize() {
        UaNodeContext ctx = getNodeContext();

        UaFolderNode root = folder(ctx, "EnergyAggregator", "Energy Aggregator");
        root.addReference(new Reference(root.getNodeId(), Identifiers.Organizes,
            Identifiers.ObjectsFolder.expanded(), false));

        UaFolderNode machines = folder(ctx, "Machines", "Machines");
        machines.addReference(new Reference(machines.getNodeId(), Identifiers.Organizes,
            root.getNodeId().expanded(), false));

        // Per-device folders, each with Power + Energy variables
        for (String device : KNOWN_DEVICES) {
            UaFolderNode deviceFolder = folder(ctx, device, device);
            deviceFolder.addReference(new Reference(deviceFolder.getNodeId(),
                Identifiers.Organizes, machines.getNodeId().expanded(), false));

            addVariable(deviceFolder, device + "." + POWER_TARGET,
                "Power", POWER_TARGET, device);
            addVariable(deviceFolder, device + "." + ENERGY_TARGET,
                "Energy", ENERGY_TARGET, device);
        }

        LOG.info("EnergyNamespace initialized with {} variables", nodesByKey.size());
    }

    private UaFolderNode folder(UaNodeContext ctx, String id, String name) {
        UaFolderNode f = UaFolderNode.builder(ctx)
            .setNodeId(newNodeId(id))
            .setBrowseName(newQualifiedName(id))
            .setDisplayName(LocalizedText.english(name))
            .build();
        getNodeManager().addNode(f);
        return f;
    }

    private void addVariable(UaFolderNode parent, String key, String displayName,
                             String flatAlias, String deviceId) {
        UaNodeContext ctx = getNodeContext();

        UaVariableNode node = UaVariableNode.builder(ctx)
            .setNodeId(newNodeId(key))
            .setBrowseName(newQualifiedName(key))
            .setDisplayName(LocalizedText.english(displayName))
            .setDataType(Identifiers.Double)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .setAccessLevel(AccessLevel.CurrentRead)
            .setUserAccessLevel(AccessLevel.CurrentRead)
            .setMinimumSamplingInterval(0.0)
            .build();

        node.setValue(new DataValue(
            new Variant(0.0),
            new StatusCode(StatusCodes.BadWaitingForInitialData),
            DateTime.MIN_VALUE, DateTime.now()));

        getNodeManager().addNode(node);
        node.addReference(new Reference(node.getNodeId(), Identifiers.Organizes,
            parent.getNodeId().expanded(), false));

        nodesByKey.put(key, node);
        lastSourceTimestampMs.put(node, new AtomicLong(0));

        // Register the flat alias too — but only one node per flat name.
        // The first device to claim it wins for the flat lookup; the qualified
        // key is always unambiguous.
        nodesByKey.putIfAbsent(flatAlias, node);
    }

    // -----------------------------------------------------------------
    // Update application
    // -----------------------------------------------------------------

    public void applyUpdate(MappedModelUpdate update) throws UnknownTargetException {
        if (update == null) throw new IllegalArgumentException("update must not be null");

        UaVariableNode node = nodesByKey.get(update.target());
        if (node == null) throw new UnknownTargetException(update.target());

        Instant sourceTime = update.timestamp();
        long sourceMs = sourceTime.toEpochMilli();
        AtomicLong last = lastSourceTimestampMs.get(node);
        long previous = last.get();
        if (previous != 0 && sourceMs < previous) {
            LOG.warn("Rejecting older update for '{}': {} < {}",
                update.target(), sourceMs, previous);
            return;
        }
        last.set(sourceMs);

        DataValue value = new DataValue(
            new Variant(update.value()),
            toStatusCode(update.quality()),
            new DateTime(sourceMs),
            DateTime.now());
        node.setValue(value);

        LOG.debug("Applied '{}' = {}", update.target(), update.value());
    }

    public int size() { return nodesByKey.size(); }

    // -----------------------------------------------------------------
    // Quality translation
    // -----------------------------------------------------------------

    private static StatusCode toStatusCode(OpcUaQuality q) {
        if (q == null) return new StatusCode(StatusCodes.BadNoData);
        return switch (q) {
            case GOOD              -> new StatusCode(StatusCodes.Good);
            case UNCERTAIN         -> new StatusCode(StatusCodes.Uncertain);
            case BAD_NOT_CONNECTED -> new StatusCode(StatusCodes.BadNotConnected);
            case BAD_INVALID_DATA  -> new StatusCode(StatusCodes.BadInvalidArgument);
        };
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private UaNodeId newNodeId(String s)       { return new UaNodeId(getNamespaceIndex(), s); }
    private QualifiedName newQualifiedName(String n) { return new QualifiedName(getNamespaceIndex(), n); }

    public static class UnknownTargetException extends Exception {
        private final String target;
        public UnknownTargetException(String target) {
            super("Unknown target: " + target);
            this.target = target;
        }
        public String getTarget() { return target; }
    }
}
