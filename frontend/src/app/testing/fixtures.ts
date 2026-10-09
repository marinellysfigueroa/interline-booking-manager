import type { Booking, FlightOffer } from '../core/api/api.types';

export const OFFER: FlightOffer = {
  offerId: 'AMS-1234abcd',
  validatingAirline: 'AV',
  interline: { singleTicketEligible: true, operatingAirlines: ['AV', 'IB'], missingAgreements: [] },
  itineraries: [
    {
      direction: 'OUTBOUND',
      duration: 'PT14H40M',
      segments: [
        {
          operatingAirline: 'AV',
          flightNumber: '26',
          origin: 'BOG',
          destination: 'MAD',
          departureAt: '2026-11-20T19:05:00',
          arrivalAt: '2026-11-21T11:40:00',
        },
        {
          operatingAirline: 'IB',
          flightNumber: '3234',
          origin: 'MAD',
          destination: 'FCO',
          departureAt: '2026-11-21T13:15:00',
          arrivalAt: '2026-11-21T15:45:00',
        },
      ],
    },
  ],
  price: { total: { amount: 1120, currency: 'USD' }, milesEquivalent: 112000 },
  expiresAt: '2026-11-01T15:30:00Z',
};

export function booking(status: Booking['status'], extra: Partial<Booking> = {}): Booking {
  return {
    locator: 'K7Q2MX',
    status,
    validatingAirline: 'AV',
    fare: OFFER.price,
    passengers: [
      { id: 'p1', type: 'ADT', firstName: 'Ana', lastName: 'Pérez', dateOfBirth: '1990-04-12' },
    ],
    segments: [],
    payments: [],
    tickets: [],
    statusHistory: [],
    availableActions: [],
    createdAt: '2026-11-01T15:00:00Z',
    updatedAt: '2026-11-01T15:00:00Z',
    ...extra,
  };
}
