package test.de.iip_ecosphere.platform.examples.energy.aggregator.server;

import de.iip_ecosphere.platform.examples.energy.aggregator.model.OpcUaQuality;
import de.iip_ecosphere.platform.examples.energy.aggregator.server.EnergyNamespace;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnergyNamespaceTest {

    @Test
    void qualityMapping() {
        assertEquals(StatusCodes.Good,
            EnergyNamespace.toStatusCode(OpcUaQuality.GOOD).getValue());
        assertEquals(StatusCodes.Uncertain,
            EnergyNamespace.toStatusCode(OpcUaQuality.UNCERTAIN).getValue());
        assertEquals(StatusCodes.Bad_NotConnected,
            EnergyNamespace.toStatusCode(OpcUaQuality.BAD_NOT_CONNECTED).getValue());
        assertEquals(StatusCodes.Bad_InvalidArgument,
            EnergyNamespace.toStatusCode(OpcUaQuality.BAD_INVALID_DATA).getValue());
        assertEquals(StatusCodes.Bad_NoData,
            EnergyNamespace.toStatusCode(null).getValue());
    }
}
