package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.domain.airport.Airport;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.port.AirportRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class AirportRepositoryAdapter implements AirportRepository {

    private final AirportPanacheRepository panache;

    AirportRepositoryAdapter(AirportPanacheRepository panache) {
        this.panache = panache;
    }

    /**
     * Coincidencia por código exacto primero y después por texto normalizado. El texto del
     * usuario se normaliza igual que {@code search_key}: minúsculas y sin tildes.
     */
    @Override
    public List<Airport> search(String text, int limit) {
        String normalized = normalize(text);
        String like = "%" + normalized.replace("%", "").replace("_", "") + "%";
        return panache.find("""
                        searchKey like :like
                        order by case when lower(code) = :exact then 0 else 1 end, city, code""",
                Parameters.with("like", like).and("exact", normalized))
                .page(Page.ofSize(limit))
                .list().stream()
                .map(e -> new Airport(new AirportCode(e.code), e.name, e.city, e.countryCode, ZoneId.of(e.timeZone)))
                .toList();
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

}
