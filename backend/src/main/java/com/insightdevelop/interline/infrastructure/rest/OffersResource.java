package com.insightdevelop.interline.infrastructure.rest;

import com.insightdevelop.interline.application.offer.SearchOffersUseCase;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.infrastructure.rest.api.OffersApi;
import com.insightdevelop.interline.infrastructure.rest.dto.OfferSearchRequestDto;
import jakarta.ws.rs.core.Response;

public class OffersResource implements OffersApi {

    private final SearchOffersUseCase searchOffers;

    OffersResource(SearchOffersUseCase searchOffers) {
        this.searchOffers = searchOffers;
    }

    @Override
    public Response searchOffers(OfferSearchRequestDto request, String xCorrelationID, String xTenant) {
        AirlineCode tenant = xTenant == null ? null : AirlineCode.of(xTenant);
        return Response.ok(RestMapper.toDto(searchOffers.search(RestMapper.toCriteria(request, tenant)))).build();
    }
}
