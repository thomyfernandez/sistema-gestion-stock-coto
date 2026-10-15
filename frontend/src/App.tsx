import { useEffect, useState } from 'react';
import { AlertCircle, ArrowDownToLine, ArrowRight, Bell, Boxes, CheckCircle2, ChevronRight, CircleHelp, ClipboardList, RefreshCw, ShoppingBag, Warehouse, X } from 'lucide-react';
import { Button, Empty } from './components/ui';
import { useStore } from './store';
import { SucursalPage, RecepcionPage } from './pages/Operations';
import { PurchasesPage } from './pages/Purchases';
import { DigitalPage } from './pages/Digital';
import cotoLogo from './assets/coto-superapp.png';

const views = [
  { id: 'sucursal', name: 'Sucursal', icon: Warehouse },
  { id: 'recepcion', name: 'Recepción', icon: ArrowDownToLine },
  { id: 'compras', name: 'Compras', icon: ClipboardList },
  { id: 'digital', name: 'Coto Digital', icon: ShoppingBag },
];
function readRoute() {
  const [view, tab] = window.location.hash.replace(/^#\/?/, '').split('/');
  return { view: views.some(v => v.id === view) ? view : 'sucursal', tab: tab || 'reposicion' };
}
export default function App() {
  const store = useStore();
  const [route, setRoute] = useState(readRoute);
  const [help, setHelp] = useState(false);
  useEffect(() => {
    const update = () => { setRoute(readRoute()); window.scrollTo({ top: 0 }); document.getElementById('contenido')?.focus({ preventScroll: true }); };
    window.addEventListener('hashchange', update);
    return () => window.removeEventListener('hashchange', update);
  }, []);
  useEffect(() => { document.title = `${views.find(v => v.id === route.view)?.name} · COTO Gestión de stock`; }, [route.view]);
  const low = store.data?.productos.filter(p => store.data?.politicas[p.id] && p.disponible < store.data.politicas[p.id].umbralAlerta).length || 0;
  const online = !!store.data && !store.error;
  return <div className="app-shell"><button className="skip-link" onClick={() => document.getElementById('contenido')?.focus()}>Ir al contenido</button>
    <aside className="sidebar"><a href="#/sucursal" className="brand" aria-label="COTO, ir al inventario"><img className="coto-logo" src={cotoLogo} alt="COTO" width="543" height="324" /><span className="brand-description">Gestión de stock</span></a><div className="nav-section-label">OPERACIONES</div>
      <nav className="main-nav" aria-label="Vistas operativas">{views.map(({ id, name, icon: Icon }) => <a href={`#/${id}`} key={id} className={route.view === id ? 'active' : ''} aria-current={route.view === id ? 'page' : undefined}><Icon size={24} aria-hidden="true" /><span>{name}</span>{id === 'sucursal' && low > 0 && <span className="nav-counter">{low}</span>}</a>)}</nav>
      <div className="sidebar-secondary"><div className="nav-section-label">SEGUIMIENTO</div><a href="#/compras/actividad"><Boxes size={24} aria-hidden="true" /><span>Actividad del stock</span></a><a href="#/compras/integraciones"><RefreshCw size={24} aria-hidden="true" /><span>Integraciones</span>{!!store.data?.integraciones.entregas.some(e => e.estado === 'PENDIENTE') && <span className="pending-dot" />}</a></div>
      <div className="sidebar-footer"><div className="branch-mark"><Warehouse size={24} aria-hidden="true" /><div><strong>Operación de sucursal</strong><span>Inventario centralizado</span></div></div><div className="connection-line"><span className={`dot ${online ? 'success' : store.loading ? 'warning' : 'danger'}`} />{online ? 'API conectada' : store.loading ? 'Conectando…' : 'Sin conexión'}</div></div>
    </aside>
    <div className="workspace"><header className="topbar"><div className="breadcrumb"><span>Operaciones</span><ChevronRight size={24} aria-hidden="true" /><strong>{views.find(v => v.id === route.view)?.name}</strong></div><div className="topbar-actions"><span className="today">{new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date())}</span><span className="topbar-divider" /><Button variant="ghost" className="icon-button" icon={RefreshCw} aria-label="Actualizar datos" title="Actualizar datos" loading={store.loading} disabled={store.busy} onClick={() => void store.refresh().catch(() => {})} /><a className="icon-link notification-button" aria-label={`Ver alertas${low ? `: ${low} ${low === 1 ? 'artículo' : 'artículos'} con stock bajo` : ''}`} href="#/compras/reposicion"><Bell size={24} aria-hidden="true" />{low > 0 && <span>{low}</span>}</a><button className="operator-avatar" aria-label="Información del operador" title="Información del operador" onClick={() => setHelp(v => !v)}>OP</button></div></header>
      {help && <div className="operator-note" role="status"><CircleHelp size={24} aria-hidden="true" /><p>Los formularios permiten indicar el operador responsable de cada movimiento. Esta aplicación es un prototipo académico.</p><Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar información del operador" onClick={() => setHelp(false)} /></div>}
      <main id="contenido" tabIndex={-1} className={`main-content view-${route.view}`}>
        {store.error && <div className="message danger connection-error" role="alert"><AlertCircle size={24} aria-hidden="true" /><div><strong>{store.error.message}</strong>{store.data && <p>Los datos visibles corresponden a la última actualización confirmada.</p>}</div><Button loading={store.loading} disabled={store.busy} onClick={() => void store.refresh().catch(() => {})}>Reintentar conexión</Button></div>}
        {!store.data ? store.loading ? <div className="loading-page" aria-busy="true" aria-label="Cargando inventario"><div className="skeleton line title" /><div className="skeleton line" /><div className="metrics-grid">{Array.from({ length: 4 }, (_, i) => <div key={i} className="skeleton metric" />)}</div><div className="skeleton skeleton-table" /><p>Conectando con el inventario de la sucursal…</p></div> : <Empty title="El inventario no está disponible" text="Iniciá el backend en el puerto configurado y reintentá la conexión." action={<Button variant="primary" icon={RefreshCw} onClick={() => void store.refresh().catch(() => {})}>Volver a conectar</Button>} /> : <>
          {route.view === 'sucursal' && <SucursalPage />}{route.view === 'recepcion' && <RecepcionPage />}{route.view === 'compras' && <PurchasesPage tab={route.tab} />}{route.view === 'digital' && <DigitalPage />}
        </>}
        <footer className="app-footer"><span>COTO · Gestión de stock</span><span>{store.updated ? `Última actualización: ${new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }).format(store.updated)}` : 'Esperando conexión'}<span className="footer-separator">·</span>Datos de una única sucursal</span></footer>
      </main>
    </div>{store.toast && <div className={`toast ${store.toast.tone}`} role={store.toast.tone === 'danger' ? 'alert' : 'status'}><span>{store.toast.tone === 'success' ? <CheckCircle2 size={24} aria-hidden="true" /> : <AlertCircle size={24} aria-hidden="true" />}</span><p>{store.toast.message}</p><Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar aviso" onClick={store.dismissToast} /></div>}
    <a className="mobile-activity-link" href="#/compras/actividad">Actividad <ArrowRight size={24} aria-hidden="true" /></a>
  </div>;
}
