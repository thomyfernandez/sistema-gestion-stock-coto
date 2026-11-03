import { useEffect, useState, type KeyboardEvent } from 'react';
import { AlertCircle, ChevronDown, ChevronUp, Mail, RotateCcw, Save, X } from 'lucide-react';
import { api } from '../api';
import { useStore } from '../store';
import { Badge, Button, ErrorMessage } from '../components/ui';

const EMAIL_REGEX = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function GeneralRecipientsCard() {
  const { data, busy, run } = useStore();
  const serverEmails = data?.destinatarios?.general || [];
  const [emails, setEmails] = useState<string[]>(serverEmails);
  const [input, setInput] = useState('');
  const [inputError, setInputError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<Error | null>(null);

  useEffect(() => {
    setEmails(data?.destinatarios?.general || []);
  }, [data?.destinatarios]);

  const dirty = JSON.stringify(emails) !== JSON.stringify(serverEmails);

  function agregarEmail() {
    const val = input.trim();
    setInputError(null);
    if (!val) {
      setInputError('Escribí una dirección de correo antes de agregarla.');
      return;
    }
    if (!EMAIL_REGEX.test(val)) {
      setInputError('Ingresá un correo electrónico con formato válido (ejemplo@coto.com.ar).');
      return;
    }
    if (emails.some(e => e.toLowerCase() === val.toLowerCase())) {
      setInputError('Esta dirección de correo ya figura en la lista.');
      return;
    }
    setEmails(prev => [...prev, val]);
    setInput('');
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      e.preventDefault();
      agregarEmail();
    }
  }

  function eliminarEmail(emailAEliminar: string) {
    setEmails(prev => prev.filter(e => e !== emailAEliminar));
  }

  function descartar() {
    setEmails(serverEmails);
    setInput('');
    setInputError(null);
    setApiError(null);
  }

  async function guardar() {
    setApiError(null);
    try {
      await run(
        () => api.guardarDestinatariosGeneral(emails),
        'Lista general guardada. Los próximos avisos usarán estas direcciones.'
      );
    } catch (e) {
      setApiError(e as Error);
    }
  }

  return (
    <section className="panel" aria-labelledby="general-recipients-title">
      <div className="panel-heading">
        <div>
          <h2 id="general-recipients-title">Lista general de la sucursal</h2>
          <p>Destinatarios predeterminados para todos los artículos que no cuenten con una lista propia en su categoría o que no tengan categoría asignada.</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)' }}>
          <Badge tone={dirty ? 'warning' : 'success'}>
            {dirty ? 'Sin guardar' : 'Guardado'}
          </Badge>
          <Badge tone="neutral">
            {emails.length} {emails.length === 1 ? 'email' : 'emails'}
          </Badge>
        </div>
      </div>

      <div className="panel-body form-stack">
        {emails.length === 0 && (
          <div className="message warning" role="alert" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)' }}>
            <AlertCircle size={24} aria-hidden="true" />
            <p style={{ margin: 0 }}>
              La sucursal no tiene emails en su lista general. Si un artículo de una categoría sin correos cae bajo umbral, no se enviarán alertas.
            </p>
          </div>
        )}

        <div className="email-input-group">
          <div style={{ flex: 1 }}>
            <label htmlFor="general-email-input" className="field-label" style={{ display: 'block', marginBottom: 'var(--space-half)', fontWeight: 'var(--weight-semibold)', fontSize: 'var(--body-medium)' }}>
              Nuevo correo electrónico
            </label>
            <input
              id="general-email-input"
              type="email"
              placeholder="ejemplo@coto.com.ar"
              value={input}
              disabled={busy}
              onChange={e => { setInput(e.target.value); setInputError(null); }}
              onKeyDown={handleKeyDown}
              aria-describedby={inputError ? 'general-email-error' : undefined}
              aria-invalid={inputError ? 'true' : 'false'}
              style={{ width: '100%', minHeight: '44px', padding: '10px 14px', border: '1px solid var(--neutral-200)', borderRadius: 'var(--radius-small)', background: 'var(--neutral-00)', fontSize: 'var(--body-medium)' }}
            />
            {inputError && (
              <p id="general-email-error" className="field-hint" role="alert" style={{ color: 'var(--danger-800)', marginTop: '4px', fontWeight: 'var(--weight-semibold)' }}>
                {inputError}
              </p>
            )}
          </div>
          <Button
            type="button"
            variant="secondary"
            disabled={busy || !input.trim()}
            onClick={agregarEmail}
          >
            Agregar dirección
          </Button>
        </div>

        <div className="email-chip-list" role="list" aria-label="Correos de la lista general">
          {emails.map(email => (
            <span key={email} className="email-chip" role="listitem">
              <Mail size={16} aria-hidden="true" style={{ color: 'var(--neutral-700)' }} />
              <span>{email}</span>
              <button
                type="button"
                className="email-chip-remove"
                aria-label={`Eliminar correo ${email} de la lista general`}
                disabled={busy}
                onClick={() => eliminarEmail(email)}
              >
                <X size={14} aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>

        <ErrorMessage error={apiError} />

        <div className="form-actions" style={{ display: 'flex', gap: 'var(--space-1)', marginTop: 'var(--space-1)' }}>
          <Button
            type="button"
            variant="primary"
            icon={Save}
            loading={busy}
            disabled={!dirty || busy}
            onClick={guardar}
          >
            Guardar lista general
          </Button>
          {dirty && (
            <Button
              type="button"
              variant="secondary"
              disabled={busy}
              onClick={descartar}
            >
              Descartar cambios
            </Button>
          )}
        </div>
      </div>
    </section>
  );
}

function CategoryRecipientsCard({
  category,
  productCount,
  generalCount,
}: {
  category: { id: number; nombre: string };
  productCount: number;
  generalCount: number;
}) {
  const { data, busy, run } = useStore();
  const serverEmails = data?.destinatarios?.porCategoria?.[category.id] || [];
  const [emails, setEmails] = useState<string[]>(serverEmails);
  const [input, setInput] = useState('');
  const [inputError, setInputError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<Error | null>(null);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    setEmails(data?.destinatarios?.porCategoria?.[category.id] || []);
  }, [data?.destinatarios, category.id]);

  const hasSpecific = serverEmails.length > 0;
  const localHasSpecific = emails.length > 0;
  const dirty = JSON.stringify(emails) !== JSON.stringify(serverEmails);

  function agregarEmail() {
    const val = input.trim();
    setInputError(null);
    if (!val) {
      setInputError('Escribí una dirección de correo antes de agregarla.');
      return;
    }
    if (!EMAIL_REGEX.test(val)) {
      setInputError('Ingresá un correo electrónico con formato válido (ejemplo@coto.com.ar).');
      return;
    }
    if (emails.some(e => e.toLowerCase() === val.toLowerCase())) {
      setInputError('Esta dirección de correo ya figura en la lista.');
      return;
    }
    setEmails(prev => [...prev, val]);
    setInput('');
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      e.preventDefault();
      agregarEmail();
    }
  }

  function eliminarEmail(emailAEliminar: string) {
    setEmails(prev => prev.filter(e => e !== emailAEliminar));
  }

  function descartar() {
    setEmails(serverEmails);
    setInput('');
    setInputError(null);
    setApiError(null);
  }

  async function guardar() {
    setApiError(null);
    try {
      await run(
        () => api.guardarDestinatariosCategoria(category.id, emails),
        `Destinatarios de la categoría ${category.nombre} guardados.`
      );
    } catch (e) {
      setApiError(e as Error);
    }
  }

  async function restablecer() {
    setApiError(null);
    try {
      await run(
        () => api.guardarDestinatariosCategoria(category.id, []),
        `La categoría ${category.nombre} vuelve a heredar de la lista general.`
      );
      setEmails([]);
      setInput('');
      setInputError(null);
    } catch (e) {
      setApiError(e as Error);
    }
  }

  return (
    <article
      className="panel"
      style={{ marginBottom: 'var(--space-2)' }}
      aria-labelledby={`cat-heading-${category.id}`}
    >
      <div
        className="panel-heading"
        style={{ cursor: 'pointer', userSelect: 'none' }}
        onClick={() => setExpanded(v => !v)}
        role="button"
        tabIndex={0}
        aria-expanded={expanded}
        aria-controls={`panel-categoria-${category.id}`}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setExpanded(v => !v); } }}
      >
        <div>
          <h3 id={`cat-heading-${category.id}`} style={{ margin: 0, fontSize: 'var(--heading-h4)' }}>
            {category.nombre}
          </h3>
          <p style={{ margin: 0, fontSize: 'var(--caption)', color: 'var(--neutral-700)' }}>
            {productCount} {productCount === 1 ? 'artículo' : 'artículos'} clasificados
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)' }}>
          {dirty ? (
            <Badge tone="warning">Sin guardar</Badge>
          ) : localHasSpecific ? (
            <Badge tone="success">{emails.length} propios</Badge>
          ) : generalCount > 0 ? (
            <Badge tone="neutral">Hereda lista general ({generalCount})</Badge>
          ) : (
            <Badge tone="warning">Sin destinatarios</Badge>
          )}

          <span style={{ display: 'inline-grid', placeItems: 'center', color: 'var(--neutral-700)', marginLeft: 'var(--space-1)' }}>
            {expanded ? <ChevronUp size={24} aria-hidden="true" /> : <ChevronDown size={24} aria-hidden="true" />}
          </span>
        </div>
      </div>

      {expanded && (
        <div id={`panel-categoria-${category.id}`} className="panel-body form-stack">
          {!localHasSpecific && (
            <p className="form-info">
              Esta categoría utiliza los {generalCount} correos de la lista general de la sucursal. Podés cargar direcciones específicas para que las alertas de {category.nombre} vayan exclusivamente a este equipo.
            </p>
          )}

          <div className="email-input-group">
            <div style={{ flex: 1 }}>
              <label htmlFor={`cat-email-input-${category.id}`} className="field-label" style={{ display: 'block', marginBottom: 'var(--space-half)', fontWeight: 'var(--weight-semibold)', fontSize: 'var(--body-medium)' }}>
                Nuevo correo para {category.nombre}
              </label>
              <input
                id={`cat-email-input-${category.id}`}
                type="email"
                placeholder="ejemplo@coto.com.ar"
                value={input}
                disabled={busy}
                onChange={e => { setInput(e.target.value); setInputError(null); }}
                onKeyDown={handleKeyDown}
                aria-describedby={inputError ? `cat-email-error-${category.id}` : undefined}
                aria-invalid={inputError ? 'true' : 'false'}
                style={{ width: '100%', minHeight: '44px', padding: '10px 14px', border: '1px solid var(--neutral-200)', borderRadius: 'var(--radius-small)', background: 'var(--neutral-00)', fontSize: 'var(--body-medium)' }}
              />
              {inputError && (
                <p id={`cat-email-error-${category.id}`} className="field-hint" role="alert" style={{ color: 'var(--danger-800)', marginTop: '4px', fontWeight: 'var(--weight-semibold)' }}>
                  {inputError}
                </p>
              )}
            </div>
            <Button
              type="button"
              variant="secondary"
              disabled={busy || !input.trim()}
              onClick={agregarEmail}
              >
              Agregar dirección
            </Button>
          </div>

          <div className="email-chip-list" role="list" aria-label={`Correos de la categoría ${category.nombre}`}>
            {emails.map(email => (
              <span key={email} className="email-chip" role="listitem">
                <Mail size={16} aria-hidden="true" style={{ color: 'var(--neutral-700)' }} />
                <span>{email}</span>
                <button
                  type="button"
                  className="email-chip-remove"
                  aria-label={`Eliminar correo ${email} de ${category.nombre}`}
                  disabled={busy}
                  onClick={() => eliminarEmail(email)}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </span>
            ))}
          </div>

          <ErrorMessage error={apiError} />

          <div className="form-actions" style={{ display: 'flex', gap: 'var(--space-1)', marginTop: 'var(--space-1)' }}>
            <Button
              type="button"
              variant="primary"
              icon={Save}
              loading={busy}
              disabled={!dirty || busy}
              onClick={guardar}
            >
              Guardar categoría
            </Button>
            {hasSpecific && (
              <Button
                type="button"
                variant="ghost"
                icon={RotateCcw}
                disabled={busy}
                onClick={restablecer}
              >
                Restablecer a lista general
              </Button>
            )}
            {dirty && (
              <Button
                type="button"
                variant="secondary"
                disabled={busy}
                onClick={descartar}
              >
                Descartar cambios
              </Button>
            )}
          </div>
        </div>
      )}
    </article>
  );
}

export function NoticeRecipients() {
  const { data } = useStore();
  if (!data) return null;

  const generalCount = data.destinatarios?.general?.length || 0;

  return (
    <div className="settings-layout" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      <GeneralRecipientsCard />

      <section aria-labelledby="category-recipients-title">
        <div style={{ marginBottom: 'var(--space-2)' }}>
          <h2 id="category-recipients-title" style={{ fontSize: 'var(--heading-h4)', marginBottom: 'var(--space-half)' }}>
            Listas específicas por categoría
          </h2>
          <p style={{ margin: 0, fontSize: 'var(--caption)', color: 'var(--neutral-700)' }}>
            Definí destinatarios especializados por sector. Al configurar una categoría, sus artículos dejarán de usar la lista general.
          </p>
        </div>

        <div>
          {data.categorias.map(cat => (
            <CategoryRecipientsCard
              key={cat.id}
              category={cat}
              productCount={data.productos.filter(p => p.categoriaId === cat.id).length}
              generalCount={generalCount}
            />
          ))}
        </div>
      </section>
    </div>
  );
}
