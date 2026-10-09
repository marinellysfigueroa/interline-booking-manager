import type { FlightOffer } from '../../../core/api/api.types';

export interface PaymentSplit {
  cashAmount: number;
  miles: number;
}

/**
 * Reparte un pago mixto: `cashPercent` % en dinero y el resto en millas, garantizando la
 * regla del backend `cash/total + miles/milesEquivalent ≥ 1`. Se trabaja en céntimos y se
 * redondean las millas hacia arriba para no quedarse nunca por debajo.
 */
export function splitMixedPayment(fare: FlightOffer['price'], cashPercent: number): PaymentSplit {
  const totalCents = Math.round(fare.total.amount * 100);
  const cashCents = Math.round((totalCents * cashPercent) / 100);
  const miles = Math.ceil((fare.milesEquivalent * (totalCents - cashCents)) / totalCents);
  return { cashAmount: cashCents / 100, miles };
}
