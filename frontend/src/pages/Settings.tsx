import { useState, type FormEvent } from 'react';
import { Bell, Edit3, Mail, MessageCircle, Plus, Save, Smartphone, Users } from 'lucide-react';
import { CategoriesPanel } from './Categories';
import { api } from '../api';
import { useStore } from '../store';
import { MAXIMO_EMAILS, validarLista } from '../emails';
import type { Canal, ConfiguracionAvisos, Contacto, ListaDestinatarios } from '../types';
import { Badge, Button, ErrorMessage, Empty, Field, Modal, PageHeading } from '../components/ui';

const channels = [{ value: 'EMAIL', name: 'Email', icon: Mail }, { value: 'SMS', name: 'SMS', icon: Smartphone }, { value: 'WHATSAPP', name: 'WhatsApp', icon: MessageCircle }] as const;

function NoticesForm({ initial }: { initial: ConfiguracionAvisos }) {
  const { busy, run } = useStore();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<Error | null>(null);
  const dirty = JSON.stringify(values) !== JSON.stringify(initial);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try { await run(() => api.guardarAvisos({ ...values, email: values.email.trim(), telefono: values.telefono.trim() }), 'Configuración guardada. Los próximos avisos usarán estos destinatarios.', ['configuracion']); }
    catch (e) { setError(e as Error); }
  }
  return (
    <form className="panel settings-form" onSubmit={submit}>
      <div className="panel-heading">
        <div>
          <h2>Respaldo de la sucursal</h2>
          <p>Se usa sólo cuando ni la categoría ni la lista general tienen emails. Admite SMS y WhatsApp.</p>
        </div>
        <Badge tone={dirty ? 'warning' : 'success'}>{dirty ? 'Sin guardar' : 'Guardado'}</Badge>
      </div>
      <div className="panel-body form-stack">
        <fieldset>
          <legend>Canal de notificación</legend>
          <div className="channel-options">
            {channels.map(({ value, name, icon: Icon }) => (
              <label className={`channel-choice ${values.canal === value ? 'selected' : ''}`} key={value}>
                <input type="radio" name="canal" value={value} checked={values.canal === value} disabled={busy} onChange={() => setValues(v => ({ ...v, canal: value as Canal }))} />
                <Icon size={24} aria-hidden="true" />
                {name}
              </label>
            ))}
          </div>
        </fieldset>
        {values.canal === 'EMAIL' ? (
          <Field key="email" label="Email de destino" hint="Destinatario compartido para los avisos por email.">
            <input type="email" required maxLength={200} value={values.email} disabled={busy} onChange={e => setValues(v => ({ ...v, email: e.target.value }))} />
          </Field>
        ) : (
          <Field key="telefono" label="Teléfono de destino" hint="Se utiliza para SMS y WhatsApp. Incluí el código de país.">
            <input type="tel" required maxLength={30} value={values.telefono} disabled={busy} onChange={e => setValues(v => ({ ...v, telefono: e.target.value }))} />
          </Field>
        )}
        <ErrorMessage error={error} />
        <div className="form-actions">
          <Button type="submit" icon={Save} variant="primary" loading={busy} disabled={!dirty}>
            Guardar configuración
          </Button>
        </div>
      </div>
    </form>
  );
}

function RecipientsForm({ lista, nombre, onClose }: { lista: ListaDestinatarios; nombre: string; onClose: () => void }) {
  const { busy, run } = useStore();
  const [texto, setTexto] = useState(lista.emails.join('\n'));
  const [error, setError] = useState<Error | null>(null);
  const { emails, error: invalida } = validarLista(texto);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    if (invalida) { setError(new Error(invalida)); return; }
    try {
      await run(
        () => api.guardarDestinatarios(lista.categoriaId, emails),
        emails.length ? 'Lista guardada. Los próximos avisos se enviarán a cada dirección.' : 'Lista vaciada. Los avisos usarán el siguiente destinatario disponible.',
        ['configuracion']
      );
      onClose();
    } catch (e) { setError(e as Error); }
  }

  function removeEmail(indexToRemove: number) {
    const nextEmails = emails.filter((_, i) => i !== indexToRemove);
    setTexto(nextEmails.join('\n'));
  }

  return (
    <Modal title={`Destinatarios · ${nombre}`} description="Cada aviso de stock bajo se envía por email a todas las direcciones de la lista." onClose={onClose}>
      <form onSubmit={submit}>
        <div className="modal-body form-stack">
          <Field label="Emails" hint={`Uno por línea o separados por coma. Hasta ${MAXIMO_EMAILS} direcciones.`}>
            <textarea rows={6} value={texto} disabled={busy} onChange={e => setTexto(e.target.value)} spellCheck={false} autoComplete="off" />
          </Field>
          {emails.length > 0 && (
            <div className="email-chip-list" role="list" aria-label="Direcciones cargadas">
              {emails.map((email, idx) => (
                <span key={`${email}-${idx}`} className="email-chip" role="listitem">
                  <Mail size={14} aria-hidden="true" />
                  <span>{email}</span>
                  <button
                    type="button"
                    className="email-chip-remove"
                    aria-label={`Quitar ${email}`}
                    onClick={() => removeEmail(idx)}
                    disabled={busy}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}
          <p className="form-info" aria-live="polite">
            {emails.length ? `${emails.length} ${emails.length === 1 ? 'dirección' : 'direcciones'}` : 'Lista vacía: se usará el siguiente destinatario disponible.'}
          </p>
          <ErrorMessage error={error} />
        </div>
        <footer className="modal-footer">
          <Button disabled={busy} onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="primary" loading={busy}>Guardar lista</Button>
        </footer>
      </form>
    </Modal>
  );
}

function Recipients() {
  const { data, busy } = useStore();
  const [editing, setEditing] = useState<{ lista: ListaDestinatarios; nombre: string } | null>(null);
  if (!data) return null;
  const lista = (categoriaId: number | null) => data.destinatarios.find(l => l.categoriaId === categoriaId) ?? { categoriaId, emails: [] };
  const general = lista(null);
  const filas = [
    { nombre: 'Lista general', detalle: 'Artículos sin lista de categoría', lista: general },
    ...data.categorias.map(c => ({ nombre: c.nombre, detalle: `${data.productos.filter(p => p.categoriaId === c.id).length} artículos`, lista: lista(c.id) }))
  ];

  return (
    <section className="panel recipients-panel" id="destinatarios">
      <div className="panel-heading">
        <div>
          <h2>Destinatarios de avisos</h2>
          <p>Listas de emails por categoría y una general para toda la sucursal.</p>
        </div>
        <Users size={24} aria-hidden="true" />
      </div>
      <div className="table-scroll" role="region" aria-label="Destinatarios de avisos" tabIndex={0}>
        <table>
          <thead>
            <tr>
              <th scope="col">Lista</th>
              <th scope="col">Emails</th>
              <th scope="col">Recibe</th>
              <th scope="col"><span className="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            {filas.map(f => {
              const usaGeneral = f.lista.categoriaId !== null && !f.lista.emails.length;
              return (
                <tr key={f.lista.categoriaId ?? 'general'}>
                  <td>
                    <div className="stacked-cell">
                      <strong>{f.nombre}</strong>
                      <span className="caption">{f.detalle}</span>
                    </div>
                  </td>
                  <td>
                    {f.lista.emails.length ? f.lista.emails.join(', ') : <span className="caption">Sin emails</span>}
                  </td>
                  <td>
                    {f.lista.emails.length ? (
                      <Badge tone="success">{f.lista.emails.length} {f.lista.emails.length === 1 ? 'dirección' : 'direcciones'}</Badge>
                    ) : usaGeneral && general.emails.length ? (
                      <Badge tone="info">Usa la lista general</Badge>
                    ) : (
                      <Badge tone="neutral">Usa el respaldo</Badge>
                    )}
                  </td>
                  <td>
                    <Button variant="ghost" icon={Edit3} disabled={busy} aria-label={`Editar destinatarios de ${f.nombre}`} onClick={() => setEditing({ lista: f.lista, nombre: f.nombre })}>
                      Editar
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {editing && <RecipientsForm key={editing.lista.categoriaId ?? 'general'} lista={editing.lista} nombre={editing.nombre} onClose={() => setEditing(null)} />}
    </section>
  );
}

function ContactForm({ contact, onClose }: { contact?: Contacto; onClose: () => void }) {
  const { busy, run } = useStore();
  const [values, setValues] = useState({ nombre: contact?.nombre || '', email: contact?.email || '', telefono: contact?.telefono || '' });
  const [error, setError] = useState<Error | null>(null);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    if (!values.email.trim() && !values.telefono.trim()) { setError(new Error('Cargá un email o un teléfono de contacto.')); return; }
    try {
      await run(() => api.guardarContacto({ nombre: values.nombre.trim(), email: values.email.trim(), telefono: values.telefono.trim() }, contact?.id), 'Contacto guardado.', ['configuracion']);
      onClose();
    } catch (e) { setError(e as Error); }
  }
  return (
    <Modal title={contact ? 'Editar contacto' : 'Nuevo contacto'} description="Agregá un sector, equipo o responsable que deba recibir avisos de stock." onClose={onClose}>
      <form onSubmit={submit}>
        <div className="modal-body form-stack">
          <Field label="Nombre del contacto" hint="Indicá el sector, equipo o responsable.">
            <input required maxLength={160} value={values.nombre} disabled={busy} onChange={e => setValues(v => ({ ...v, nombre: e.target.value }))} />
          </Field>
          <Field label="Email de contacto">
            <input type="email" maxLength={200} value={values.email} disabled={busy} onChange={e => setValues(v => ({ ...v, email: e.target.value }))} />
          </Field>
          <Field label="Teléfono de contacto" hint="Para SMS y WhatsApp. Incluí el código de país.">
            <input type="tel" maxLength={30} value={values.telefono} disabled={busy} onChange={e => setValues(v => ({ ...v, telefono: e.target.value }))} />
          </Field>
          <p className="field-hint">Cargá al menos un medio de contacto. El canal se elige en cada categoría.</p>
          <ErrorMessage error={error} />
        </div>
        <footer className="modal-footer">
          <Button disabled={busy} onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="primary" loading={busy}>Guardar contacto</Button>
        </footer>
      </form>
    </Modal>
  );
}

function Contacts() {
  const { data, busy } = useStore();
  const [editing, setEditing] = useState<Contacto | 'new' | null>(null);
  if (!data) return null;
  return (
    <section className="panel contacts-panel" id="contactos">
      <div className="panel-heading">
        <div>
          <h2>Contactos de respaldo</h2>
          <p>Se usan en categorías sin lista de emails y en asignaciones anteriores por artículo.</p>
        </div>
        <Button variant="primary" icon={Plus} disabled={busy} onClick={() => setEditing('new')}>
          Nuevo contacto
        </Button>
      </div>
      {data.contactos.length ? (
        <div className="table-scroll" role="region" aria-label="Agenda de contactos" tabIndex={0}>
          <table>
            <thead>
              <tr>
                <th scope="col">Contacto</th>
                <th scope="col">Email</th>
                <th scope="col">Teléfono</th>
                <th scope="col">Asignaciones</th>
                <th scope="col"><span className="sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody>
              {data.contactos.map(p => {
                const products = data.productos.filter(product => data.politicas[product.id]?.contactoId === p.id);
                const categories = data.categorias.filter(c => c.contactoId === p.id);
                const assignments = [...categories.map(c => `Categoría: ${c.nombre}`), ...products.map(product => product.nombre)];
                return (
                  <tr key={p.id}>
                    <td><strong>{p.nombre}</strong></td>
                    <td>{p.email || '—'}</td>
                    <td>{p.telefono || '—'}</td>
                    <td>{assignments.length ? assignments.join(', ') : <span className="caption">Sin asignar</span>}</td>
                    <td>
                      <Button variant="ghost" icon={Edit3} disabled={busy} aria-label={`Editar contacto ${p.nombre}`} onClick={() => setEditing(p)}>
                        Editar
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <Empty title="Todavía no hay contactos" text="Agregá contactos de los distintos sectores de Compras." />
      )}
      {editing && <ContactForm contact={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </section>
  );
}

export function SettingsPage({ tab = 'avisos' }: { tab?: string }) {
  const { data } = useStore();
  if (!data) return null;
  const categories = tab === 'categorias';
  return (
    <>
      <PageHeading
        eyebrow="Configuración"
        title={categories ? 'Categorías' : 'Alertas y avisos'}
        text={categories ? 'Organizá los artículos de la sucursal y sus avisos por sector.' : 'Definí quién recibe los avisos de stock bajo de cada categoría y de la sucursal.'}
      />
      <nav className="tabs" aria-label="Secciones de configuración">
        <a href="#/configuracion/avisos" className={!categories ? 'active' : ''} aria-current={!categories ? 'page' : undefined}>
          Avisos y contactos
        </a>
        <a href="#/configuracion/categorias" className={categories ? 'active' : ''} aria-current={categories ? 'page' : undefined}>
          Categorías
        </a>
      </nav>
      {categories ? (
        <CategoriesPanel />
      ) : (
        <>
          <Recipients />
          <div className="settings-layout">
            <NoticesForm key={JSON.stringify(data.avisos)} initial={data.avisos} />
            <aside className="panel settings-help">
              <Bell size={32} aria-hidden="true" />
              <h2>Cómo se elige el destinatario</h2>
              <p>Primero, la lista de emails de la categoría del artículo; si está vacía, la lista general.</p>
              <p>Si ninguna tiene emails, se usa el respaldo: el contacto anterior del artículo o de la categoría y, por último, el de la sucursal.</p>
              <p>El umbral de alerta de cada artículo se define en Compras → Reposición.</p>
              <p>Se genera un aviso por episodio de stock bajo. Al recuperar existencias, el artículo queda preparado para una nueva alerta.</p>
              <p>Los avisos pendientes conservan el destino con el que se crearon.</p>
              <a className="text-link" href="#/compras/reposicion">Configurar umbrales</a>
            </aside>
          </div>
          <Contacts />
        </>
      )}
    </>
  );
}
