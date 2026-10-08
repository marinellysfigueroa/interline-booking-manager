import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { BookingFlowStore, FlowStep } from '../booking-flow.store';

/**
 * Guard funcional que impide saltar pasos: si el paso no está permitido redirige al más
 * avanzado posible (≈ un HandlerInterceptor que redirige, pero en el router del cliente).
 */
export function stepGuard(step: FlowStep): CanActivateFn {
  return () => {
    const store = inject(BookingFlowStore);
    return store.canEnter(step) || inject(Router).createUrlTree(['/booking', store.furthestStep()]);
  };
}
