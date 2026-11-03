import { expect, test, type Page } from '@playwright/test';

// Los recorridos comparten un backend demo recién iniciado; cada uno opera sobre artículos distintos.

async function ingresar(page: Page, ruta = '/') {
  await page.goto('/');
  await page.getByLabel('Usuario').fill('supervisor.demo');
  await page.getByLabel('Contraseña').fill('coto123');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('status').filter({ hasText: '¡Bienvenido' })).toBeVisible();
  if (ruta !== '/') await page.goto(ruta);
}

/** Llama a la API con el token de la sesión iniciada en el navegador. */
async function apiConSesion(page: Page) {
  const token = await page.evaluate(() => JSON.parse(sessionStorage.getItem('coto_stock_auth') || '{}').token as string);
  const headers = { Authorization: `Bearer ${token}` };
  return {
    get: (ruta: string) => page.request.get(`/api${ruta}`, { headers }),
    post: (ruta: string, data: unknown) => page.request.post(`/api${ruta}`, { headers, data }),
  };
}

test('reserva digital: confirma la operación y descuenta el disponible', async ({ page }) => {
  await ingresar(page, '/#/stock/digital');
  const fila = page.getByRole('region', { name: 'Stock y reservas digitales' }).getByRole('row').filter({ hasText: 'Aceite de girasol COTO 900 ml' });
  const disponible = fila.locator('td.available');
  await expect(disponible).toHaveText('35');

  await fila.getByRole('button', { name: 'Registrar operación de Aceite de girasol COTO 900 ml' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Registrar operación digital' });
  await expect(dialogo.getByLabel('Artículo')).toHaveValue('2');
  await dialogo.getByLabel('Tipo de operación').selectOption('RESERVA_DIGITAL');
  await dialogo.getByLabel('Cantidad').fill('2');
  await dialogo.getByLabel('Motivo').fill('Compra web de prueba E2E');
  await dialogo.getByRole('button', { name: 'Confirmar operación' }).click();

  await expect(page.getByRole('status').filter({ hasText: /Movimiento #\d+ confirmado/ })).toBeVisible();
  await expect(dialogo).toBeHidden();
  await expect(disponible).toHaveText('33');
  const ultimos = page.getByRole('region', { name: 'Movimientos digitales' }).getByRole('row').nth(1);
  await expect(ultimos).toContainText('Aceite de girasol COTO 900 ml');
  await expect(ultimos).toContainText('Compra web de prueba E2E');
});

test('recepción: recibe el pedido enviado por el centro de distribución', async ({ page }) => {
  await ingresar(page, '/#/recepcion');
  const pedido = page.locator('.arriving-list article').filter({ hasText: 'Pedido #0001' });
  // El pedido inicial pasa a "Enviado" cuando el reintento automático lo entrega al ERP simulado.
  await expect(async () => {
    await page.reload();
    await expect(pedido).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 30_000 });
  await expect(pedido).toContainText('Leche entera COTO 1 l');

  await pedido.getByRole('button', { name: 'Recibir pedido' }).click();
  const confirmacion = page.getByRole('dialog', { name: 'Recibir pedido #0001' });
  await confirmacion.getByRole('button', { name: 'Confirmar recepción' }).click();

  await expect(page.getByRole('status').filter({ hasText: /Pedido recibido\. Movimiento #\d+ confirmado\./ })).toBeVisible();
  await expect(pedido).toBeHidden();
  const ingreso = page.getByRole('region', { name: 'Últimos ingresos' }).getByRole('row').nth(1);
  await expect(ingreso).toContainText('Leche entera COTO 1 l');
  await expect(ingreso).toContainText('+30');
});

test('destinatarios: la lista de la categoría recibe un email por dirección', async ({ page }) => {
  await ingresar(page, '/#/configuracion/avisos');
  await page.getByRole('button', { name: 'Editar destinatarios de Almacén' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Destinatarios · Almacén' });
  await dialogo.getByLabel('Emails').fill('almacen@coto.example\nJefe.Almacen@coto.example');
  await expect(dialogo.getByText('2 direcciones')).toBeVisible();
  await dialogo.getByRole('button', { name: 'Guardar lista' }).click();
  await expect(dialogo).toBeHidden();
  const fila = page.getByRole('region', { name: 'Destinatarios de avisos' }).getByRole('row').filter({ hasText: 'Almacén' });
  await expect(fila).toContainText('almacen@coto.example, jefe.almacen@coto.example');

  // Arroz (Almacén) baja de 80 a 19 disponibles, por debajo de su umbral de 20.
  const api = await apiConSesion(page);
  expect((await api.post('/digital/reservas', { stockId: 1, cantidad: 61, motivo: 'Prueba de aviso E2E', usuario: 'digital.demo' })).status()).toBe(201);
  await expect(async () => {
    const { mensajes } = await (await api.get('/integraciones')).json() as { mensajes: { canal: string; destino: string }[] };
    expect(mensajes.filter(m => m.canal === 'EMAIL').map(m => m.destino)).toEqual(expect.arrayContaining(['almacen@coto.example', 'jefe.almacen@coto.example']));
  }).toPass({ timeout: 15_000 });
});
