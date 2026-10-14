package de.iip_ecosphere.platform.examples.energy.server;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.iip_ecosphere.platform.examples.energy.model.MappedModelUpdate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OPC UA server that exposes a namespace modelled after OPC 40001-4.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #start()} — binds the endpoint, initializes the namespace.</li>
 *   <li>{@link #applyUpdate(MappedModelUpdate)} — writes a value to a target node.</li>
 *   <li>{@link #stop()} — shuts the server down.</li>
 * </ol>
 *
 * <p>Values, timestamps, and StatusCodes are exposed on each variable node
 * so that any external OPC UA client can browse/read them.</p>
 */
public class EnergyAggregatorServer {

    private static final Logger LOG = LoggerFactory.getLogger(EnergyAggregatorServer.class);

    private final String host;
    private final int port;
    private final String serverName;

    private OpcUaServer server;
    private EnergyNamespace namespace;
    private ScheduledExecutorService lifecycleExecutor;

    public EnergyAggregatorServer(String host, int port, String serverName) {
        this.host = host;
        this.port = port;
        this.serverName = serverName;
    }

    // -----------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------

    /** Starts the OPC UA server and initializes the address space. */
    public void start() throws Exception {
        EndpointConfiguration endpoint = EndpointConfiguration.newBuilder()
            .setBindAddress(host)
            .setBindPort(port)
            .setPath("/energy-aggregator")
            .setSecurityPolicy(org.eclipse.milo.opcua.stack.core.SecurityPolicy.None)
            .setSecurityMode(org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode.None)
            .addTokenPolicies(org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy.anonymous())
            .build();

        OpcUaServerConfig config = OpcUaServerConfig.builder()
            .setApplicationUri("urn:iip:energy:aggregator:" + serverName)
            .setApplicationName(LocalizedText.english("Energy Aggregator"))
            .setEndpoints(endpoint)
            .setIdentityValidator(new AnonymousProvider())
            .build();

        server = new OpcUaServer(config);

        // Initialize the address space (OPC 40001-4-aligned namespace)
        namespace = new EnergyNamespace(server);
        namespace.initialize();

        server.startup().get();
        LOG.info("EnergyAggregatorServer started at opc.tcp://{}:{}", host, port);
    }

    /** Stops the OPC UA server gracefully. */
    public void stop() {
        if (server != null) {
            try {
                server.shutdown().get(5, TimeUnit.SECONDS);
                LOG.info("EnergyAggregatorServer stopped");
            } catch (Exception e) {
                LOG.warn("Error during server shutdown", e);
            }
        }
    }

    // -----------------------------------------------------------------
    // Update application
    // -----------------------------------------------------------------

    /**
     * Applies a mapped update to the target node in the address space.
     *
     * <p>Writes the value, sets the source timestamp, and sets the StatusCode
     * on the variable node. Any subscribed client receives the change.</p>
     */
    public void applyUpdate(MappedModelUpdate update) {
        if (namespace == null) {
            LOG.warn("applyUpdate called before start()");
            return;
        }
        namespace.applyUpdate(update);
    }

    // -----------------------------------------------------------------
    // Accessors for testing
    // -----------------------------------------------------------------

    public OpcUaServer getServer() { return server; }
    public EnergyNamespace getNamespace() { return namespace; }
    public String getEndpointUrl() { return "opc.tcp://" + host + ":" + port + "/energy-aggregator"; }
}
