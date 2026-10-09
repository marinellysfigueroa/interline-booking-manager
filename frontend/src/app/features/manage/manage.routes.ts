import { Routes } from '@angular/router';
import { ManageStore } from './manage.store';

export const MANAGE_ROUTES: Routes = [
  {
    path: '',
    providers: [ManageStore],
    children: [
      {
        path: '',
        title: 'Mis reservas',
        loadComponent: () => import('./bookings-list.page').then((m) => m.BookingsListPage),
      },
      {
        path: ':locator',
        title: 'Detalle de reserva',
        loadComponent: () => import('./booking-detail.page').then((m) => m.BookingDetailPage),
      },
    ],
  },
];
