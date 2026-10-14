package de.iip_ecosphere.platform.examples.energy.server;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.iip_ecosphere.platform.examples.energy.model.MappedModelUpdate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPC 40001-4-aligned address space for the Energy Aggregator.
 *
 * <p>Structure:</p>
 * <pre>
 * Objects
 *   └── EnergyAggregator
 *         └── Machines
 *               ├── ShellyPro3EM
 *               │     ├── Power
 *               │     ├── Energy
 *               │     └── MachineState
 *               ├── SentronPAC
 *               ├── LatheNEF400
 *               └── MarkingLaser
 * </pre>
 */
public class EnergyNamespace extends ManagedNamespaceWithLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(EnergyNamespace.class);

    public static final String NAMESPACE_URI = "urn:iip:energy:aggregator";

    private final Map<String, UaVariableNode> variableNodes = new ConcurrentHashMap<>();
    private final SubscriptionModel subscriptionModel;

    public EnergyNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);
        this.subscriptionModel = new SubscriptionModel(server, this);
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

    // -----------------------------------------------------------------
    // Address space initialization
    // -----------------------------------------------------------------

    /** Builds the full folder/variable tree. */
    public void initialize() {
        UaNodeContext context = getNodeContext();

        // Root folder: EnergyAggregator
        UaFolderNode root = UaFolderNode.builder(context)
            .setNodeId(newNodeId("EnergyAggregator"))
            .setBrowseName(newQualifiedName("EnergyAggregator"))
            .setDisplayName(LocalizedText.english("Energy Aggregator"))
            .build();
        getNodeManager().addNode(root);
        root.addReference(new Reference(
            root.getNodeId(),
            Identifiers.Organizes,
            Identifiers.ObjectsFolder.expanded(),
            false
        ));

        // Machines folder
        UaFolderNode machines = UaFolderNode.builder(context)
            .setNodeId(newNodeId("Machines"))
            .setBrowseName(newQualifiedName("Machines"))
            .setDisplayName(LocalizedText.english("Machines"))
            .build();
        getNodeManager().addNode(machines);
        machines.addReference(new Reference(
            machines.getNodeId(),
            Identifiers.Organizes,
            root.getNodeId().expanded(),
            false
        ));

        // Per-source folders + variable nodes
        addSource(machines, "ShellyPro3EM");
        addSource(machines, "SentronPAC");
        addSource(machines, "LatheNEF400");
        addSource(machines, "MarkingLaser");

        LOG.info("EnergyNamespace initialized with {} variable nodes", variableNodes.size());
    }

    private void addSource(UaFolderNode parent, String sourceName) {
        UaNodeContext context = getNodeContext();

        UaFolderNode sourceFolder = UaFolderNode.builder(context)
            .setNodeId(newNodeId(sourceName))
            .setBrowseName(newQualifiedName(sourceName))
            .setDisplayName(LocalizedText.english(sourceName))
            .build();
        getNodeManager().addNode(sourceFolder);
        sourceFolder.addReference(new Reference(
            sourceFolder.getNodeId(),
            Identifiers.Organizes,
            parent.getNodeId().expanded(),
            false
        ));

        // Standard variables per source
        addVariable(sourceFolder, sourceName, "Power",     Identifiers.Double);
        addVariable(sourceFolder, sourceName, "Energy",    Identifiers.Double);
        addVariable(sourceFolder, sourceName, "MachineState", Identifiers.String);
    }

    private void addVariable(UaFolderNode parent, String sourceName, String varName, NodeId dataType) {
        UaNodeContext context = getNodeContext();
        String key = sourceName + "." + varName;

        UaVariableNode node = UaVariableNode.builder(context)
            .setNodeId(newNodeId(key))
            .setBrowseName(newQualifiedName(key))
            .setDisplayName(LocalizedText.english(varName))
            .setDataType(dataType)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .setAccessLevel(org.eclipse.milo.opcua.stack.core.types.enumerated
                .AccessLevel.toValue(org.eclipse.milo.opcua.stack.core.types.enumerated
                    .AccessLevel.CurrentRead))
            .setUserAccessLevel(org.eclipse.milo.opcua.stack.core.types.enumerated
                .AccessLevel.toValue(org.eclipse.milo.opcua.stack.core.types.enumerated
                    .AccessLevel.CurrentRead))
            .build();

        node.setValue(new DataValue(new Variant(initialValue(dataType)), StatusCode.GOOD, DateTime.now()));

        getNodeManager().addNode(node);
        node.addReference(new Reference(
            node.getNodeId(),
            Identifiers.Organizes,
            parent.getNodeId().expanded(),
            false
        ));

        variableNodes.put(key, node);
    }

    private Object initialValue(NodeId dataType) {
        if (Identifiers.Double.equals(dataType)) return 0.0;
        if (Identifiers.Float.equals(dataType))  return 0.0f;
        if (Identifiers.String.equals(dataType)) return "";
        return 0;
    }

    // -----------------------------------------------------------------
    // Update application
    // -----------------------------------------------------------------

    /**
     * Applies a mapped update to the address space.
     *
     * <p>Looks up the target node by its {@code targetNodeId} (in the form
     * {@code "SourceName.VariableName"}), sets the value, the source
     * timestamp, and the StatusCode, then triggers a subscription
     * notification.</p>
     */
    public void applyUpdate(MappedModelUpdate update) {
        UaVariableNode node = variableNodes.get(update.getTargetNodeId());
        if (node == null) {
            LOG.warn("No node found for target '{}'", update.getTargetNodeId());
            return;
        }

        DateTime sourceTime = new DateTime(update.getSourceTimestamp());
        StatusCode status = new StatusCode(update.getStatusCode());
        Variant value = new Variant(update.getValue());

        DataValue dataValue = new DataValue(value, status, sourceTime);
        node.setValue(dataValue);
        node.getFilterChain().forEach(filter -> { /* no-op */ });

        LOG.debug("Applied update: {} = {} @ {} (status {})",
            update.getTargetNodeId(), update.getValue(),
            update.getSourceTimestamp(), update.getStatusCode());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private UaNodeId newNodeId(String suffix) {
        return new UaNodeId(getNamespaceIndex(), suffix);
    }

    private QualifiedName newQualifiedName(String name) {
        return new QualifiedName(getNamespaceIndex(), name);
    }
}
