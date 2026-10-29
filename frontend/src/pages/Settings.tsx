import { useState, type FormEvent } from 'react';
import { Bell, Edit3, Mail, MessageCircle, Plus, Save, Smartphone } from 'lucide-react';
import { CategoriesPanel } from './Categories';
import { api } from '../api';
import { useStore } from '../store';
import type { Canal, ConfiguracionAvisos, Contacto } from '../types';
import { Badge, Button, ErrorMessage, Empty, Field, Modal, PageHeading } from '../components/ui';

const channels = [{ value: 'EMAIL', name: 'Email', icon: Mail }, { value: 'SMS', name: 'SMS', icon: Smartphone }, { value: 'WHATSAPP', name: 'WhatsApp', icon: MessageCircle }] as const;
function NoticesForm({ initial }: { initial: ConfiguracionAvisos }) {
  const { busy, run } = useStore();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<Error | null>(null);
  const dirty = JSON.stringify(values) !== JSON.stringify(initial);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try { await run(() => api.guardarAvisos({ ...values, email: values.email.trim(), telefono: values.telefono.trim() }), 'Configuración guardada. Los próximos avisos usarán estos destinatarios.'); }
    catch (e) { setError(e as Error); }
  }
  return <form className="panel settings-form" onSubmit={submit}><div className="panel-heading"><div><h2>Destinatario general de la sucursal</h2><p>Se usa cuando el artículo y su categoría no tienen un contacto asignado.</p></div><Badge tone={dirty ? 'warning' : 'success'}>{dirty ? 'Sin guardar' : 'Guardado'}</Badge></div><div className="panel-body form-stack"><fieldset><legend>Canal de notificación</legend><div className="channel-options">{channels.map(({ value, name, icon: Icon }) => <label className={`channel-choice ${values.canal === value ? 'selected' : ''}`} key={value}><input type="radio" name="canal" value={value} checked={values.canal === value} disabled={busy} onChange={() => setValues(v => ({ ...v, canal: value as Canal }))} /><Icon size={24} aria-hidden="true" />{name}</label>)}</div></fieldset>
    {values.canal === 'EMAIL' ? <Field key="email" label="Email de destino" hint="Destinatario compartido para los avisos por email."><input type="email" required maxLength={200} value={values.email} disabled={busy} onChange={e => setValues(v => ({ ...v, email: e.target.value }))} /></Field> :
    <Field key="telefono" label="Teléfono de destino" hint="Se utiliza para SMS y WhatsApp. Incluí el código de país."><input type="tel" required maxLength={30} value={values.telefono} disabled={busy} onChange={e => setValues(v => ({ ...v, telefono: e.target.value }))} /></Field>}<ErrorMessage error={error} />
    <div className="form-actions"><Button type="submit" icon={Save} variant="primary" loading={busy} disabled={!dirty}>Guardar configuración</Button></div></div></form>;
}
function ContactForm({ contact, onClose }: { contact?: Contacto; onClose: () => void }) {
  const { busy, run } = useStore();
  const [values, setValues] = useState({ nombre: contact?.nombre || '', email: contact?.email || '', telefono: contact?.telefono || '' });
  const [error, setError] = useState<Error | null>(null);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    if (!values.email.trim() && !values.telefono.trim()) { setError(new Error('Cargá un email o un teléfono de contacto.')); return; }
    try {
      await run(() => api.guardarContacto({ nombre: values.nombre.trim(), email: values.email.trim(), telefono: values.telefono.trim() }, contact?.id), 'Contacto guardado.');
      onClose();
    } catch (e) { setError(e as Error); }
  }
  return <Modal title={contact ? 'Editar contacto' : 'Nuevo contacto'} description="Agregá un sector, equipo o responsable que deba recibir avisos de stock." onClose={onClose}><form onSubmit={submit}><div className="modal-body form-stack">
    <Field label="Nombre del contacto" hint="Indicá el sector, equipo o responsable."><input required maxLength={160} value={values.nombre} disabled={busy} onChange={e => setValues(v => ({ ...v, nombre: e.target.value }))} /></Field>
    <Field label="Email de contacto"><input type="email" maxLength={200} value={values.email} disabled={busy} onChange={e => setValues(v => ({ ...v, email: e.target.value }))} /></Field>
    <Field label="Teléfono de contacto" hint="Para SMS y WhatsApp. Incluí el código de país."><input type="tel" maxLength={30} value={values.telefono} disabled={busy} onChange={e => setValues(v => ({ ...v, telefono: e.target.value }))} /></Field>
    <p className="field-hint">Cargá al menos un medio de contacto. El canal se elige para cada producto.</p><ErrorMessage error={error} />
  </div><footer className="modal-footer"><Button disabled={busy} onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" loading={busy}>Guardar contacto</Button></footer></form></Modal>;
}
function Contacts() {
  const { data, busy } = useStore();
  const [editing, setEditing] = useState<Contacto | 'new' | null>(null);
  if (!data) return null;
  return <section className="panel contacts-panel" id="contactos"><div className="panel-heading"><div><h2>Contactos</h2><p>Elegí estos contactos en Compras → Reposición → Configurar artículo.</p></div><Button variant="primary" icon={Plus} disabled={busy} onClick={() => setEditing('new')}>Nuevo contacto</Button></div>
    {data.contactos.length ? <div className="table-scroll" role="region" aria-label="Agenda de contactos" tabIndex={0}><table><thead><tr><th scope="col">Contacto</th><th scope="col">Email</th><th scope="col">Teléfono</th><th scope="col">Asignaciones</th><th scope="col"><span className="sr-only">Acciones</span></th></tr></thead><tbody>{data.contactos.map(p => {
      const products = data.productos.filter(product => data.politicas[product.id]?.contactoId === p.id);
      const categories = data.categorias.filter(c => c.contactoId === p.id);
      const assignments = [...categories.map(c => `Categoría: ${c.nombre}`), ...products.map(product => product.nombre)];
      return <tr key={p.id}><td><strong>{p.nombre}</strong></td><td>{p.email || '—'}</td><td>{p.telefono || '—'}</td><td>{assignments.length ? assignments.join(', ') : <span className="caption">Sin asignar</span>}</td><td><Button variant="ghost" icon={Edit3} disabled={busy} aria-label={`Editar contacto ${p.nombre}`} onClick={() => setEditing(p)}>Editar</Button></td></tr>;
    })}</tbody></table></div> : <Empty title="Todavía no hay contactos" text="Agregá contactos de los distintos sectores de Compras." />}
    {editing && <ContactForm contact={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
  </section>;
}
export function SettingsPage({ tab = 'avisos' }: { tab?: string }) {
  const { data } = useStore();
  if (!data) return null;
  const categories = tab === 'categorias';
  return <><PageHeading eyebrow="Configuración" title={categories ? 'Categorías' : 'Alertas y avisos'} text={categories ? 'Organizá los artículos de la sucursal y sus avisos por sector.' : 'Administrá el destinatario general y los contactos de los sectores de la sucursal.'} /><nav className="tabs" aria-label="Secciones de configuración"><a href="#/configuracion/avisos" className={!categories ? 'active' : ''} aria-current={!categories ? 'page' : undefined}>Avisos y contactos</a><a href="#/configuracion/categorias" className={categories ? 'active' : ''} aria-current={categories ? 'page' : undefined}>Categorías</a></nav>{categories ? <CategoriesPanel /> : <><div className="settings-layout"><NoticesForm key={JSON.stringify(data.avisos)} initial={data.avisos} /><aside className="panel settings-help"><Bell size={32} aria-hidden="true" /><h2>Avisos por producto</h2><p>En Compras → Reposición, configurá el umbral, el contacto y el canal de aviso de cada artículo.</p><p>Se prioriza el contacto del artículo, luego el de su categoría y, por último, el general de la sucursal.</p><p>Se genera un aviso por episodio de stock bajo. Al recuperar existencias, el artículo queda preparado para una nueva alerta.</p><p>Los avisos pendientes conservan el destino con el que se crearon.</p><a className="text-link" href="#/compras/reposicion">Configurar artículos</a></aside></div><Contacts /></>}</>;
}
