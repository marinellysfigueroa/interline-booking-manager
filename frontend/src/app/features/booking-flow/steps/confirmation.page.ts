import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ItineraryComponent } from '../../../shared/ui/itinerary.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { StatusTimelineComponent } from '../../../shared/ui/status-timeline.component';
import { BookingFlowStore } from '../booking-flow.store';

@Component({
  selector: 'app-confirmation-page',
  imports: [RouterLink, ItineraryComponent, StatusBadgeComponent, StatusTimelineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (booking(); as booking) {
      <section class="card stack">
        @if (ticketed()) {
          <h1>¡Reserva emitida!</h1>
          <p>
            Tu localizador es <strong class="locator">{{ booking.locator }}</strong
            >. Todo el itinerario va en un solo ticket.
          </p>
        } @else {
          <h1>No pudimos emitir tu reserva</h1>
          <p>
            La reserva <strong class="locator">{{ booking.locator }}</strong> quedó
            <app-status-badge [status]="booking.status" />. Liberamos los asientos y el pago: no se
            te cobró nada.
          </p>
        }
        <app-itinerary [segments]="booking.segments" />

        @if (booking.tickets.length) {
          <h2>Tickets</h2>
          <ul class="tickets">
            @for (ticket of booking.tickets; track ticket.number) {
              <li>
                <span class="mono">{{ ticket.number }}</span> ·
                {{ passengerName(ticket.passengerId) }}
              </li>
            }
          </ul>
        }

        <!-- La línea de tiempo no es crítica para la primera pintura: se carga diferida -->
        @defer (on idle) {
          <h2>Historial</h2>
          <app-status-timeline [history]="booking.statusHistory" />
        } @placeholder {
          <p class="muted">Cargando historial…</p>
        }

        <div class="actions">
          <a class="btn btn--ghost" [routerLink]="['/manage', booking.locator]"
            >Gestionar reserva</a
          >
          <button class="btn" type="button" (click)="newBooking()">Nueva reserva</button>
        </div>
      </section>
    }
  `,
  styles: `
    .locator,
    .mono {
      font-family: ui-monospace, monospace;
      letter-spacing: 0.08em;
    }
    .tickets {
      padding-left: 1.2rem;
      margin: 0;
    }
  `,
})
export class ConfirmationPage {
  private readonly store = inject(BookingFlowStore);
  private readonly router = inject(Router);

  protected readonly booking = this.store.booking;
  protected readonly ticketed = computed(() => this.booking()?.status === 'TICKETED');

  protected passengerName(id: string): string {
    const p = this.booking()?.passengers.find((x) => x.id === id);
    return p ? `${p.firstName} ${p.lastName}` : id;
  }

  protected newBooking(): void {
    this.store.reset();
    this.router.navigate(['/booking/search']);
  }
}
