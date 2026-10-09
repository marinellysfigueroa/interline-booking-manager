import { Injectable, computed, signal } from '@angular/core';

export interface Notification {
  id: number;
  kind: 'error' | 'success' | 'info';
  title: string;
  detail: string;
  correlationId?: string;
}

/** Avisos globales (toasts). Store mínimo con signals: estado privado, lectura pública. */
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly items = signal<Notification[]>([]);
  private nextId = 1;

  readonly notifications = this.items.asReadonly();
  readonly hasNotifications = computed(() => this.items().length > 0);

  error(title: string, detail: string, correlationId?: string): void {
    this.push({ kind: 'error', title, detail, correlationId });
  }

  success(title: string, detail = ''): void {
    this.push({ kind: 'success', title, detail });
  }

  dismiss(id: number): void {
    this.items.update((items) => items.filter((n) => n.id !== id));
  }

  private push(notification: Omit<Notification, 'id'>): void {
    const id = this.nextId++;
    this.items.update((items) => [...items.slice(-3), { ...notification, id }]);
    setTimeout(() => this.dismiss(id), 8000);
  }
}
