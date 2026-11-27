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
  expect((await api.post('/digital/reservas', { stockId: 1, cantidad: 61, motivo: 'Prueba de aviso E2E' })).status()).toBe(201);
  await expect(async () => {
    const { mensajes } = await (await api.get('/integraciones')).json() as { mensajes: { canal: string; destino: string }[] };
    expect(mensajes.filter(m => m.canal === 'EMAIL').map(m => m.destino)).toEqual(expect.arrayContaining(['almacen@coto.example', 'jefe.almacen@coto.example']));
  }).toPass({ timeout: 15_000 });
});

test('temporadas: Compras carga una temporada por categoría y la política estacional muestra el factor de hoy', async ({ page }) => {
  await ingresar(page, '/#/compras/temporadas');
  const tabla = page.getByRole('region', { name: 'Temporadas de reposición' });
  // La temporada de ejemplo del pan dulce cruza el año.
  const navidad = tabla.getByRole('row').filter({ hasText: 'Navidad y Reyes' });
  await expect(navidad).toContainText('Artículo: Pan dulce COTO 500 g');
  await expect(navidad).toContainText('15/11 al 06/01');
  await expect(navidad).toContainText('Cruza el año');

  await page.getByRole('button', { name: 'Nueva temporada' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Nueva temporada' });
  await dialogo.getByLabel('Nombre de la temporada').fill('Invierno almacén E2E');
  await dialogo.getByText('Una categoría').click();
  await dialogo.getByLabel('Categoría', { exact: true }).selectOption({ label: 'Almacén' });
  // Calendario sin año: primer clic, inicio; segundo clic, fin.
  const mes = dialogo.getByLabel('Mes del calendario');
  await mes.selectOption('6');
  await dialogo.getByRole('button', { name: /^1 de junio/ }).click();
  await mes.selectOption('8');
  await dialogo.getByRole('button', { name: /^31 de agosto/ }).click();
  await expect(dialogo.getByText('Del 1 de junio al 31 de agosto de cada año.')).toBeVisible();
  await dialogo.getByLabel('Factor de temporada').fill('1.2');
  await dialogo.getByRole('button', { name: 'Crear temporada' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Temporada creada' })).toBeVisible();
  await expect(tabla.getByRole('row').filter({ hasText: 'Invierno almacén E2E' })).toContainText('Categoría: Almacén');

  // Superponer otra temporada de la misma categoría devuelve el detalle del backend y deja el modal abierto.
  await page.getByRole('button', { name: 'Nueva temporada' }).click();
  await dialogo.getByLabel('Nombre de la temporada').fill('Vacaciones de invierno');
  await dialogo.getByText('Una categoría').click();
  await dialogo.getByLabel('Categoría', { exact: true }).selectOption({ label: 'Almacén' });
  await mes.selectOption('7');
  await dialogo.getByRole('button', { name: /^10 de julio/ }).click();
  await dialogo.getByRole('button', { name: /^25 de julio/ }).click();
  await dialogo.getByLabel('Factor de temporada').fill('1.4');
  await dialogo.getByRole('button', { name: 'Crear temporada' }).click();
  await expect(dialogo.getByRole('alert')).toContainText('Se superpone con «Invierno almacén E2E» (01/06 al 31/08)');
  await dialogo.getByRole('button', { name: 'Cancelar' }).click();

  await page.getByRole('button', { name: 'Eliminar temporada Invierno almacén E2E' }).click();
  await page.getByRole('dialog', { name: 'Eliminar temporada Invierno almacén E2E' }).getByRole('button', { name: 'Eliminar temporada' }).click();
  await expect(tabla.getByRole('row').filter({ hasText: 'Invierno almacén E2E' })).toHaveCount(0);

  await page.goto('/#/compras/reposicion');
  await page.getByRole('button', { name: 'Configurar política de Pan dulce COTO 500 g' }).click();
  const politica = page.getByRole('dialog', { name: 'Política de reposición' });
  await expect(politica.getByLabel('Factor de temporada', { exact: true })).toHaveCount(0);
  const tarjeta = politica.getByRole('region', { name: 'Factor de temporada vigente' });
  await expect(tarjeta).toContainText('Objetivo de hoy');
  // Sea cual sea el día, el backend decide entre la temporada del pan dulce o ninguna.
  await expect(tarjeta).toContainText(/Hoy rige «Navidad y Reyes» · ×1,5|Hoy no rige ninguna temporada · ×1/);
});
