import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationStore } from './notification.store';

@Component({
  selector: 'app-toasts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" aria-live="polite">
      @for (n of store.notifications(); track n.id) {
        <div class="toast" [class]="'toast toast--' + n.kind" role="status">
          <div>
            <strong>{{ n.title }}</strong>
            @if (n.detail) {
              <p>{{ n.detail }}</p>
            }
            @if (n.correlationId) {
              <small>Referencia: {{ n.correlationId }}</small>
            }
          </div>
          <button
            type="button"
            class="link"
            (click)="store.dismiss(n.id)"
            aria-label="Cerrar aviso"
          >
            ✕
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts {
      position: fixed;
      right: 1rem;
      bottom: 1rem;
      display: grid;
      gap: 0.5rem;
      z-index: 10;
      max-width: min(28rem, calc(100vw - 2rem));
    }
    .toast {
      display: flex;
      gap: 1rem;
      justify-content: space-between;
      align-items: start;
      padding: 0.75rem 1rem;
      border-radius: var(--radius);
      background: var(--surface);
      box-shadow: var(--shadow);
      border-left: 4px solid var(--muted);
    }
    .toast--error {
      border-left-color: var(--danger);
    }
    .toast--success {
      border-left-color: var(--success);
    }
    p {
      margin: 0.25rem 0 0;
    }
    small {
      color: var(--muted);
    }
  `,
})
export class ToastsComponent {
  protected readonly store = inject(NotificationStore);
}
