import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { EMPTY, Subject, catchError, exhaustMap, startWith, tap } from 'rxjs';
import type { PaymentMethod, PaymentRequest } from '../../../core/api/api.types';
import { ApiError } from '../../../core/http/api-error';
import { ItineraryComponent } from '../../../shared/ui/itinerary.component';
import { BookingFlowStore } from '../booking-flow.store';
import { splitMixedPayment } from '../payment/payment-split';

@Component({
  selector: 'app-payment-page',
  imports: [ReactiveFormsModule, CurrencyPipe, DecimalPipe, ItineraryComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (booking(); as booking) {
      <div class="layout">
        <form class="card stack" [formGroup]="form" (ngSubmit)="pay$.next()" novalidate>
          <h1>Pago</h1>
          <p class="muted">
            Reserva <strong class="locator">{{ booking.locator }}</strong> retenida.
          </p>

          <fieldset class="methods" [disabled]="alreadyAuthorized()">
            <legend>Medio de pago</legend>
            @for (option of methods; track option.value) {
              <label class="method">
                <input type="radio" formControlName="method" [value]="option.value" />
                {{ option.label }}
              </label>
            }
          </fieldset>

          @if (usesMiles()) {
            <label>
              Número de socio {{ loyaltyHint }}
              <input formControlName="memberNumber" inputmode="numeric" autocomplete="off" />
              @if (form.controls.memberNumber.invalid && form.controls.memberNumber.touched) {
                <span class="field-error">Entre 6 y 16 dígitos</span>
              }
            </label>
          }
          @if (method() === 'MIXED') {
            <label>
              Parte en efectivo: {{ form.controls.cashPercent.value }} %
              <input type="range" min="10" max="90" step="5" formControlName="cashPercent" />
            </label>
          }

          <div class="summary">
            @if (request(); as r) {
              @if (r.cash) {
                <span>{{ r.cash.amount | currency: r.cash.currency }}</span>
              }
              @if (r.cash && r.miles) {
                <span>+</span>
              }
              @if (r.miles) {
                <span>{{ r.miles.amount | number }} millas</span>
              }
            }
          </div>

          @if (alreadyAuthorized()) {
            <p class="muted">El pago ya está autorizado: falta emitir el ticket.</p>
          }
          @if (store.error(); as error) {
            <p class="field-error" role="alert">{{ error.detail }}</p>
          }

          <div class="actions">
            <!-- El clic alimenta un Subject; exhaustMap ignora clics mientras hay un pago en curso -->
            <button class="btn" type="submit" [disabled]="store.loading()">
              {{
                store.loading()
                  ? 'Procesando pago y emisión…'
                  : alreadyAuthorized()
                    ? 'Emitir ticket'
                    : 'Pagar y emitir'
              }}
            </button>
          </div>
        </form>

        <aside class="card stack">
          <h2>Resumen</h2>
          <app-itinerary [segments]="booking.segments" />
          <p>{{ booking.passengers.length }} pasajeros</p>
          @if (booking.fare; as fare) {
            <p class="total">{{ fare.total.amount | currency: fare.total.currency }}</p>
            <small class="muted">o {{ fare.milesEquivalent | number }} millas</small>
          }
        </aside>
      </div>
    }
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 1rem;
      align-items: start;
    }
    .locator {
      font-family: ui-monospace, monospace;
      letter-spacing: 0.1em;
    }
    fieldset {
      border: 0;
      padding: 0;
      margin: 0;
      display: flex;
      gap: 1rem;
      flex-wrap: wrap;
    }
    legend {
      font-weight: 700;
      margin-bottom: 0.5rem;
      width: 100%;
    }
    .method {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-weight: 500;
    }
    .summary {
      display: flex;
      gap: 0.5rem;
      font-size: 1.25rem;
      font-weight: 800;
    }
    .total {
      font-size: 1.4rem;
      font-weight: 800;
      margin: 0;
    }
    @media (max-width: 48rem) {
      .layout {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class PaymentPage {
  protected readonly store = inject(BookingFlowStore);
  private readonly router = inject(Router);

  protected readonly booking = this.store.booking;
  protected readonly alreadyAuthorized = computed(
    () => this.booking()?.status === 'PAYMENT_AUTHORIZED',
  );
  protected readonly loyaltyHint = '(programa de lealtad de la aerolínea validadora)';
  protected readonly methods: { value: PaymentMethod; label: string }[] = [
    { value: 'CASH', label: 'Efectivo / tarjeta' },
    { value: 'MILES', label: 'Millas' },
    { value: 'MIXED', label: 'Mixto' },
  ];

  protected readonly form = new FormGroup({
    method: new FormControl<PaymentMethod>('CASH', { nonNullable: true }),
    memberNumber: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[0-9]{6,16}$/)],
    }),
    cashPercent: new FormControl(50, { nonNullable: true }),
  });

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    {
      initialValue: this.form.getRawValue(),
    },
  );
  protected readonly method = computed(() => this.formValue().method ?? 'CASH');
  protected readonly usesMiles = computed(() => this.method() !== 'CASH');

  /** Cuerpo del pago derivado del formulario y de la tarifa de la reserva. */
  protected readonly request = computed<PaymentRequest | null>(() => {
    const fare = this.booking()?.fare;
    if (!fare) return null;
    const { method = 'CASH', memberNumber = '', cashPercent = 50 } = this.formValue();
    switch (method) {
      case 'CASH':
        return { method, cash: { ...fare.total } };
      case 'MILES':
        return { method, miles: { amount: fare.milesEquivalent, memberNumber } };
      case 'MIXED': {
        const split = splitMixedPayment(fare, cashPercent);
        return {
          method,
          cash: { amount: split.cashAmount, currency: fare.total.currency },
          miles: { amount: split.miles, memberNumber },
        };
      }
    }
  });

  /** Clics del botón pagar. */
  protected readonly pay$ = new Subject<void>();

  constructor() {
    this.pay$
      .pipe(
        tap(() => this.form.markAllAsTouched()),
        // exhaustMap: mientras un pago está en vuelo, los clics extra se descartan (no hay doble cobro)
        exhaustMap(() => {
          const request = this.request();
          const memberInvalid = this.usesMiles() && this.form.controls.memberNumber.invalid;
          if (!request || (memberInvalid && !this.alreadyAuthorized())) {
            return EMPTY;
          }
          return this.store.payAndIssue(request).pipe(
            catchError((error: unknown) => {
              // Saga compensada: la reserva quedó FAILED y se muestra en la confirmación.
              if (error instanceof ApiError && error.bookingStatus === 'FAILED') {
                this.router.navigate(['/booking/confirmation']);
              }
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.router.navigate(['/booking/confirmation']));
  }
}
