package de.iip_ecosphere.platform.examples.energy.server;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.MappedModelUpdate;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>This class does not ingest, normalize, or semantically map any data. It only
 * exposes whatever {@link MappedModelUpdate}s it is given.</p>
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
     * Starts the server. Order matters (P4, section 10):
     * <ol>
     *   <li>Initialize the namespace.</li>
     *   <li>Start the server.</li>
     *   <li>Only then accept updates.</li>
     * </ol>
     */
    public void start() throws Exception {
        EndpointConfiguration endpoint = EndpointConfiguration.newBuilder()
            .setBindAddress(host)
            .setBindPort(port)
            .setPath("/energy-aggregator")
            .setSecurityPolicy(SecurityPolicy.None)
            .setSecurityMode(MessageSecurityMode.None)
            .addTokenPolicies(UserTokenPolicy.anonymous())
            .build();

        OpcUaServerConfig config = OpcUaServerConfig.builder()
            .setApplicationUri("urn:iip:energy:aggregator:" + serverName)
            .setApplicationName(LocalizedText.english("Energy Aggregator"))
            .setEndpoints(endpoint)
            .setIdentityValidator(new AnonymousProvider())
            .build();

        server = new OpcUaServer(config);

        namespace = new EnergyNamespace(server);
        namespace.initialize();

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
     * Applies a mapped update. Rejects updates after {@link #stop()} or before
     * {@link #start()} — there is no half-initialized window in which writes are accepted.
     *
     * @throws IllegalStateException if the server is not accepting updates
     * @throws EnergyNamespace.UnknownTargetException if the target is not registered
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
