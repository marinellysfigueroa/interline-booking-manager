import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';
import type { Airport } from '../../../core/api/api.types';
import { InterlineApi } from '../../../core/api/interline-api';

let nextId = 0;

/**
 * Autocompletado de aeropuertos (combobox accesible).
 *
 * El texto escrito pasa por `debounceTime` (no se consulta en cada tecla), `distinctUntilChanged`
 * y `switchMap`, que cancela la petición anterior si llega una nueva: nunca se pinta una
 * respuesta vieja encima de una reciente. El resultado se expone como signal con `toSignal`.
 * El control del formulario guarda el código IATA elegido.
 */
@Component({
  selector: 'app-airport-autocomplete',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label [attr.for]="inputId">
      {{ label() }}
      <input
        [id]="inputId"
        type="text"
        role="combobox"
        autocomplete="off"
        aria-autocomplete="list"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="listId"
        [attr.aria-activedescendant]="open() && active() >= 0 ? listId + '-' + active() : null"
        [formControl]="text"
        [placeholder]="placeholder()"
        (focus)="focused.set(true)"
        (blur)="onBlur()"
        (keydown)="onKeydown($event)"
      />
    </label>
    @if (open()) {
      <ul class="options" role="listbox" [id]="listId">
        @for (airport of results(); track airport.code; let i = $index) {
          <li
            role="option"
            [id]="listId + '-' + i"
            [attr.aria-selected]="i === active()"
            [class.active]="i === active()"
            (mousedown)="$event.preventDefault(); choose(airport)"
          >
            <strong>{{ airport.code }}</strong> {{ airport.city }} ·
            <span class="muted">{{ airport.name }}</span>
          </li>
        }
      </ul>
    }
    @if (control().touched && control().invalid) {
      <span class="field-error">Elige un aeropuerto de la lista</span>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.25rem;
      position: relative;
    }
    .options {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      z-index: 5;
      list-style: none;
      margin: 0.25rem 0 0;
      padding: 0.25rem;
      background: var(--surface);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      max-height: 16rem;
      overflow: auto;
    }
    li {
      padding: 0.5rem 0.6rem;
      border-radius: 0.5rem;
      cursor: pointer;
    }
    li.active,
    li:hover {
      background: color-mix(in srgb, var(--brand-primary) 10%, transparent);
    }
  `,
})
export class AirportAutocompleteComponent {
  private readonly api = inject(InterlineApi);

  readonly label = input.required<string>();
  readonly placeholder = input('Ciudad o código IATA');
  /** Control del formulario padre: contiene el código IATA seleccionado. */
  readonly control = input.required<FormControl<string>>();

  protected readonly inputId = `airport-${++nextId}`;
  protected readonly listId = `${this.inputId}-list`;
  protected readonly text = new FormControl('', { nonNullable: true });
  protected readonly focused = signal(false);
  protected readonly active = signal(-1);

  protected readonly results = toSignal(
    this.text.valueChanges.pipe(
      map((value) => value.trim()),
      debounceTime(300),
      distinctUntilChanged(),
      filter((query) => query.length >= 2 && query !== this.control().value),
      switchMap((query) =>
        this.api.searchAirports(query).pipe(catchError(() => of<Airport[]>([]))),
      ),
    ),
    { initialValue: [] as Airport[] },
  );

  protected readonly open = computed(() => this.focused() && this.results().length > 0);

  constructor() {
    // Si el control ya trae un código (p. ej. al volver a la búsqueda), se muestra en el input.
    effect(() => {
      const code = this.control().value;
      if (code) {
        this.text.setValue(code, { emitEvent: false });
      }
    });
  }

  protected choose(airport: Airport): void {
    this.control().setValue(airport.code);
    this.control().markAsTouched();
    this.text.setValue(`${airport.code} · ${airport.city}`, { emitEvent: false });
    this.focused.set(false);
    this.active.set(-1);
  }

  protected onBlur(): void {
    this.focused.set(false);
    this.control().markAsTouched();
  }

  protected onKeydown(event: KeyboardEvent): void {
    const results = this.results();
    if (!this.open()) {
      return;
    }
    if (event.key === 'ArrowDown') {
      this.active.update((i) => (i + 1) % results.length);
      event.preventDefault();
    } else if (event.key === 'ArrowUp') {
      this.active.update((i) => (i <= 0 ? results.length - 1 : i - 1));
      event.preventDefault();
    } else if (event.key === 'Enter' && this.active() >= 0) {
      this.choose(results[this.active()]);
      event.preventDefault();
    } else if (event.key === 'Escape') {
      this.focused.set(false);
    }
  }
}
