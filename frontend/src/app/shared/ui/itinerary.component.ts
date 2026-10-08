import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { FlightSegment } from '../../core/api/api.types';

/** Lista de tramos con la aerolínea operadora (la que cuenta para la regla interline). */
@Component({
  selector: 'app-itinerary',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="segments">
      @for (segment of segments(); track $index) {
        <li>
          <span class="flight">{{ segment.operatingAirline }}{{ segment.flightNumber }}</span>
          <span
            ><strong>{{ segment.origin }}</strong>
            {{ segment.departureAt | date: 'd MMM, HH:mm' }}</span
          >
          <span aria-hidden="true">→</span>
          <span
            ><strong>{{ segment.destination }}</strong>
            {{ segment.arrivalAt | date: 'd MMM, HH:mm' }}</span
          >
          @if (statusOf(segment); as status) {
            <span class="seg-status" [attr.data-status]="status">{{ status }}</span>
          }
        </li>
      }
    </ul>
  `,
  styles: `
    .segments {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.4rem;
    }
    li {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: baseline;
    }
    .flight {
      font-family: ui-monospace, monospace;
      font-weight: 700;
      color: var(--brand-primary);
      min-width: 4.5rem;
    }
    .seg-status {
      font-family: ui-monospace, monospace;
      font-size: 0.75rem;
      padding: 0 0.4rem;
      border-radius: 0.3rem;
      background: color-mix(in srgb, var(--muted) 15%, transparent);
    }
    .seg-status[data-status='HK'] {
      color: var(--success);
    }
    .seg-status[data-status='XX'] {
      color: var(--danger);
    }
  `,
})
export class ItineraryComponent {
  readonly segments = input.required<(FlightSegment & { status?: string })[]>();

  protected statusOf(segment: FlightSegment & { status?: string }): string | undefined {
    return segment.status;
  }
}
