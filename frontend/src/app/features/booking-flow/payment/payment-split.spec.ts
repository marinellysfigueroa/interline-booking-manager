import { splitMixedPayment } from './payment-split';

describe('splitMixedPayment', () => {
  const fare = { total: { amount: 1849.6, currency: 'USD' }, milesEquivalent: 184960 };
  const covers = (cash: number, miles: number) =>
    cash / fare.total.amount + miles / fare.milesEquivalent >= 1 - 1e-12;

  it('splits 50/50', () => {
    expect(splitMixedPayment(fare, 50)).toEqual({ cashAmount: 924.8, miles: 92480 });
  });

  it.each([10, 25, 33, 50, 67, 75, 90])('always covers the fare with %i %% cash', (percent) => {
    const { cashAmount, miles } = splitMixedPayment(fare, percent);
    expect(covers(cashAmount, miles)).toBe(true);
    expect(Number.isInteger(miles)).toBe(true);
    expect(Math.round(cashAmount * 100)).toBe(cashAmount * 100);
  });

  it('rounds miles up when the cash part is not exact', () => {
    const odd = { total: { amount: 100, currency: 'USD' }, milesEquivalent: 9999 };
    expect(splitMixedPayment(odd, 33)).toEqual({ cashAmount: 33, miles: 6700 });
  });
});
