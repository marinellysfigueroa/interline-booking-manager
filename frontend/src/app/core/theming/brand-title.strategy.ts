import { Injectable, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { ThemeService } from './theme.service';

/**
 * Título de pestaña "<paso> · <aerolínea>". El título de la ruta se guarda en un signal y un
 * `effect` lo recompone también cuando cambia el tema (no solo al navegar).
 */
@Injectable()
export class BrandTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly theme = inject(ThemeService);
  private readonly pageTitle = signal<string | undefined>(undefined);

  constructor() {
    super();
    effect(() => {
      const page = this.pageTitle();
      const brand = this.theme.theme().name;
      this.title.setTitle(page ? `${page} · ${brand}` : brand);
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.pageTitle.set(this.buildTitle(snapshot));
  }
}
