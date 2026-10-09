package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.airline.Airline;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import java.util.Optional;

public interface AirlineRepository {

    Optional<Airline> findByCode(AirlineCode code);
}
