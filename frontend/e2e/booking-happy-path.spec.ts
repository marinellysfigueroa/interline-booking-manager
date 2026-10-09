import { expect, test } from '@playwright/test';

test.describe('Flujo de reserva interline', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/booking/search?tenant=AV');
    await page.evaluate(() => sessionStorage.clear());
  });

  test('BOG → FCO en un solo ticket: buscar, elegir, pasajeros, pagar y gestionar', async ({
    page,
  }) => {
    await expect(page.getByRole('banner')).toContainText('Aerolíneas Cóndor');

    // 1. Buscar (autocompletado con debounce)
    await page.getByRole('combobox', { name: 'Origen' }).fill('bog');
    await page.getByRole('option', { name: /BOG/ }).click();
    await page.getByRole('combobox', { name: 'Destino' }).fill('roma');
    await page.getByRole('option', { name: /FCO/ }).click();
    await page.getByRole('button', { name: 'Buscar vuelos' }).click();

    // 2. Elegir oferta (AV26 BOG-MAD + IB3234 MAD-FCO, validada por AV)
    await expect(page).toHaveURL(/\/booking\/offers$/);
    await expect(page.getByText('Ticket único').first()).toBeVisible();
    await expect(page.getByText('IB3234')).toBeVisible();
    await page
      .getByRole('button', { name: /^Elegir oferta de AV/ })
      .first()
      .click();

    // 3. Pasajeros
    await expect(page).toHaveURL(/\/booking\/passengers$/);
    await page.getByLabel('Nombre del pasajero 1').fill('Ana');
    await page.getByLabel('Apellido del pasajero 1').fill('Pérez');
    await page.getByLabel('Fecha de nacimiento del pasajero 1').fill('1990-04-12');
    await page.getByLabel('Email').fill('ana.perez@example.com');
    await page.getByRole('button', { name: 'Continuar al pago' }).click();

    // 4. Pago: la reserva ya existe (HELD); recargar no pierde el progreso
    await expect(page).toHaveURL(/\/booking\/payment$/);
    const locator = (await page.locator('.locator').first().textContent())!.trim();
    expect(locator).toMatch(/^[A-HJ-NP-Z2-9]{6}$/);
    await page.reload();
    await expect(page).toHaveURL(/\/booking\/payment$/);
    await expect(page.locator('.locator').first()).toHaveText(locator);

    await page.getByRole('button', { name: 'Pagar y emitir' }).click();

    // 5. Confirmación con ticket de la validadora (prefijo 134)
    await expect(page).toHaveURL(/\/booking\/confirmation$/);
    await expect(page.getByRole('heading', { name: '¡Reserva emitida!' })).toBeVisible();
    await expect(page.getByText(/134\d{10}/)).toBeVisible();

    // Los guards no dejan volver a un paso anterior
    await page.goto('/booking/passengers');
    await expect(page).toHaveURL(/\/booking\/confirmation$/);

    // Gestión: detalle con estado y línea de tiempo
    await page.getByRole('link', { name: 'Gestionar reserva' }).click();
    await expect(page).toHaveURL(new RegExp(`/manage/${locator}$`));
    await expect(page.getByRole('heading', { name: `Reserva ${locator}` })).toBeVisible();
    const timeline = page.getByRole('list', { name: 'Línea de tiempo de la reserva' });
    await expect(timeline.getByText('Emitida')).toBeVisible();
    await expect(timeline.getByText('Pago autorizado')).toBeVisible();

    // Listado filtrado por estado
    await page.getByRole('link', { name: '← Volver al listado' }).click();
    await page.getByRole('button', { name: 'Emitida' }).click();
    await expect(page.getByRole('link', { name: locator })).toBeVisible();
  });

  test('el theming white-label cambia en runtime al elegir otra aerolínea', async ({ page }) => {
    const primary = () =>
      page.evaluate(() =>
        getComputedStyle(document.documentElement).getPropertyValue('--brand-primary').trim(),
      );
    expect(await primary()).toBe('#0f766e');

    await page.getByLabel('Aerolínea (white-label)').selectOption('LA');

    await expect(page.getByRole('banner')).toContainText('Pacífico Air');
    expect(await primary()).toBe('#3730a3');
    await expect(page).toHaveTitle(/Pacífico Air/);

    // La elección se recuerda al recargar y el selector la refleja
    await page.goto('/booking/search');
    await expect(page.getByRole('banner')).toContainText('Pacífico Air');
    await expect(page.getByLabel('Aerolínea (white-label)')).toHaveValue('LA');
  });

  test('no se puede saltar directamente al pago', async ({ page }) => {
    await page.goto('/booking/payment');
    await expect(page).toHaveURL(/\/booking\/search$/);
  });
});
