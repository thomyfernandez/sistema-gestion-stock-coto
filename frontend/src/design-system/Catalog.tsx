import { useEffect, useState, type ReactNode } from 'react';
import { ArrowRight, Bell, CircleHelp, Delete, Filter, Package, Search, ShoppingCart, Trash2, X } from 'lucide-react';
import logo from '../assets/coto-superapp.png';
import { Button, IconButton } from '../components/ui';

// Catálogo vivo del Design System COTO: se lee de los mismos tokens que usa la aplicación.
const PALETAS: { nombre: string; texto: string; tonos: [string, string][] }[] = [
  { nombre: 'Primary', texto: 'Acciones e interactivos. 10 % de la composición.', tonos: [['700', 'Dark'], ['500', 'Base'], ['100', 'Light']].map(([t, n]) => [`--primary-${t}`, `${t} / ${n}`]) },
  { nombre: 'Neutrals / Grays', texto: 'Texto, campos, fondos y divisores. 30 %.', tonos: ['900', '800', '700', '600', '500', '400', '300', '200', '100', '50', '00'].map(t => [`--neutral-${t}`, t === '500' ? '500 / Base' : t === '50' ? '50 / BG' : t === '00' ? '00 / White' : t]) },
  { nombre: 'Info', texto: 'Enlaces e información.', tonos: ['800', '500', '100'].map(t => [`--info-${t}`, t === '100' ? '100 / BG' : t]) },
  { nombre: 'Success', texto: 'Acción positiva o confirmación exitosa.', tonos: ['900', '500', '100'].map(t => [`--success-${t}`, t === '100' ? '100 / BG' : t]) },
  { nombre: 'Warning', texto: 'Acción potencialmente destructiva o en espera.', tonos: ['800', '500', '100'].map(t => [`--warning-${t}`, t === '500' ? '500 / Base' : t]) },
  { nombre: 'Danger', texto: 'Errores y acciones destructivas.', tonos: ['800', '500', '100'].map(t => [`--danger-${t}`, t === '500' ? '500 / Base' : t]) },
];
const TIPOGRAFIA: [string, string, string][] = [
  ['display-lg', 'Display Large', '56 px / 140 % Bold'], ['display-md', 'Display Medium', '40 px / 140 % Bold'], ['display-sm', 'Display Small', '32 px / 140 % Bold'],
  ['heading-h1', 'Heading 1', '28 px / 140 % Semibold'], ['heading-h2', 'Heading 2', '24 px / 140 % Semibold'], ['heading-h3', 'Heading 3', '22 px / 140 % Semibold'], ['heading-h4', 'Heading 4', '20 px / 140 % Semibold'],
  ['body-lg weight-bold', 'Body bold', '18 px Bold'], ['body-lg weight-semibold', 'Body semibold', '18 px Semibold'], ['body-lg', 'Body regular', '18 px Regular'],
  ['body-md weight-bold', 'Body bold', '16 px Bold'], ['body-md weight-semibold', 'Body semibold', '16 px Semibold'], ['body-md', 'Body regular', '16 px Regular'], ['body-md weight-book', 'Body book', '16 px Book'],
  ['caption-text weight-bold', 'Caption bold', '12 px Bold'], ['caption-text weight-semibold', 'Caption semibold', '12 px Semibold'], ['caption-text', 'Caption regular', '12 px Regular'],
];
const ESPACIOS = ['1', '2', '3', '4', '5', '6', '7', '8', '10'];

function luminancia(hex: string) {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4);
  return .2126 * r + .7152 * g + .0722 * b;
}
function contraste(a: string, b: string) {
  const [x, y] = [luminancia(a), luminancia(b)].sort((m, n) => n - m);
  return (x + .05) / (y + .05);
}
function nivel(ratio: number) { return ratio >= 7 ? 'AAA' : ratio >= 4.5 ? 'AA' : ratio >= 3 ? 'AA grande' : '—'; }

function useTokens() {
  const [valores, setValores] = useState<Record<string, string>>({});
  useEffect(() => {
    const estilo = getComputedStyle(document.documentElement);
    setValores(Object.fromEntries(PALETAS.flatMap(p => p.tonos.map(([v]) => [v, estilo.getPropertyValue(v).trim().toUpperCase()]))));
  }, []);
  return valores;
}

function Seccion({ titulo, texto, children }: { titulo: string; texto: string; children: ReactNode }) {
  return <section className="ds-section"><h2 className="heading-h2">{titulo}</h2><p className="ds-lead">{texto}</p>{children}</section>;
}
function Fila({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return <div className="ds-row"><span className="ds-row-label">{etiqueta}</span><div className="ds-row-items">{children}</div></div>;
}

export default function Catalog() {
  const tokens = useTokens();
  const [marcado, setMarcado] = useState(true);
  const [radio, setRadio] = useState('a');
  return <div className="ds-page">
    <header className="ds-header"><div><span className="eyebrow">FOUNDATIONS</span><h1 className="heading-h1">COTO Design System</h1></div><img src={logo} alt="COTO" className="ds-logo" /></header>
    <main className="ds-main">
      <Seccion titulo="Color System" texto="Paleta inspirada en los colores originales de la marca, más vibrante. El contraste se calcula contra blanco o contra el texto principal, el que resulte mayor.">
        <div className="ds-harmony" aria-label="Armonía 60/30/10"><span>60 % White</span><span>30 % Neutrals</span><span>10 % Primary</span></div>
        {PALETAS.map(p => <div key={p.nombre} className="ds-palette"><h3>{p.nombre}</h3><p className="muted">{p.texto}</p><div className="ds-swatches">{p.tonos.map(([variable, nombre]) => {
          const hex = tokens[variable] ?? '';
          const blanco = hex ? contraste(hex, '#FFFFFF') : 0, oscuro = hex ? contraste(hex, tokens['--neutral-900'] || '#1B171C') : 0;
          const ratio = Math.max(blanco, oscuro);
          return <figure key={variable} className="ds-swatch"><div style={{ background: `var(${variable})`, color: blanco >= oscuro ? 'var(--neutral-00)' : 'var(--neutral-900)' }}>{hex && `${nivel(ratio)} ${ratio.toFixed(2)}:1`}</div>
            <figcaption><strong>{nombre}</strong><code>{hex}</code><code>{variable}</code></figcaption></figure>;
        })}</div></div>)}
      </Seccion>

      <Seccion titulo="Typography" texto="Gotham Rounded en títulos y cuerpo. Si no está instalada (licencia comercial), se usa Nunito Variable incluida en el build.">
        <p className="ds-specimen">Ag</p>
        <div className="ds-type">{TIPOGRAFIA.map(([clase, nombre, spec]) => <div key={clase}><span className={clase}>{nombre}</span><span className="caption">{spec}</span></div>)}</div>
      </Seccion>

      <Seccion titulo="Grillas y espaciado" texto="Grilla de 8 puntos. Desktop de 1024 px: 9 columnas de 94 px, carriles de 16 px y márgenes de 24 px; zona principal de 645 px y secundaria de 315 px.">
        <div className="ds-grid-demo"><div className="ds-columns">{Array.from({ length: 9 }, (_, i) => <span key={i}>{i + 1}</span>)}</div><div className="ds-zones"><span>Zona principal 645 px</span><span>Zona secundaria 315 px</span></div></div>
        <div className="ds-spaces">{ESPACIOS.map(s => <div key={s}><span style={{ width: `var(--space-${s})` }} /><code>--space-{s}</code></div>)}</div>
      </Seccion>

      <Seccion titulo="Elevations" texto="Siete jerarquías: 100 para el estado default, 400 para hover/active y 300 para modales o popups.">
        <div className="ds-elevations">{[100, 200, 300, 400, 500, 600, 700].map(n => <div key={n} className={`elevation-${n}`}>Shadows {n}</div>)}</div>
      </Seccion>

      <Seccion titulo="Icon System" texto="Íconos outline de trazo redondeado (morfología Feather / Untitled UI) mediante lucide-react, en 48, 32 y 24 px.">
        <div className="ds-row-items ds-icon-sizes"><span><strong>Big</strong> 48 px <Trash2 size={48} aria-hidden="true" /></span><span><strong>Medium</strong> 32 px <Trash2 size={32} aria-hidden="true" /></span><span><strong>Small</strong> 24 px <Trash2 size={24} aria-hidden="true" /></span></div>
        <div className="ds-row-items">{[Filter, Package, Search, ShoppingCart, Bell, CircleHelp, ArrowRight, X].map((Icon, i) => <Icon key={i} size={24} aria-hidden="true" />)}</div>
      </Seccion>

      <Seccion titulo="Buttons" texto="Comunican acciones. Una sola acción primaria por zona; las semánticas solo cuando el significado lo pide.">
        <Fila etiqueta="Primary"><Button variant="primary">Button</Button><Button variant="lineal">Button</Button><Button variant="primary" icon={X}>Button</Button><Button variant="primary" icon={X} iconPosition="right">Button</Button></Fila>
        <Fila etiqueta="Tamaños"><Button variant="primary" size="lg">Button</Button><Button variant="primary">Button</Button><Button variant="primary" size="sm">Button</Button></Fila>
        <Fila etiqueta="Secondary"><Button variant="dark">Button</Button><Button variant="secondary">Button</Button><Button variant="ghost">Button</Button></Fila>
        <Fila etiqueta="Semantics"><Button variant="warning" icon={X}>Button</Button><Button variant="success" icon={X}>Button</Button><Button variant="danger" icon={X}>Button</Button><Button variant="primary" icon={X} disabled>Button</Button><Button variant="info" icon={X}>Button</Button></Fila>
        <Fila etiqueta="Loading / disabled"><Button variant="primary" loading>Guardando</Button><Button variant="lineal" disabled>Button</Button></Fila>
        <Fila etiqueta="Header button"><Button className="header-button" icon={CircleHelp}>Ayuda</Button></Fila>
      </Seccion>

      <Seccion titulo="Links" texto="Color Info 500 subrayado. Tres tamaños.">
        <Fila etiqueta="Primary"><a className="link lg" href="#links">link</a><a className="link" href="#links">link</a><a className="link sm" href="#links">link</a></Fila>
      </Seccion>

      <Seccion titulo="Icon buttons" texto="Naked, lineal y fill, en 24, 32 y 40 px. Siempre con etiqueta accesible.">
        {(['naked', 'lineal', 'fill'] as const).map(a => <Fila key={a} etiqueta={a[0].toUpperCase() + a.slice(1)}>
          <IconButton icon={X} label="Cerrar" appearance={a} /><IconButton icon={X} label="Cerrar" appearance={a} disabled />
          <span className="ds-gap" /><IconButton icon={X} label="Cerrar" appearance={a} size="sm" /><IconButton icon={X} label="Cerrar" appearance={a} /><IconButton icon={X} label="Cerrar" appearance={a} size="lg" />
        </Fila>)}
      </Seccion>

      <Seccion titulo="Checkbox & Radio buttons" texto="Trazo y relleno Primary 500; hover con Primary 700 y halo Primary 100.">
        <Fila etiqueta="Checkbox"><label className="choice"><input type="checkbox" checked={!marcado} onChange={() => setMarcado(!marcado)} />Sin marcar</label><label className="choice"><input type="checkbox" checked={marcado} onChange={() => setMarcado(!marcado)} />Marcado</label><label className="choice"><input type="checkbox" disabled />Deshabilitado</label></Fila>
        <Fila etiqueta="Radio"><label className="choice"><input type="radio" name="ds" checked={radio === 'a'} onChange={() => setRadio('a')} />Opción A</label><label className="choice"><input type="radio" name="ds" checked={radio === 'b'} onChange={() => setRadio('b')} />Opción B</label><label className="choice"><input type="radio" disabled />Deshabilitado</label></Fila>
      </Seccion>

      <Seccion titulo="Keypad" texto="Teclas de 40 px para ingreso numérico táctil (cantidades en recepción y góndola).">
        <div className="keypad">{['1', '2', '3', '4', '5', '6', '7', '8', '9', ',', '0'].map(k => <button key={k} type="button" className="keypad-key">{k}</button>)}<button type="button" className="keypad-key fill" aria-label="Borrar"><Delete size={20} aria-hidden="true" /></button></div>
      </Seccion>
    </main>
  </div>;
}
