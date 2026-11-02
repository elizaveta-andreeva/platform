package de.iip_ecosphere.platform.examples.energy.aggregator.server;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Thin OPC UA server for the Energy Aggregator.
 * 
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>{@link #start()} — bind endpoint, initialize namespace, then accept updates.</li>
 *   <li>{@link #applyUpdate(MappedModelUpdate)} — delegate to {@link EnergyNamespace}.</li>
 *   <li>{@link #stop()} — shut down cleanly; no further updates accepted.</li>
 * </ol>
 */
public class EnergyAggregatorServer {

    private static final Logger LOG = LoggerFactory.getLogger(EnergyAggregatorServer.class);

    private final String host;
    private final int port;
    private final String serverName;

    private OpcUaServer server;
    private EnergyNamespace namespace;
    private volatile boolean running = false;
    private volatile boolean acceptingUpdates = false;

    public EnergyAggregatorServer(String host, int port, String serverName) {
        this.host = host;
        this.port = port;
        this.serverName = serverName;
    }

    // -----------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------

    /**
     * Starts the server. Order matters: initialize namespace, start server, then accept updates.
     */
    public void start() throws Exception {
        // 1. Build the endpoint configuration
        EndpointConfiguration endpoint = EndpointConfiguration.newBuilder()
            .setBindAddress(host)
            .setBindPort(port)
            .setPath("/energy-aggregator")
            .setSecurityPolicy(SecurityPolicy.None)
            .setSecurityMode(MessageSecurityMode.None)
            .addTokenPolicies(new UserTokenPolicy(
                "anonymous",
                UserTokenType.Anonymous,
                null, null, null))
            .build();

        // 2. Build the server configuration
        // Note: setEndpoints requires a Set<EndpointConfiguration>
        OpcUaServerConfig config = OpcUaServerConfig.builder()
            .setApplicationUri("urn:iip:energy:aggregator:" + serverName)
            .setApplicationName(LocalizedText.english("Energy Aggregator"))
            .setEndpoints(Collections.singleton(endpoint))
            .setIdentityValidator(AnonymousIdentityValidator.INSTANCE) // Milo 0.6.4 style
            .build();

        // 3. Create and start the server
        server = new OpcUaServer(config);

        // 4. Initialize the namespace BEFORE starting the server
        namespace = new EnergyNamespace(server);
        namespace.initialize();

        // 5. Start
        server.startup().get(10, TimeUnit.SECONDS);
        running = true;
        acceptingUpdates = true;

        LOG.info("EnergyAggregatorServer started at {}", getEndpointUrl());
    }

    /** Stops the server and prevents further updates. */
    public void stop() {
        acceptingUpdates = false;
        if (server != null) {
            try {
                server.shutdown().get(5, TimeUnit.SECONDS);
                LOG.info("EnergyAggregatorServer stopped");
            } catch (Exception e) {
                LOG.warn("Error during server shutdown", e);
            }
        }
        running = false;
    }

    // -----------------------------------------------------------------
    // Update application
    // -----------------------------------------------------------------

    /**
     * Applies a mapped update. Rejects updates after {@link #stop()} or before {@link #start()}.
     */
    public void applyUpdate(MappedModelUpdate update) throws EnergyNamespace.UnknownTargetException {
        if (!acceptingUpdates) {
            throw new IllegalStateException("Server is not accepting updates");
        }
        namespace.applyUpdate(update);
    }

    // -----------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------

    public boolean isRunning() { return running; }
    public String getEndpointUrl() {
        return "opc.tcp://" + host + ":" + port + "/energy-aggregator";
    }
    public EnergyNamespace getNamespace() { return namespace; }
}
