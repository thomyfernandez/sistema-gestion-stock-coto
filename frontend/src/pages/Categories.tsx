import { useState, type FormEvent } from 'react';
import { Edit3, Plus } from 'lucide-react';
import { api } from '../api';
import { useStore } from '../store';
import type { Canal, Categoria } from '../types';
import { Button, Empty, ErrorMessage, Field, Modal } from '../components/ui';

function CategoryForm({ category, onClose }: { category?: Categoria; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const [values, setValues] = useState<Omit<Categoria, 'id'>>({ nombre: category?.nombre || '', contactoId: category?.contactoId ?? null, canalAlerta: category?.canalAlerta ?? null });
  const [error, setError] = useState<Error | null>(null);
  const contact = data?.contactos.find(c => c.id === values.contactoId);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try { await run(() => api.guardarCategoria({ ...values, nombre: values.nombre.trim() }, category?.id), 'Categoría guardada.'); onClose(); }
    catch (e) { setError(e as Error); }
  }
  return <Modal title={category ? 'Editar categoría' : 'Nueva categoría'} description="Organizá los artículos y definí quién recibe sus avisos." onClose={onClose}><form onSubmit={submit}><div className="modal-body form-stack">
    <Field label="Nombre de la categoría"><input required maxLength={80} value={values.nombre} disabled={busy} onChange={e => setValues(v => ({ ...v, nombre: e.target.value }))} /></Field>
    <Field label="Contacto para los avisos" hint="Se aplica a los artículos que no tienen un contacto particular."><select value={values.contactoId ?? ''} disabled={busy} onChange={e => { const next = data?.contactos.find(c => c.id === Number(e.target.value)); setValues(v => ({ ...v, contactoId: next?.id ?? null, canalAlerta: next ? next.email ? 'EMAIL' : 'WHATSAPP' : null })); }}><option value="">Destinatario general de la sucursal</option>{data?.contactos.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}</select></Field>
    {contact && <><Field label="Canal del aviso"><select required value={values.canalAlerta || ''} disabled={busy} onChange={e => setValues(v => ({ ...v, canalAlerta: e.target.value as Canal }))}><option value="EMAIL" disabled={!contact.email}>Email{!contact.email ? ' · sin email cargado' : ''}</option><option value="SMS" disabled={!contact.telefono}>SMS{!contact.telefono ? ' · sin teléfono cargado' : ''}</option><option value="WHATSAPP" disabled={!contact.telefono}>WhatsApp{!contact.telefono ? ' · sin teléfono cargado' : ''}</option></select></Field><p className="form-info">{contact.nombre} · {values.canalAlerta}: {values.canalAlerta === 'EMAIL' ? contact.email : contact.telefono}</p></>}
    <ErrorMessage error={error} /></div><footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Cancelar</Button><Button type="submit" variant="primary" loading={busy}>Guardar categoría</Button></footer></form></Modal>;
}
export function CategoriesPanel() {
  const { data, busy } = useStore();
  const [editing, setEditing] = useState<Categoria | 'new' | null>(null);
  if (!data) return null;
  return <><section className="panel categories-panel"><div className="panel-heading"><div><h2>Categorías de productos</h2><p>Clasificá el catálogo por sector y compartí el destinatario de sus avisos.</p></div><Button variant="primary" icon={Plus} disabled={busy} onClick={() => setEditing('new')}>Nueva categoría</Button></div>
    {data.categorias.length ? <div className="table-scroll" role="region" aria-label="Categorías de productos" tabIndex={0}><table><thead><tr><th scope="col">Categoría</th><th scope="col" className="numeric">Artículos</th><th scope="col">Avisar a</th><th scope="col">Canal</th><th scope="col"><span className="sr-only">Acciones</span></th></tr></thead><tbody>{data.categorias.map(c => <tr key={c.id}><td><strong>{c.nombre}</strong></td><td className="numeric">{data.productos.filter(p => p.categoriaId === c.id).length}</td><td>{data.contactos.find(contact => contact.id === c.contactoId)?.nombre || 'Destinatario general de la sucursal'}</td><td>{c.canalAlerta || data.avisos.canal}</td><td><Button variant="ghost" icon={Edit3} disabled={busy} aria-label={`Editar categoría ${c.nombre}`} onClick={() => setEditing(c)}>Editar</Button></td></tr>)}</tbody></table></div> : <Empty title="Todavía no hay categorías" text="Creá una categoría para organizar tus productos." />}
  </section><p className="page-footnote">Podés cambiar la categoría de un artículo desde su detalle en Stock o en Productos y combos.</p>{editing && <CategoryForm category={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}</>;
}
