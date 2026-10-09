import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, exhaustMap, switchMap, tap } from 'rxjs';
import type { Booking } from '../../core/api/api.types';
import { NotificationStore } from '../../core/notifications/notification.store';
import { ItineraryComponent } from '../../shared/ui/itinerary.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { StatusTimelineComponent } from '../../shared/ui/status-timeline.component';
import { ManageStore } from './manage.store';

@Component({
  selector: 'app-booking-detail-page',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    ItineraryComponent,
    StatusBadgeComponent,
    StatusTimelineComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/manage">← Volver al listado</a>
    @if (booking(); as b) {
      <div class="layout">
        <section class="card stack">
          <header class="row heading">
            <h1>
              Reserva <span class="mono">{{ b.locator }}</span>
            </h1>
            <app-status-badge [status]="b.status" />
          </header>
          <p class="muted">
            Validada por {{ b.validatingAirline }} · creada el {{ b.createdAt | date: 'medium' }}
          </p>

          <h2>Itinerario</h2>
          <app-itinerary [segments]="b.segments" />

          <h2>Pasajeros</h2>
          <ul>
            @for (p of b.passengers; track p.id) {
              <li>
                {{ p.firstName }} {{ p.lastName }}
                <span class="muted">({{ p.type }}, {{ p.dateOfBirth | date: 'd MMM y' }})</span>
              </li>
            }
          </ul>

          @if (b.payments.length) {
            <h2>Pagos</h2>
            <ul>
              @for (pay of b.payments; track pay.id) {
                <li>
                  {{ pay.method }} ·
                  @if (pay.cash) {
                    {{ pay.cash.amount | currency: pay.cash.currency }}
                  }
                  @if (pay.miles) {
                    {{ pay.miles.amount | number }} millas ({{ pay.miles.memberNumber }})
                  }
                  · <strong>{{ pay.status }}</strong>
                </li>
              }
            </ul>
          }

          @if (b.tickets.length) {
            <h2>Tickets</h2>
            <ul>
              @for (t of b.tickets; track t.number) {
                <li class="mono">{{ t.number }}</li>
              }
            </ul>
          }

          @if (canCancel()) {
            <form class="cancel stack" (ngSubmit)="cancel$.next()">
              <h2>Cancelar reserva</h2>
              <label>
                Motivo (opcional)
                <input [formControl]="reason" maxlength="200" />
              </label>
              @if (!confirming()) {
                <div class="actions">
                  <button class="btn btn--danger" type="button" (click)="confirming.set(true)">
                    Cancelar reserva
                  </button>
                </div>
              } @else {
                <p>Se liberarán los asientos y el pago. ¿Confirmas?</p>
                <div class="actions">
                  <button class="btn btn--ghost" type="button" (click)="confirming.set(false)">
                    No
                  </button>
                  <button class="btn btn--danger" type="submit" [disabled]="cancelling()">
                    {{ cancelling() ? 'Cancelando…' : 'Sí, cancelar' }}
                  </button>
                </div>
              }
            </form>
          }
        </section>

        <aside class="card stack">
          <h2>Línea de tiempo</h2>
          @defer (on viewport) {
            <app-status-timeline [history]="b.statusHistory" />
          } @placeholder {
            <p class="muted">…</p>
          }
        </aside>
      </div>
    } @else if (notFound()) {
      <p class="card">No encontramos la reserva {{ locator() }}.</p>
    } @else {
      <p class="muted">Cargando…</p>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
    }
    .layout {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 1rem;
      align-items: start;
    }
    .heading {
      justify-content: space-between;
      align-items: center;
    }
    .heading > * {
      flex: 0 0 auto;
    }
    .mono {
      font-family: ui-monospace, monospace;
      letter-spacing: 0.08em;
    }
    ul {
      margin: 0;
      padding-left: 1.2rem;
    }
    .cancel {
      border-top: 1px solid var(--border);
      padding-top: 1rem;
    }
    @media (max-width: 48rem) {
      .layout {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class BookingDetailPage {
  private readonly store = inject(ManageStore);
  private readonly notifications = inject(NotificationStore);

  /** Parámetro de ruta enlazado como input (withComponentInputBinding). */
  readonly locator = input.required<string>();

  protected readonly booking = signal<Booking | null>(null);
  protected readonly notFound = signal(false);
  protected readonly confirming = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly reason = new FormControl('', { nonNullable: true });
  protected readonly canCancel = computed(
    () => this.booking()?.availableActions.includes('CANCEL') ?? false,
  );
  protected readonly cancel$ = new Subject<void>();

  constructor() {
    toObservable(this.locator)
      .pipe(
        tap(() => {
          this.booking.set(null);
          this.notFound.set(false);
        }),
        switchMap((locator) =>
          this.store.get(locator).pipe(
            catchError(() => {
              this.notFound.set(true);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((booking) => this.booking.set(booking));

    this.cancel$
      .pipe(
        exhaustMap(() => {
          this.cancelling.set(true);
          return this.store.cancel(this.locator(), this.reason.value.trim()).pipe(
            catchError(() => EMPTY),
            tap({ finalize: () => this.cancelling.set(false) }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((booking) => {
        this.booking.set(booking);
        this.confirming.set(false);
        this.notifications.success(
          'Reserva cancelada',
          `La reserva ${booking.locator} se canceló y se liberó el pago.`,
        );
      });
  }
}
