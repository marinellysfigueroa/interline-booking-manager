import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { BookingStatus } from '../../core/api/api.types';

export const STATUS_LABELS: Record<BookingStatus, string> = {
  DRAFT: 'Borrador',
  PRICED: 'Tarificada',
  HELD: 'Retenida',
  PAYMENT_AUTHORIZED: 'Pago autorizado',
  TICKETED: 'Emitida',
  FAILED: 'Fallida',
  CANCELLED: 'Cancelada',
};

@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [attr.data-tone]="tone()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 700;
      background: color-mix(in srgb, var(--muted) 15%, transparent);
      color: var(--text);
      white-space: nowrap;
    }
    .badge[data-tone='ok'] {
      background: color-mix(in srgb, var(--success) 15%, transparent);
      color: var(--success);
    }
    .badge[data-tone='bad'] {
      background: color-mix(in srgb, var(--danger) 12%, transparent);
      color: var(--danger);
    }
    .badge[data-tone='progress'] {
      background: color-mix(in srgb, var(--brand-primary) 12%, transparent);
      color: var(--brand-primary);
    }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<BookingStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'TICKETED':
        return 'ok';
      case 'FAILED':
      case 'CANCELLED':
        return 'bad';
      default:
        return 'progress';
    }
  });
}
