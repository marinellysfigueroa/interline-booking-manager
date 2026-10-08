import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BOOKING_STATUSES } from '../../core/api/api.types';
import { StatusBadgeComponent, STATUS_LABELS } from '../../shared/ui/status-badge.component';
import { ManageStore } from './manage.store';

@Component({
  selector: 'app-bookings-list-page',
  imports: [DatePipe, RouterLink, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="stack">
      <header class="row heading">
        <h1>Mis reservas</h1>
        <button class="btn btn--ghost" type="button" (click)="store.refresh()">Actualizar</button>
      </header>

      <div class="filters" role="group" aria-label="Filtrar por estado">
        <button
          type="button"
          class="chip"
          [class.on]="!store.filter().statuses.length"
          (click)="store.clearStatuses()"
        >
          Todas
        </button>
        @for (status of statuses; track status) {
          <button
            type="button"
            class="chip"
            [class.on]="store.filter().statuses.includes(status)"
            [attr.aria-pressed]="store.filter().statuses.includes(status)"
            (click)="store.toggleStatus(status)"
          >
            {{ labels[status] }}
          </button>
        }
      </div>

      @if (store.page(); as page) {
        @if (page.items.length) {
          <div class="card table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Localizador</th>
                  <th>Ruta</th>
                  <th>Salida</th>
                  <th>Pax</th>
                  <th>Estado</th>
                  <th>Creada</th>
                </tr>
              </thead>
              <tbody>
                @for (b of page.items; track b.locator) {
                  <tr>
                    <td>
                      <a class="mono" [routerLink]="[b.locator]">{{ b.locator }}</a>
                    </td>
                    <td>{{ b.origin }} → {{ b.destination }}</td>
                    <td>{{ b.firstDepartureAt | date: 'd MMM y, HH:mm' }}</td>
                    <td>{{ b.passengerCount }}</td>
                    <td><app-status-badge [status]="b.status" /></td>
                    <td class="muted">{{ b.createdAt | date: 'short' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <nav class="pager" aria-label="Paginación">
            <button
              class="btn btn--ghost"
              type="button"
              [disabled]="page.page === 0"
              (click)="store.goToPage(page.page - 1)"
            >
              Anterior
            </button>
            <span
              >Página {{ page.page + 1 }} de {{ page.totalPages || 1 }} ·
              {{ page.totalElements }} reservas</span
            >
            <button
              class="btn btn--ghost"
              type="button"
              [disabled]="!store.hasNext()"
              (click)="store.goToPage(page.page + 1)"
            >
              Siguiente
            </button>
          </nav>
        } @else {
          <p class="card muted">No hay reservas con esos filtros.</p>
        }
      } @else if (store.loading()) {
        <p class="muted">Cargando…</p>
      }
    </section>
  `,
  styles: `
    .heading {
      justify-content: space-between;
      align-items: center;
    }
    .heading > * {
      flex: 0 0 auto;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .chip {
      font: inherit;
      font-size: 0.85rem;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text);
      border-radius: 999px;
      padding: 0.3rem 0.8rem;
      cursor: pointer;
    }
    .chip.on {
      background: var(--brand-primary);
      color: var(--brand-primary-contrast);
      border-color: var(--brand-primary);
    }
    .table-wrap {
      overflow-x: auto;
      padding: 0;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 0.7rem 1rem;
      border-bottom: 1px solid var(--border);
      white-space: nowrap;
    }
    th {
      font-size: 0.8rem;
      color: var(--muted);
    }
    .mono {
      font-family: ui-monospace, monospace;
      font-weight: 700;
      letter-spacing: 0.08em;
    }
    .pager {
      display: flex;
      gap: 1rem;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
    }
  `,
})
export class BookingsListPage {
  protected readonly store = inject(ManageStore);
  protected readonly statuses = BOOKING_STATUSES;
  protected readonly labels = STATUS_LABELS;
}
