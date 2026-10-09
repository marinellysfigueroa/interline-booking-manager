import { Injectable, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, combineLatest, of, switchMap, tap } from 'rxjs';
import type { Booking, BookingPage, BookingStatus } from '../../core/api/api.types';
import { InterlineApi } from '../../core/api/interline-api';
import { ThemeService } from '../../core/theming/theme.service';

interface ListFilter {
  statuses: BookingStatus[];
  page: number;
  size: number;
}

/**
 * Store de la gestión de reservas. Los filtros son signals; `toObservable` los convierte en un
 * stream que, con `switchMap`, relanza la consulta (cancelando la anterior) cada vez que cambia
 * un filtro o el tenant, y `toSignal` vuelve a exponer el resultado como signal.
 */
@Injectable()
export class ManageStore {
  private readonly api = inject(InterlineApi);
  private readonly theme = inject(ThemeService);

  readonly filter = signal<ListFilter>({ statuses: [], page: 0, size: 10 });
  private readonly reload = signal(0);
  private readonly loadingList = signal(false);

  private readonly page$ = combineLatest([
    toObservable(this.filter),
    toObservable(this.theme.tenant),
    toObservable(this.reload),
  ]).pipe(
    tap(() => this.loadingList.set(true)),
    switchMap(([filter]) =>
      this.api.listBookings(filter).pipe(catchError(() => of<BookingPage | null>(null))),
    ),
    tap(() => this.loadingList.set(false)),
  );

  readonly page = toSignal(this.page$, { initialValue: null });
  readonly loading = this.loadingList.asReadonly();
  readonly hasNext = computed(() => {
    const page = this.page();
    return !!page && page.page + 1 < page.totalPages;
  });

  toggleStatus(status: BookingStatus): void {
    this.filter.update((f) => ({
      ...f,
      page: 0,
      statuses: f.statuses.includes(status)
        ? f.statuses.filter((s) => s !== status)
        : [...f.statuses, status],
    }));
  }

  clearStatuses(): void {
    this.filter.update((f) => ({ ...f, page: 0, statuses: [] }));
  }

  goToPage(page: number): void {
    this.filter.update((f) => ({ ...f, page: Math.max(0, page) }));
  }

  refresh(): void {
    this.reload.update((n) => n + 1);
  }

  get(locator: string): Observable<Booking> {
    return this.api.getBooking(locator);
  }

  cancel(locator: string, reason: string): Observable<Booking> {
    return this.api.cancelBooking(locator, reason).pipe(tap(() => this.refresh()));
  }
}
