import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { AlertTriangle, CalendarRange, Edit3, FolderOpen, Package, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { api } from '../api';
import { ajuste, diaMes, factor, number, periodoCorto, periodoLargo, sku, todoElAnio } from '../format';
import { MAX_FACTOR_TEMPORADA } from '../limites';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Politica, Producto, Snapshot, SolicitudTemporada, Temporada } from '../types';
import { CalendarioTemporada } from '../components/CalendarioTemporada';
import { Badge, Button, Confirmation, Empty, ErrorMessage, Field, Modal } from '../components/ui';

const politicaLabels: Record<Politica['tipo'], string> = { PUNTO_PEDIDO: 'Punto de pedido', DEMANDA: 'Por demanda', ESTACIONAL: 'Estacional' };
const plural = (n: number, uno: string, varios: string) => `${number(n)} ${n === 1 ? uno : varios}`;

/** "Pan dulce", "Pan dulce y Budín" o "Pan dulce, Budín y 2 más". */
export function enumerar(nombres: string[], resto = 'más') {
  if (nombres.length <= 2) return nombres.join(' y ');
  return `${nombres.slice(0, 2).join(', ')} y ${nombres.length - 2} ${resto}`;
}

/** Artículos simples a los que llega la temporada y cuántos tienen política estacional (sólo presentación). */
function alcance(data: Snapshot, t: Pick<Temporada, 'stockIds' | 'categoriaId'>) {
  const deArticulos = t.categoriaId === null;
  const articulos = data.productos.filter(p => p.tipo === 'SIMPLE' && (deArticulos ? t.stockIds.includes(p.id) : p.categoriaId === t.categoriaId));
  const estacionales = articulos.filter(p => data.politicas[p.id]?.tipo === 'ESTACIONAL').length;
  if (deArticulos) {
    const nombres = t.stockIds.map(id => data.productos.find(p => p.id === id)?.nombre ?? sku(id));
    const nombre = enumerar(nombres);
    return { texto: `${nombres.length === 1 ? 'Artículo' : 'Artículos'}: ${nombre}`, nombre, plural: nombres.length > 1, articulos, estacionales };
  }
  const categoria = data.categorias.find(c => c.id === t.categoriaId)?.nombre ?? 'sin nombre';
  return { texto: `Categoría: ${categoria} · ${plural(articulos.length, 'artículo', 'artículos')}`, nombre: `Los artículos de ${categoria}`, plural: true, articulos, estacionales };
}
function aclaracion(t: Temporada) {
  if (todoElAnio(t)) return 'Todo el año';
  if (t.desdeDia === t.hastaDia && t.desdeMes === t.hastaMes) return 'Un solo día';
  return t.cruzaElAnio ? 'Cruza el año' : null;
}

export function SeasonsPanel() {
  const { data, busy, run, revision } = useStore();
  const [intento, setIntento] = useState(0);
  const temporadas = useResource('temporadas', signal => api.temporadas(signal), revision * 1000 + intento);
  const [editing, setEditing] = useState<Temporada | 'nueva' | null>(null);
  const [deleting, setDeleting] = useState<Temporada | null>(null);
  const nueva = useRef<HTMLSpanElement>(null);
  const enfocarNueva = useRef(false);
  // Corre después de desmontar la confirmación: la fila que la abrió ya no existe y el foco vuelve a la acción principal.
  useEffect(() => {
    if (deleting || !enfocarNueva.current) return;
    enfocarNueva.current = false;
    nueva.current?.querySelector('button')?.focus();
  }, [deleting]);
  if (!data) return null;
  const lista = (temporadas.data ?? []).slice().sort((a, b) => Number(b.vigente) - Number(a.vigente)
    || a.desdeMes - b.desdeMes || a.desdeDia - b.desdeDia || a.nombre.localeCompare(b.nombre, 'es'));
  const sinCatalogo = !data.productos.some(p => p.tipo === 'SIMPLE') && !data.categorias.length;
  const vigentes = lista.filter(t => t.vigente).length;
  const estado = temporadas.loading && temporadas.data ? 'Actualizando…'
    : temporadas.error && temporadas.data ? 'Puede estar desactualizada · Reintentá.'
    : `${plural(lista.length, 'temporada', 'temporadas')} · ${plural(vigentes, 'vigente hoy', 'vigentes hoy')}`;
  const reintentar = <Button icon={RefreshCw} onClick={() => setIntento(i => i + 1)} loading={temporadas.loading}>Reintentar</Button>;
  async function eliminar(t: Temporada) {
    await run(() => api.eliminarTemporada(t.id), 'Temporada eliminada. Fuera de otras temporadas, el factor vuelve a 1.', ['politicas']);
    enfocarNueva.current = true;
  }

  return <div className="settings-layout">
    <section className="panel">
      <div className="panel-heading"><div><h2>Temporadas de reposición</h2><p>Ajustan el objetivo de la política estacional en fechas que se repiten cada año.</p></div>
        <span ref={nueva}><Button variant="primary" icon={Plus} onClick={() => setEditing('nueva')} disabled={busy || sinCatalogo || (!temporadas.data && (temporadas.loading || !!temporadas.error))}>Nueva temporada</Button></span></div>
      {temporadas.data && <div className="table-toolbar"><span className="caption" aria-live="polite">{estado}</span>{temporadas.error && reintentar}</div>}
      {temporadas.error && !(temporadas.loading && !temporadas.data) && <div className="panel-body form-stack"><ErrorMessage error={temporadas.error.message ? temporadas.error : new Error('No pudimos cargar las temporadas. Revisá la conexión y reintentá.')} />{!temporadas.data && reintentar}</div>}
      {temporadas.loading && !temporadas.data ? <p className="panel-body muted loading-label">Cargando temporadas…</p>
        : sinCatalogo ? <Empty title="El catálogo está vacío" text="Creá un artículo o una categoría antes de cargar temporadas." />
        : temporadas.data && !lista.length ? <Empty title="Todavía no hay temporadas" text="Cargá una temporada para que la política estacional ajuste el objetivo en fechas como Navidad o el Día del Niño. Fuera de toda temporada, el factor es 1." />
        : temporadas.data && <div className="table-scroll" tabIndex={0} role="region" aria-label="Temporadas de reposición" aria-busy={temporadas.loading}><table className="seasons-table">
          <thead><tr><th scope="col">Temporada</th><th scope="col">Período</th><th scope="col" className="numeric">Factor</th><th scope="col">Estado</th><th scope="col"><span className="sr-only">Acciones</span></th></tr></thead>
          <tbody>{lista.map(t => { const a = alcance(data, t); const nota = aclaracion(t); return <tr key={t.id}>
            <td><div className="stacked-cell"><strong>{t.nombre}</strong><span className="caption">{a.texto}</span></div></td>
            <td><div className="stacked-cell"><span className="nowrap">{periodoCorto(t)}</span>{nota && <span className="caption">{nota}</span>}</div></td>
            <td className="numeric"><div className="stacked-cell"><strong>{factor(t.factor)}</strong><span className="caption">{ajuste(t.factor)}</span></div></td>
            <td><div className="stacked-cell"><span>{t.vigente ? <Badge tone="success">Vigente hoy</Badge> : <Badge>Fuera de temporada</Badge>}</span>
              {!a.estacionales && <span className="caption">Sin efecto: ningún artículo con política estacional</span>}</div></td>
            <td><span className="row-actions"><Button variant="ghost" className="icon-button" icon={Edit3} onClick={() => setEditing(t)} disabled={busy} aria-label={`Editar temporada ${t.nombre}`} title="Editar" />
              <Button variant="ghost" className="icon-button" icon={Trash2} onClick={() => setDeleting(t)} disabled={busy} aria-label={`Eliminar temporada ${t.nombre}`} /></span></td>
          </tr>; })}</tbody></table></div>}
    </section>
    <aside className="panel settings-help">
      <CalendarRange size={32} aria-hidden="true" />
      <h2>Cómo se elige el factor</h2>
      <p>Cada día, la política estacional usa la temporada vigente del artículo. Si no tiene, usa la de su categoría. Si no rige ninguna, el factor es 1 y se repone el objetivo base.</p>
      <p>Las fechas se repiten todos los años. Si el fin es anterior al inicio, la temporada cruza el año: del 15/11 al 06/01 va de noviembre a enero.</p>
      <p>Las temporadas de un mismo artículo o de una misma categoría no pueden superponerse.</p>
      <p>El objetivo del día es el objetivo base por el factor, redondeado hacia arriba. Sólo cambia en los artículos con política Estacional.</p>
      <a className="text-link" href="#/compras/reposicion">Configurar políticas</a>
    </aside>
    {editing && <SeasonForm existing={editing === 'nueva' ? undefined : editing} onClose={() => setEditing(null)} />}
    {deleting && <Confirmation danger title={`Eliminar temporada ${deleting.nombre}`} label="Eliminar temporada" onClose={() => setDeleting(null)} onConfirm={() => eliminar(deleting)}
      text={`${alcance(data, deleting).nombre} ${alcance(data, deleting).plural ? 'dejan' : 'deja'} de ajustar su objetivo del ${diaMes(deleting.desdeDia, deleting.desdeMes)} al ${diaMes(deleting.hastaDia, deleting.hastaMes)}. Los pedidos ya generados no cambian.`}>
      {deleting.vigente && <div className="message warning"><AlertTriangle size={24} aria-hidden="true" /><div>Está vigente hoy. Desde la próxima evaluación deja de aplicarse su factor.</div></div>}
    </Confirmation>}
  </div>;
}

type Valores = { nombre: string; alcance: 'ARTICULO' | 'CATEGORIA'; stockIds: number[]; categoriaId: string; desdeDia: string; desdeMes: string; hastaDia: string; hastaMes: string; factor: string };

/** Selección de varios artículos simples con buscador. */
function ArticlePicker({ articulos, seleccion, disabled, onChange }: { articulos: Producto[]; seleccion: number[]; disabled: boolean; onChange: (ids: number[]) => void }) {
  const [busqueda, setBusqueda] = useState('');
  const texto = busqueda.trim().toLocaleLowerCase('es');
  // Orden fijo (alfabético): marcar un artículo no lo mueve de lugar. Los elegidos siguen visibles al buscar.
  const visibles = articulos.filter(p => seleccion.includes(p.id) || !texto || p.nombre.toLocaleLowerCase('es').includes(texto) || sku(p.id).includes(texto));
  const cambiar = (id: number, marcado: boolean) => onChange(marcado ? [...seleccion, id] : seleccion.filter(s => s !== id));
  return <fieldset className="article-picker">
    <legend>Artículos</legend>
    <span className="field-hint">Prevalece sobre la temporada de su categoría. Sólo artículos simples: los combos se reponen por sus componentes.</span>
    <Field label="Buscar artículo"><input type="search" value={busqueda} disabled={disabled} placeholder="Nombre o código" onChange={e => setBusqueda(e.target.value)} /></Field>
    <div className="article-picker-list" role="group" aria-label="Artículos de la temporada">
      {visibles.map(p => <label key={p.id} className="checkbox-label"><input type="checkbox" checked={seleccion.includes(p.id)} disabled={disabled} onChange={e => cambiar(p.id, e.target.checked)} /><span>{p.nombre}</span><span className="caption">{sku(p.id)}</span></label>)}
      {!visibles.length && <p className="muted">Ningún artículo coincide con la búsqueda.</p>}
    </div>
    <span className="caption" aria-live="polite">{seleccion.length ? plural(seleccion.length, 'artículo elegido', 'artículos elegidos') : 'Elegí al menos un artículo.'}</span>
  </fieldset>;
}

export function SeasonForm({ existing, onClose }: { existing?: Temporada; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const [values, setValues] = useState<Valores>(() => ({
    nombre: existing?.nombre ?? '', alcance: existing?.categoriaId != null ? 'CATEGORIA' : 'ARTICULO',
    stockIds: existing?.stockIds ?? [], categoriaId: existing?.categoriaId != null ? String(existing.categoriaId) : '',
    desdeDia: existing ? String(existing.desdeDia) : '', desdeMes: existing ? String(existing.desdeMes) : '',
    hastaDia: existing ? String(existing.hastaDia) : '', hastaMes: existing ? String(existing.hastaMes) : '',
    factor: existing ? String(existing.factor) : '',
  }));
  const [error, setError] = useState<Error | null>(null);
  if (!data) return null;
  const set = (cambios: Partial<Valores>) => setValues(old => ({ ...old, ...cambios }));
  const simples = data.productos.filter(p => p.tipo === 'SIMPLE').sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  const categorias = data.categorias.slice().sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  const periodo = { desdeDia: Number(values.desdeDia), desdeMes: Number(values.desdeMes), hastaDia: Number(values.hastaDia), hastaMes: Number(values.hastaMes) };
  const periodoCompleto = Object.values(periodo).every(v => v > 0);
  const f = Number(values.factor);
  const factorValido = values.factor !== '' && f > 0 && f <= 10;
  const destino = values.alcance === 'ARTICULO'
    ? (values.stockIds.length ? alcance(data, { stockIds: values.stockIds, categoriaId: null }) : null)
    : (values.categoriaId ? alcance(data, { stockIds: [], categoriaId: Number(values.categoriaId) }) : null);
  let aviso: ReactNode = null;
  const sinEstacional = destino && values.alcance === 'ARTICULO' ? destino.articulos.filter(p => data.politicas[p.id]?.tipo !== 'ESTACIONAL') : [];
  if (sinEstacional.length === 1 && destino?.articulos.length === 1) {
    const tipo = data.politicas[sinEstacional[0].id]?.tipo;
    aviso = tipo
      ? <><strong>{sinEstacional[0].nombre}</strong> tiene política {politicaLabels[tipo]}: esta temporada no cambia sus pedidos hasta que la pases a Estacional.</>
      : <><strong>{sinEstacional[0].nombre}</strong> no tiene política de reposición: esta temporada no cambia sus pedidos hasta que le configures una Estacional.</>;
  } else if (sinEstacional.length) {
    aviso = <><strong>{enumerar(sinEstacional.map(p => p.nombre), sinEstacional.length - 2 === 1 ? 'artículo más' : 'artículos más')}</strong> {sinEstacional.length === 1 ? 'no tiene política Estacional: esta temporada no cambia sus pedidos hasta que la tenga.' : 'no tienen política Estacional: esta temporada no cambia sus pedidos hasta que la tengan.'}</>;
  } else if (destino && values.alcance === 'CATEGORIA' && !destino.estacionales) {
    aviso = <>Ningún artículo de <strong>{destino.nombre}</strong> tiene política Estacional: esta temporada no cambia sus pedidos por ahora.</>;
  }
  const hintCategoria = 'Rige en los artículos de la categoría que no tengan una temporada propia vigente.'
    + (destino && values.alcance === 'CATEGORIA' && destino.estacionales
      ? ` Con política Estacional: ${destino.estacionales} de ${plural(destino.articulos.length, 'artículo', 'artículos')}.` : '');

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    if (values.alcance === 'ARTICULO' && !values.stockIds.length) { setError(new Error('Elegí al menos un artículo para la temporada.')); return; }
    if (!periodoCompleto) { setError(new Error('Elegí en el calendario el día de inicio y el de fin.')); return; }
    const body: SolicitudTemporada = {
      nombre: values.nombre.trim(),
      stockIds: values.alcance === 'ARTICULO' ? values.stockIds : [],
      categoriaId: values.alcance === 'CATEGORIA' ? Number(values.categoriaId) : null,
      ...periodo, factor: f,
    };
    try {
      await run(() => api.guardarTemporada(body, existing?.id),
        `Temporada ${existing ? 'actualizada' : 'creada'}. Se aplica desde la próxima evaluación de reposición.`, ['politicas']);
      onClose();
    } catch (e) { setError(e as Error); }
  }

  return <Modal title={existing ? 'Editar temporada' : 'Nueva temporada'} description="Ajustá el objetivo de reposición en un período que se repite cada año." onClose={onClose}>
    <form onSubmit={submit}><div className="modal-body form-stack">
      <Field label="Nombre de la temporada" hint="Por ejemplo: Navidad y Reyes, Día del Niño."><input required maxLength={80} value={values.nombre} disabled={busy} onChange={e => set({ nombre: e.target.value })} /></Field>
      <fieldset><legend>Se aplica a</legend><div className="channel-options two">
        <label className={`channel-choice ${values.alcance === 'ARTICULO' ? 'selected' : ''}`}><input type="radio" name="alcance" value="ARTICULO" checked={values.alcance === 'ARTICULO'} disabled={busy} onChange={() => set({ alcance: 'ARTICULO' })} /><Package size={24} aria-hidden="true" />Artículos</label>
        <label className={`channel-choice ${values.alcance === 'CATEGORIA' ? 'selected' : ''}`}><input type="radio" name="alcance" value="CATEGORIA" checked={values.alcance === 'CATEGORIA'} disabled={busy || !categorias.length} onChange={() => set({ alcance: 'CATEGORIA' })} /><FolderOpen size={24} aria-hidden="true" />Una categoría</label>
      </div></fieldset>
      {values.alcance === 'ARTICULO'
        ? <ArticlePicker key="articulos" articulos={simples} seleccion={values.stockIds} disabled={busy} onChange={stockIds => set({ stockIds })} />
        : <Field key="categoria" label="Categoría" hint={hintCategoria}><select required value={values.categoriaId} disabled={busy} onChange={e => set({ categoriaId: e.target.value })}><option value="">Seleccioná una categoría</option>{categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}</select></Field>}
      {aviso && <div className="message warning"><AlertTriangle size={24} aria-hidden="true" /><div>{aviso}</div></div>}
      <div className="period-range">
        <CalendarioTemporada disabled={busy}
          desde={{ dia: Number(values.desdeDia), mes: Number(values.desdeMes) }} hasta={{ dia: Number(values.hastaDia), mes: Number(values.hastaMes) }}
          onChange={(desde, hasta) => set({ desdeDia: desde.dia ? String(desde.dia) : '', desdeMes: desde.mes ? String(desde.mes) : '',
            hastaDia: hasta.dia ? String(hasta.dia) : '', hastaMes: hasta.mes ? String(hasta.mes) : '' })} />
        <div aria-live="polite">{periodoCompleto && <p className="form-info">{periodoLargo(periodo)}</p>}</div>
      </div>
      <Field label="Factor de temporada" hint={!factorValido ? 'Mayor que 0 y hasta 10. Por ejemplo, 1,5 repone un 50 % más que el objetivo base.'
        : f === 1 ? 'Sin ajuste: repone el objetivo base.' : `Repone un ${ajuste(f)} que el objetivo base.`}>
        <input required type="number" inputMode="decimal" min="0.0001" max={MAX_FACTOR_TEMPORADA} step="0.0001" placeholder="1,5" value={values.factor} disabled={busy} onChange={e => set({ factor: e.target.value })} />
      </Field>
      <ErrorMessage error={error} />
    </div><footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Cancelar</Button><Button variant="primary" type="submit" loading={busy}>{existing ? 'Guardar cambios' : 'Crear temporada'}</Button></footer></form>
  </Modal>;
}

