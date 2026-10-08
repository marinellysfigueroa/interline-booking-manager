import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import type { PassengerType } from '../../../core/api/api.types';
import { ageOn } from '../../../shared/util/dates';

export type PassengerForm = FormGroup<{
  ref: FormControl<string>;
  type: FormControl<PassengerType>;
  firstName: FormControl<string>;
  lastName: FormControl<string>;
  dateOfBirth: FormControl<string>;
  associatedAdultRef: FormControl<string | null>;
}>;

/** Rango de edad por tipo, a la fecha del primer vuelo (mismas reglas que el backend). */
export const AGE_RANGES: Record<PassengerType, { min: number; max: number; label: string }> = {
  ADT: { min: 12, max: 130, label: 'Adulto (12 años o más)' },
  CHD: { min: 2, max: 11, label: 'Niño (2 a 11 años)' },
  INF: { min: 0, max: 1, label: 'Infante (menor de 2 años)' },
};

/** La fecha de nacimiento debe corresponder al tipo de pasajero en la fecha de viaje. */
export function ageForTypeValidator(type: PassengerType, travelDate: string): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const dob = control.value;
    if (!dob) {
      return null; // de eso se encarga Validators.required
    }
    if (dob > travelDate) {
      return { notBornYet: true };
    }
    const age = ageOn(dob, travelDate);
    const range = AGE_RANGES[type];
    return age < range.min || age > range.max
      ? { ageMismatch: { type, age, expected: range.label } }
      : null;
  };
}

/**
 * Validadores dinámicos por tipo: cada fila del FormArray recibe los suyos según sea ADT, CHD
 * o INF. Para INF se habilita y exige el adulto asociado; para los demás se deshabilita.
 */
export function applyPassengerTypeValidators(group: PassengerForm, travelDate: string): void {
  const type = group.controls.type.value;
  group.controls.dateOfBirth.setValidators([
    Validators.required,
    ageForTypeValidator(type, travelDate),
  ]);
  const adult = group.controls.associatedAdultRef;
  if (type === 'INF') {
    adult.setValidators([Validators.required]);
    adult.enable({ emitEvent: false });
  } else {
    adult.clearValidators();
    adult.setValue(null, { emitEvent: false });
    adult.disable({ emitEvent: false });
  }
  group.controls.dateOfBirth.updateValueAndValidity({ emitEvent: false });
  adult.updateValueAndValidity({ emitEvent: false });
}

/**
 * Validador de grupo (sobre el FormArray de pasajeros): nunca más infantes que adultos y cada
 * infante asociado a un adulto distinto de la misma reserva.
 */
export const infantRuleValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const rows = (control as FormArray<PassengerForm>).getRawValue();
  const adults = rows.filter((p) => p.type === 'ADT');
  const infants = rows.filter((p) => p.type === 'INF');
  if (infants.length > adults.length) {
    return { infantsExceedAdults: { infants: infants.length, adults: adults.length } };
  }
  const adultRefs = new Set(adults.map((a) => a.ref));
  const taken = new Set<string>();
  for (const infant of infants) {
    const adultRef = infant.associatedAdultRef;
    if (!adultRef) {
      continue; // lo marca el validador required de la fila
    }
    if (!adultRefs.has(adultRef)) {
      return { infantWithoutAdult: { infant: infant.ref } };
    }
    if (taken.has(adultRef)) {
      return { adultWithTwoInfants: { adult: adultRef } };
    }
    taken.add(adultRef);
  }
  return null;
};
