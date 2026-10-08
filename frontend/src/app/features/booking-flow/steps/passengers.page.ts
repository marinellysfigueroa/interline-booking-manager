import { LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { map, startWith } from 'rxjs';
import type { PassengerInput, PassengerType } from '../../../core/api/api.types';
import { BookingFlowStore } from '../booking-flow.store';
import {
  AGE_RANGES,
  PassengerForm,
  applyPassengerTypeValidators,
  infantRuleValidator,
} from '../validators/passenger.validators';

const TYPE_ORDER: PassengerType[] = ['ADT', 'CHD', 'INF'];
const REF_PREFIX: Record<PassengerType, string> = { ADT: 'A', CHD: 'C', INF: 'I' };

/**
 * Datos de pasajeros con un FormArray. Las filas se generan con la composición para la que se
 * tarificó la oferta (el backend exige que coincida) y cada una recibe validadores según su
 * tipo; el FormArray lleva el validador de grupo de la regla de infantes.
 */
@Component({
  selector: 'app-passengers-page',
  imports: [ReactiveFormsModule, LowerCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="stack" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h1>Pasajeros</h1>
      <ng-container formArrayName="passengers">
        @for (group of passengers.controls; track group.controls.ref.value; let i = $index) {
          <fieldset class="card stack" [formGroupName]="i">
            <legend>{{ i + 1 }}. {{ ranges[group.controls.type.value].label }}</legend>
            <div class="row">
              <label>
                Nombre
                <input
                  formControlName="firstName"
                  autocomplete="given-name"
                  [attr.aria-label]="'Nombre del pasajero ' + (i + 1)"
                />
                @if (invalid(group.controls.firstName)) {
                  <span class="field-error">Obligatorio</span>
                }
              </label>
              <label>
                Apellido
                <input
                  formControlName="lastName"
                  autocomplete="family-name"
                  [attr.aria-label]="'Apellido del pasajero ' + (i + 1)"
                />
                @if (invalid(group.controls.lastName)) {
                  <span class="field-error">Obligatorio</span>
                }
              </label>
              <label>
                Fecha de nacimiento
                <input
                  type="date"
                  formControlName="dateOfBirth"
                  [attr.aria-label]="'Fecha de nacimiento del pasajero ' + (i + 1)"
                />
                @if (invalid(group.controls.dateOfBirth)) {
                  <span class="field-error">
                    @if (group.controls.dateOfBirth.hasError('required')) {
                      Obligatoria
                    } @else if (group.controls.dateOfBirth.hasError('notBornYet')) {
                      No puede ser posterior al viaje
                    } @else {
                      Debe ser {{ ranges[group.controls.type.value].label | lowercase }} el día del
                      vuelo
                    }
                  </span>
                }
              </label>
              @if (group.controls.type.value === 'INF') {
                <label>
                  Viaja en brazos de
                  <select
                    formControlName="associatedAdultRef"
                    [attr.aria-label]="'Adulto responsable del pasajero ' + (i + 1)"
                  >
                    <option [ngValue]="null" disabled>Elige un adulto</option>
                    @for (adult of adultOptions(); track adult.ref) {
                      <option [ngValue]="adult.ref">{{ adult.label }}</option>
                    }
                  </select>
                  @if (invalid(group.controls.associatedAdultRef)) {
                    <span class="field-error">Elige el adulto</span>
                  }
                </label>
              }
            </div>
          </fieldset>
        }
      </ng-container>
      @if (passengers.errors; as errors) {
        <p class="field-error" role="alert">
          @if (errors['infantsExceedAdults']) {
            No puede haber más infantes que adultos.
          }
          @if (errors['adultWithTwoInfants']) {
            Cada adulto puede llevar solo un infante.
          }
          @if (errors['infantWithoutAdult']) {
            Cada infante debe ir con un adulto de la reserva.
          }
        </p>
      }

      <fieldset class="card row" formGroupName="contact">
        <legend>Contacto</legend>
        <label>
          Email
          <input type="email" formControlName="email" autocomplete="email" />
          @if (invalid(form.controls.contact.controls.email)) {
            <span class="field-error">Email válido obligatorio</span>
          }
        </label>
        <label>
          Teléfono (opcional, formato +573001234567)
          <input type="tel" formControlName="phone" autocomplete="tel" />
          @if (invalid(form.controls.contact.controls.phone)) {
            <span class="field-error">Formato E.164, p. ej. +573001234567</span>
          }
        </label>
      </fieldset>

      <div class="actions">
        <button class="btn" type="submit" [disabled]="store.loading()">
          {{ store.loading() ? 'Reservando…' : 'Continuar al pago' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    fieldset {
      border: 0;
      margin: 0;
    }
    legend {
      font-weight: 700;
      padding: 0;
      margin-bottom: 0.5rem;
    }
  `,
})
export class PassengersPage {
  protected readonly store = inject(BookingFlowStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly ranges = AGE_RANGES;

  protected readonly passengers = new FormArray<PassengerForm>([], {
    validators: infantRuleValidator,
  });
  protected readonly form = new FormGroup({
    passengers: this.passengers,
    contact: new FormGroup({
      email: new FormControl(this.store.contact()?.email ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.email],
      }),
      phone: new FormControl(this.store.contact()?.phone ?? '', {
        nonNullable: true,
        validators: Validators.pattern(/^\+[1-9][0-9]{6,14}$/),
      }),
    }),
  });

  /** Adultos ya nombrados, para el selector "viaja en brazos de" de los infantes. */
  protected readonly adultOptions = toSignal(
    this.passengers.valueChanges.pipe(
      startWith(null),
      map(() =>
        this.passengers
          .getRawValue()
          .filter((p) => p.type === 'ADT')
          .map((p, i) => ({
            ref: p.ref,
            label: `${p.firstName || 'Adulto'} ${p.lastName || i + 1}`.trim(),
          })),
      ),
    ),
    { initialValue: [] },
  );

  private readonly travelDate = computed(() => this.store.firstDepartureDate() ?? '');

  constructor() {
    const counts = this.store.criteria()?.passengers ?? { adults: 1, children: 0, infants: 0 };
    const saved = this.store.passengers();
    const amount: Record<PassengerType, number> = {
      ADT: counts.adults,
      CHD: counts.children,
      INF: counts.infants,
    };
    for (const type of TYPE_ORDER) {
      for (let n = 1; n <= amount[type]; n++) {
        const ref = `${REF_PREFIX[type]}${n}`;
        this.passengers.push(
          this.row(
            type,
            ref,
            saved.find((p) => p.ref === ref),
          ),
        );
      }
    }
  }

  private row(type: PassengerType, ref: string, saved?: PassengerInput): PassengerForm {
    const group: PassengerForm = new FormGroup({
      ref: new FormControl(ref, { nonNullable: true }),
      type: new FormControl(type, { nonNullable: true }),
      firstName: new FormControl(saved?.firstName ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(50)],
      }),
      lastName: new FormControl(saved?.lastName ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(50)],
      }),
      dateOfBirth: new FormControl(saved?.dateOfBirth ?? '', { nonNullable: true }),
      associatedAdultRef: new FormControl<string | null>(saved?.associatedAdultRef ?? null),
    });
    applyPassengerTypeValidators(group, this.travelDate());
    return group;
  }

  protected invalid(control: { invalid: boolean; touched: boolean }): boolean {
    return control.invalid && control.touched;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const passengers: PassengerInput[] = this.passengers.getRawValue().map((p) => ({
      ref: p.ref,
      type: p.type,
      firstName: p.firstName.trim(),
      lastName: p.lastName.trim(),
      dateOfBirth: p.dateOfBirth,
      ...(p.type === 'INF' && p.associatedAdultRef
        ? { associatedAdultRef: p.associatedAdultRef }
        : {}),
    }));
    const { email, phone } = this.form.controls.contact.getRawValue();
    this.store
      .createBooking(passengers, { email, ...(phone ? { phone } : {}) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.router.navigate(['/booking/payment']),
        error: () => undefined,
      });
  }
}
