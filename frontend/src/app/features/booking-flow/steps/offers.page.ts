import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import type { FlightOffer } from '../../../core/api/api.types';
import { ItineraryComponent } from '../../../shared/ui/itinerary.component';
import { BookingFlowStore } from '../booking-flow.store';

@Component({
  selector: 'app-offers-page',
  imports: [CurrencyPipe, DecimalPipe, ItineraryComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <header class="row heading">
        <h1>{{ criteria()?.origin }} → {{ criteria()?.destination }}</h1>
        <a routerLink="/booking/search">Modificar búsqueda</a>
      </header>
      <p class="muted">
        {{ offers().length }} ofertas · {{ eligibleCount() }} se pueden emitir en un solo ticket
      </p>

      @for (offer of offers(); track offer.offerId) {
        <article class="card offer" [class.ineligible]="!offer.interline.singleTicketEligible">
          <div class="stack">
            <div class="row meta">
              <strong>Valida {{ offer.validatingAirline }}</strong>
              @if (offer.interline.singleTicketEligible) {
                <span class="tag ok">Ticket único</span>
              } @else {
                <span class="tag warn"
                  >Sin acuerdo interline con
                  {{ offer.interline.missingAgreements.join(', ') }}</span
                >
              }
            </div>
            @for (itinerary of offer.itineraries; track $index) {
              <div>
                <small class="muted"
                  >{{ itinerary.direction === 'OUTBOUND' ? 'Ida' : 'Vuelta' }} ·
                  {{ duration(itinerary.duration) }}</small
                >
                <app-itinerary [segments]="itinerary.segments" />
              </div>
            }
          </div>
          <div class="price">
            <span class="amount">{{
              offer.price.total.amount | currency: offer.price.total.currency
            }}</span>
            <small class="muted">o {{ offer.price.milesEquivalent | number }} millas</small>
            <button
              class="btn"
              type="button"
              [disabled]="!offer.interline.singleTicketEligible"
              [attr.aria-label]="
                'Elegir oferta de ' + offer.validatingAirline + ' por ' + offer.price.total.amount
              "
              (click)="choose(offer)"
            >
              Elegir
            </button>
          </div>
        </article>
      }
    </div>
  `,
  styles: `
    .heading {
      justify-content: space-between;
      align-items: baseline;
    }
    .heading > * {
      flex: 0 1 auto;
    }
    .offer {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 1rem;
      align-items: center;
    }
    .offer.ineligible {
      opacity: 0.75;
    }
    .meta {
      align-items: center;
      gap: 0.5rem;
    }
    .meta > * {
      flex: 0 0 auto;
    }
    .tag {
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
    }
    .tag.ok {
      background: color-mix(in srgb, var(--success) 15%, transparent);
      color: var(--success);
    }
    .tag.warn {
      background: color-mix(in srgb, var(--warning) 15%, transparent);
      color: var(--warning);
    }
    .price {
      display: grid;
      justify-items: end;
      gap: 0.35rem;
    }
    .amount {
      font-size: 1.4rem;
      font-weight: 800;
    }
    @media (max-width: 40rem) {
      .offer {
        grid-template-columns: 1fr;
      }
      .price {
        justify-items: start;
      }
    }
  `,
})
export class OffersPage {
  private readonly store = inject(BookingFlowStore);
  private readonly router = inject(Router);

  protected readonly criteria = this.store.criteria;
  protected readonly offers = this.store.offers;
  protected readonly eligibleCount = computed(
    () => this.offers().filter((o) => o.interline.singleTicketEligible).length,
  );

  protected choose(offer: FlightOffer): void {
    this.store.selectOffer(offer.offerId);
    this.router.navigate(['/booking/passengers']);
  }

  /** PT14H40M → 14 h 40 min */
  protected duration(iso: string): string {
    const match = /PT(?:(\d+)H)?(?:(\d+)M)?/.exec(iso);
    return match ? `${match[1] ?? 0} h ${match[2] ?? '00'} min` : iso;
  }
}
