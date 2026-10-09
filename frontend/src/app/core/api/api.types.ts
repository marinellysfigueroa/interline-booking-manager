/**
 * Alias legibles sobre los tipos generados desde el contrato (`npm run api:types`).
 * El contrato OpenAPI de /api es la única fuente de verdad también en el frontend.
 */
import type { components } from './schema';

type Schemas = components['schemas'];

export type Airport = Schemas['Airport'];
export type OfferSearchRequest = Schemas['OfferSearchRequest'];
export type OfferSearchResponse = Schemas['OfferSearchResponse'];
export type FlightOffer = Schemas['FlightOffer'];
export type FlightSegment = Schemas['FlightSegment'];
export type PassengerCounts = Schemas['PassengerCounts'];
export type PassengerType = Schemas['PassengerType'];
export type PassengerInput = Schemas['PassengerInput'];
export type Contact = Schemas['Contact'];
export type CreateBookingRequest = Schemas['CreateBookingRequest'];
export type PaymentRequest = Schemas['PaymentRequest'];
export type PaymentMethod = Schemas['PaymentMethod'];
export type Booking = Schemas['Booking'];
export type BookingStatus = Schemas['BookingStatus'];
export type BookingSummary = Schemas['BookingSummary'];
export type BookingPage = Schemas['BookingPage'];
export type Segment = Schemas['Segment'];
export type StatusChange = Schemas['StatusChange'];
export type Problem = Schemas['Problem'];
export type Money = Schemas['Money'];

export const BOOKING_STATUSES: readonly BookingStatus[] = [
  'DRAFT',
  'PRICED',
  'HELD',
  'PAYMENT_AUTHORIZED',
  'TICKETED',
  'FAILED',
  'CANCELLED',
];
