package com.insightdevelop.interline.infrastructure.gateway.simulated;

import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.port.SegmentInventoryGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Inventario de aerolíneas simulado en memoria, idempotente por segmento. El estado se
 * pierde al reiniciar y no se comparte entre instancias: es un sustituto de demostración
 * de los sistemas de reservas de las operadoras.
 */
@ApplicationScoped
public class SimulatedSegmentInventoryGateway implements SegmentInventoryGateway {

    private static final Logger LOG = Logger.getLogger(SimulatedSegmentInventoryGateway.class);

    private record State(SegmentStatus status, int checks) {
    }

    private final Map<UUID, State> segments = new ConcurrentHashMap<>();
    private final SimulatedGatewaysConfig.Inventory config;

    SimulatedSegmentInventoryGateway(SimulatedGatewaysConfig config) {
        this.config = config.inventory();
    }

    @Override
    public SegmentStatus sell(BookingLocator locator, Segment segment, int seats) {
        String carrier = segment.operatingCarrier().value();
        State state = segments.computeIfAbsent(segment.id().value(), id -> new State(
                has(config.rejectedCarriers(), carrier) ? SegmentStatus.XX
                        : has(config.unconfirmedCarriers(), carrier) || has(config.neverConfirmCarriers(), carrier)
                                ? SegmentStatus.UC
                                : SegmentStatus.HK, 0));
        LOG.debugf("[%s] venta %s x%d -> %s", locator, segment.label(), seats, state.status());
        return state.status();
    }

    @Override
    public SegmentStatus checkStatus(BookingLocator locator, Segment segment) {
        State state = segments.compute(segment.id().value(), (id, current) -> {
            State base = current == null ? new State(SegmentStatus.UC, 0) : current;
            if (base.status() != SegmentStatus.UC) {
                return base;
            }
            int checks = base.checks() + 1;
            boolean confirms = !has(config.neverConfirmCarriers(), segment.operatingCarrier().value())
                    && checks >= config.confirmAfterChecks();
            return new State(confirms ? SegmentStatus.HK : SegmentStatus.UC, checks);
        });
        return state.status();
    }

    @Override
    public void cancel(BookingLocator locator, Segment segment) {
        segments.put(segment.id().value(), new State(SegmentStatus.XX, 0));
        LOG.debugf("[%s] cancelado %s", locator, segment.label());
    }

    private static boolean has(java.util.Optional<Set<String>> carriers, String carrier) {
        return carriers.map(set -> set.contains(carrier)).orElse(false);
    }
}
