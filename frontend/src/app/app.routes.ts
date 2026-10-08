import { Routes } from '@angular/router';

/** Rutas de primer nivel: cada feature se carga de forma lazy. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'booking' },
  {
    path: 'booking',
    loadChildren: () =>
      import('./features/booking-flow/booking-flow.routes').then((m) => m.BOOKING_FLOW_ROUTES),
  },
  {
    path: 'manage',
    loadChildren: () => import('./features/manage/manage.routes').then((m) => m.MANAGE_ROUTES),
  },
  { path: '**', redirectTo: 'booking' },
];
