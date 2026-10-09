import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { isoDate } from '../../../shared/util/dates';
import { BookingFlowStore, SearchCriteria } from '../booking-flow.store';
import { AirportAutocompleteComponent } from '../ui/airport-autocomplete.component';

/** Validador de grupo: origen ≠ destino, regreso ≥ ida e infantes ≤ adultos. */
function searchRules(group: AbstractControl): ValidationErrors | null {
  const v = (group as FormGroup).getRawValue();
  const errors: ValidationErrors = {};
  if (v.origin && v.origin === v.destination) errors['sameAirport'] = true;
  if (v.returnDate && v.returnDate < v.departureDate) errors['returnBeforeDeparture'] = true;
  if (v.infants > v.adults) errors['infantsExceedAdults'] = true;
  if (v.adults + v.children > 9) errors['tooManyPassengers'] = true;
  return Object.keys(errors).length ? errors : null;
}

@Component({
  selector: 'app-search-page',
  imports: [ReactiveFormsModule, AirportAutocompleteComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="card stack" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h1>¿A dónde viajas?</h1>
      <div class="row">
        <app-airport-autocomplete label="Origen" [control]="form.controls.origin" />
        <app-airport-autocomplete label="Destino" [control]="form.controls.destination" />
      </div>
      <div class="row">
        <label>
          Ida
          <input type="date" formControlName="departureDate" [min]="today" />
        </label>
        <label>
          Regreso (opcional)
          <input
            type="date"
            formControlName="returnDate"
            [min]="form.controls.departureDate.value"
          />
        </label>
      </div>
      <fieldset class="row pax">
        <legend>Pasajeros</legend>
        <label>Adultos <input type="number" min="1" max="9" formControlName="adults" /></label>
        <label
          >Niños (2-11) <input type="number" min="0" max="8" formControlName="children"
        /></label>
        <label
          >Infantes (&lt;2) <input type="number" min="0" max="9" formControlName="infants"
        /></label>
      </fieldset>
      @if (form.touched && form.errors; as errors) {
        <div class="field-error" role="alert">
          @if (errors['sameAirport']) {
            <p>El origen y el destino deben ser distintos.</p>
          }
          @if (errors['returnBeforeDeparture']) {
            <p>El regreso no puede ser antes de la ida.</p>
          }
          @if (errors['infantsExceedAdults']) {
            <p>
              Cada infante viaja en brazos de un adulto: no puede haber más infantes que adultos.
            </p>
          }
          @if (errors['tooManyPassengers']) {
            <p>Máximo 9 pasajeros con asiento.</p>
          }
        </div>
      }
      @if (noResults()) {
        <p class="card empty" role="status">
          No encontramos vuelos para esa ruta y fecha. Prueba otra combinación.
        </p>
      }
      <div class="actions">
        <button class="btn" type="submit" [disabled]="store.loading()">
          {{ store.loading() ? 'Buscando…' : 'Buscar vuelos' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    fieldset {
      border: 0;
      padding: 0;
      margin: 0;
    }
    legend {
      font-weight: 700;
      margin-bottom: 0.5rem;
    }
    .field-error p {
      margin: 0.25rem 0;
    }
    .empty {
      background: color-mix(in srgb, var(--warning) 10%, var(--surface));
    }
  `,
})
export class SearchPage {
  protected readonly store = inject(BookingFlowStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly today = isoDate();

  private readonly previous = this.store.criteria();
  protected readonly form = new FormGroup(
    {
      origin: new FormControl(this.previous?.origin ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^[A-Z]{3}$/)],
      }),
      destination: new FormControl(this.previous?.destination ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^[A-Z]{3}$/)],
      }),
      departureDate: new FormControl(this.previous?.departureDate ?? isoDate(30), {
        nonNullable: true,
        validators: Validators.required,
      }),
      returnDate: new FormControl(this.previous?.returnDate ?? '', { nonNullable: true }),
      adults: new FormControl(this.previous?.passengers.adults ?? 1, {
        nonNullable: true,
        validators: [Validators.required, Validators.min(1), Validators.max(9)],
      }),
      children: new FormControl(this.previous?.passengers.children ?? 0, {
        nonNullable: true,
        validators: [Validators.min(0), Validators.max(8)],
      }),
      infants: new FormControl(this.previous?.passengers.infants ?? 0, {
        nonNullable: true,
        validators: [Validators.min(0), Validators.max(9)],
      }),
    },
    { validators: searchRules },
  );

  protected readonly noResults = signal(false);

  protected submit(): void {
    this.noResults.set(false);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    const criteria: SearchCriteria = {
      origin: v.origin,
      destination: v.destination,
      departureDate: v.departureDate,
      ...(v.returnDate ? { returnDate: v.returnDate } : {}),
      passengers: { adults: v.adults, children: v.children, infants: v.infants },
    };
    this.store
      .search(criteria)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (offers) =>
          offers.length ? this.router.navigate(['/booking/offers']) : this.noResults.set(true),
        error: () => undefined, // el interceptor de errores ya avisó
      });
  }
}
