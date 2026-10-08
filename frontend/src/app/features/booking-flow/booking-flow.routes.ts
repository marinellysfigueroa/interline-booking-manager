import { Routes } from '@angular/router';
import { BookingFlowShellComponent } from './booking-flow.shell';
import { BookingFlowStore } from './booking-flow.store';
import { stepGuard } from './guards/step.guard';

/**
 * Una ruta lazy por paso. El store se provee en la ruta padre, así lo comparten todos los
 * pasos (y sus guards) mientras el usuario está dentro de /booking.
 */
export const BOOKING_FLOW_ROUTES: Routes = [
  {
    path: '',
    component: BookingFlowShellComponent,
    providers: [BookingFlowStore],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'search' },
      {
        path: 'search',
        title: 'Buscar vuelos',
        canActivate: [stepGuard('search')],
        loadComponent: () => import('./steps/search.page').then((m) => m.SearchPage),
      },
      {
        path: 'offers',
        title: 'Elegir oferta',
        canActivate: [stepGuard('offers')],
        loadComponent: () => import('./steps/offers.page').then((m) => m.OffersPage),
      },
      {
        path: 'passengers',
        title: 'Pasajeros',
        canActivate: [stepGuard('passengers')],
        loadComponent: () => import('./steps/passengers.page').then((m) => m.PassengersPage),
      },
      {
        path: 'payment',
        title: 'Pago',
        canActivate: [stepGuard('payment')],
        loadComponent: () => import('./steps/payment.page').then((m) => m.PaymentPage),
      },
      {
        path: 'confirmation',
        title: 'Confirmación',
        canActivate: [stepGuard('confirmation')],
        loadComponent: () => import('./steps/confirmation.page').then((m) => m.ConfirmationPage),
      },
    ],
  },
];
