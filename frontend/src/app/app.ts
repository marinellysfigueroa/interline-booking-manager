import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ToastsComponent } from './core/notifications/toasts.component';
import { ThemeService } from './core/theming/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastsComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly theme = inject(ThemeService);

  protected onTenantChange(event: Event): void {
    this.theme.select((event.target as HTMLSelectElement).value);
  }
}
