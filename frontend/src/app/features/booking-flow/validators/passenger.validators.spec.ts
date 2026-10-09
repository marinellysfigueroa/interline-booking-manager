import { FormArray, FormControl, FormGroup } from '@angular/forms';
import type { PassengerType } from '../../../core/api/api.types';
import {
  PassengerForm,
  ageForTypeValidator,
  applyPassengerTypeValidators,
  infantRuleValidator,
} from './passenger.validators';

const TRAVEL = '2026-11-20';

function row(
  ref: string,
  type: PassengerType,
  dateOfBirth = '',
  adult: string | null = null,
): PassengerForm {
  const group: PassengerForm = new FormGroup({
    ref: new FormControl(ref, { nonNullable: true }),
    type: new FormControl(type, { nonNullable: true }),
    firstName: new FormControl('X', { nonNullable: true }),
    lastName: new FormControl('Y', { nonNullable: true }),
    dateOfBirth: new FormControl(dateOfBirth, { nonNullable: true }),
    associatedAdultRef: new FormControl<string | null>(adult),
  });
  applyPassengerTypeValidators(group, TRAVEL);
  return group;
}

describe('ageForTypeValidator', () => {
  const check = (type: PassengerType, dob: string) =>
    ageForTypeValidator(type, TRAVEL)(new FormControl(dob));

  it.each([
    ['ADT', '1990-01-01'],
    ['ADT', '2014-11-20'], // cumple 12 el día del vuelo
    ['CHD', '2018-06-01'],
    ['CHD', '2024-11-20'], // cumple 2 el día del vuelo
    ['INF', '2025-12-01'],
    ['INF', '2024-11-21'], // cumple 2 el día siguiente: aún INF
  ] as const)('accepts %s born on %s', (type, dob) => {
    expect(check(type, dob)).toBeNull();
  });

  it.each([
    ['ADT', '2015-01-01'],
    ['CHD', '2010-01-01'],
    ['CHD', '2025-01-01'],
    ['INF', '2024-11-20'],
  ] as const)('rejects %s born on %s', (type, dob) => {
    expect(check(type, dob)).toEqual({ ageMismatch: expect.objectContaining({ type }) });
  });

  it('rejects a birth date after the flight', () => {
    expect(check('INF', '2026-12-01')).toEqual({ notBornYet: true });
  });

  it('leaves empty values to Validators.required', () => {
    expect(check('ADT', '')).toBeNull();
  });
});

describe('applyPassengerTypeValidators', () => {
  it('requires and enables the associated adult only for infants', () => {
    const infant = row('I1', 'INF', '2026-01-01');
    const adult = row('A1', 'ADT', '1990-01-01');

    expect(infant.controls.associatedAdultRef.enabled).toBe(true);
    expect(infant.controls.associatedAdultRef.hasError('required')).toBe(true);
    expect(adult.controls.associatedAdultRef.disabled).toBe(true);
    expect(adult.valid).toBe(true);
  });

  it('applies the age range of the row type', () => {
    expect(row('C1', 'CHD', '1990-01-01').controls.dateOfBirth.hasError('ageMismatch')).toBe(true);
    expect(row('A1', 'ADT', '').controls.dateOfBirth.hasError('required')).toBe(true);
  });
});

describe('infantRuleValidator', () => {
  const group = (...rows: PassengerForm[]) =>
    new FormArray(rows, { validators: infantRuleValidator });

  it('accepts each infant with a distinct adult', () => {
    const array = group(
      row('A1', 'ADT'),
      row('A2', 'ADT'),
      row('I1', 'INF', '', 'A1'),
      row('I2', 'INF', '', 'A2'),
    );
    expect(array.errors).toBeNull();
  });

  it('rejects more infants than adults', () => {
    const array = group(row('A1', 'ADT'), row('I1', 'INF', '', 'A1'), row('I2', 'INF', '', 'A1'));
    expect(array.errors).toEqual({ infantsExceedAdults: { infants: 2, adults: 1 } });
  });

  it('rejects two infants on the same adult', () => {
    const array = group(
      row('A1', 'ADT'),
      row('A2', 'ADT'),
      row('I1', 'INF', '', 'A1'),
      row('I2', 'INF', '', 'A1'),
    );
    expect(array.errors).toEqual({ adultWithTwoInfants: { adult: 'A1' } });
  });

  it('rejects an infant assigned to a child', () => {
    const array = group(row('A1', 'ADT'), row('C1', 'CHD'), row('I1', 'INF', '', 'C1'));
    expect(array.errors).toEqual({ infantWithoutAdult: { infant: 'I1' } });
  });

  it('re-evaluates when an assignment changes', () => {
    const infant2 = row('I2', 'INF', '', 'A1');
    const array = group(row('A1', 'ADT'), row('A2', 'ADT'), row('I1', 'INF', '', 'A1'), infant2);
    expect(array.invalid).toBe(true);

    infant2.controls.associatedAdultRef.setValue('A2');

    expect(array.errors).toBeNull();
  });
});
