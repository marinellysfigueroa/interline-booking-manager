import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { BookingFlowStore, FLOW_STEPS, FlowStep } from './booking-flow.store';

const STEP_LABELS: Record<FlowStep, string> = {
  search: 'Buscar',
  offers: 'Elegir oferta',
  passengers: 'Pasajeros',
  payment: 'Pago',
  confirmation: 'Confirmación',
};

/** Contenedor del flujo: stepper + paso actual (cada paso es una ruta lazy). */
@Component({
  selector: 'app-booking-flow-shell',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav aria-label="Pasos de la reserva">
      <ol class="stepper">
        @for (step of steps; track step; let i = $index) {
          <li
            [class.current]="step === current()"
            [class.done]="i < currentIndex()"
            [attr.aria-current]="step === current() ? 'step' : null"
          >
            <span class="num">{{ i + 1 }}</span>
            <span class="label">{{ labels[step] }}</span>
          </li>
        }
      </ol>
    </nav>
    <router-outlet />
  `,
  styles: `
    .stepper {
      list-style: none;
      display: flex;
      gap: 0.5rem;
      padding: 0;
      margin: 0 0 1.5rem;
      overflow-x: auto;
    }
    li {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      color: var(--muted);
      font-size: 0.875rem;
      white-space: nowrap;
    }
    li:not(:last-child)::after {
      content: '';
      width: 1.5rem;
      height: 2px;
      background: var(--border);
      margin-left: 0.25rem;
    }
    .num {
      display: inline-grid;
      place-items: center;
      width: 1.6rem;
      height: 1.6rem;
      border-radius: 50%;
      border: 2px solid var(--border);
      font-weight: 700;
    }
    li.current {
      color: var(--text);
      font-weight: 700;
    }
    li.current .num {
      border-color: var(--brand-primary);
      background: var(--brand-primary);
      color: var(--brand-primary-contrast);
    }
    li.done .num {
      border-color: var(--brand-primary);
      color: var(--brand-primary);
    }
    @media (max-width: 40rem) {
      li:not(.current) .label {
        display: none;
      }
    }
  `,
})
export class BookingFlowShellComponent {
  protected readonly store = inject(BookingFlowStore);
  private readonly router = inject(Router);
  protected readonly steps = FLOW_STEPS;
  protected readonly labels = STEP_LABELS;

  protected readonly current = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.stepFromUrl()),
    ),
    { initialValue: this.stepFromUrl() },
  );
  protected readonly currentIndex = computed(() => FLOW_STEPS.indexOf(this.current()));

  private stepFromUrl(): FlowStep {
    const segment = this.router.url.split('?')[0].split('/')[2] as FlowStep;
    return FLOW_STEPS.includes(segment) ? segment : 'search';
  }
}
