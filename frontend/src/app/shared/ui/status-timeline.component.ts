import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { StatusChange } from '../../core/api/api.types';
import { StatusBadgeComponent } from './status-badge.component';

/** Línea de tiempo de estados de la reserva (statusHistory). */
@Component({
  selector: 'app-status-timeline',
  imports: [DatePipe, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="timeline" aria-label="Línea de tiempo de la reserva">
      @for (change of history(); track $index) {
        <li>
          <span class="dot" aria-hidden="true"></span>
          <div>
            <app-status-badge [status]="change.to" />
            <time [attr.datetime]="change.at">{{ change.at | date: 'medium' }}</time>
            @if (change.reason) {
              <p class="muted">{{ change.reason }}</p>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styles: `
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 0.75rem;
    }
    li {
      display: grid;
      grid-template-columns: 1rem 1fr;
      gap: 0.75rem;
      position: relative;
    }
    li:not(:last-child)::after {
      content: '';
      position: absolute;
      left: 0.4rem;
      top: 1.1rem;
      bottom: -0.9rem;
      width: 2px;
      background: var(--border);
    }
    .dot {
      width: 0.8rem;
      height: 0.8rem;
      border-radius: 50%;
      background: var(--brand-primary);
      margin-top: 0.25rem;
    }
    time {
      margin-left: 0.5rem;
      font-size: 0.8rem;
      color: var(--muted);
    }
    p {
      margin: 0.25rem 0 0;
      font-size: 0.875rem;
    }
  `,
})
export class StatusTimelineComponent {
  readonly history = input.required<StatusChange[]>();
}
