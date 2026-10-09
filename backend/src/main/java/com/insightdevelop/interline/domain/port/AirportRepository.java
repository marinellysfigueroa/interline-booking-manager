package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.airport.Airport;
import java.util.List;

public interface AirportRepository {

    /** Busca por código, ciudad o nombre (sin distinguir mayúsculas ni tildes). */
    List<Airport> search(String text, int limit);
}
