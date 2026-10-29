import { useEffect, useState } from 'react';
import { AlertCircle, ArrowDownToLine, Bell, Boxes, CheckCircle2, ChevronRight, CircleHelp, ClipboardList, Menu, PanelLeftClose, PanelLeftOpen, RefreshCw, Settings, Warehouse, X } from 'lucide-react';
import { Button, Empty } from './components/ui';
import { useStore } from './store';
import { RecepcionPage } from './pages/Operations';
import { PurchasesPage } from './pages/Purchases';
import { StockPage } from './pages/Stock';
import { SettingsPage } from './pages/Settings';
import cotoLogo from './assets/coto-superapp.png';

const views = [
  { id: 'stock', name: 'Stock', icon: Warehouse },
  { id: 'recepcion', name: 'Recepción', icon: ArrowDownToLine },
  { id: 'compras', name: 'Compras', icon: ClipboardList },
  { id: 'configuracion', name: 'Configuración', icon: Settings },
];
const subtitles: Record<string, string> = { inventario: 'Existencias', digital: 'Reservas y despachos', avisos: 'Alertas y avisos', categorias: 'Categorías', reposicion: 'Reposición', catalogo: 'Productos y combos', promociones: 'Promociones', actividad: 'Actividad' };
function readRoute() {
  let [view, tab] = window.location.hash.replace(/^#\/?/, '').split('/');
  if (view === 'sucursal') { view = 'stock'; tab = 'inventario'; }
  if (view === 'digital') { view = 'stock'; tab = 'digital'; }
  if (view === 'compras' && tab === 'integraciones') tab = 'reposicion';
  if (!views.some(v => v.id === view)) view = 'stock';
  return { view, tab: tab || (view === 'stock' ? 'inventario' : view === 'configuracion' ? 'avisos' : view === 'compras' ? 'reposicion' : '') };
}
export default function App() {
  const store = useStore();
  const [route, setRoute] = useState(readRoute);
  useEffect(() => {
    if (window.location.hash === '#/compras/integraciones') window.history.replaceState(null, '', '#/compras/reposicion');
  }, [route]);
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('coto.sidebar.collapsed') !== 'false'; } catch { return true; } });
  const [help, setHelp] = useState(false);
  const [alerts, setAlerts] = useState(false);
  useEffect(() => {
    const update = () => { setRoute(readRoute()); setAlerts(false); setHelp(false); window.scrollTo({ top: 0 }); document.getElementById('contenido')?.focus({ preventScroll: true }); if (window.matchMedia('(max-width: 800px)').matches) setCollapsed(true); };
    const escape = (e: KeyboardEvent) => { if (e.key === 'Escape') { setAlerts(false); setHelp(false); if (window.matchMedia('(max-width: 800px)').matches) setCollapsed(true); } };
    window.addEventListener('hashchange', update); window.addEventListener('keydown', escape);
    return () => { window.removeEventListener('hashchange', update); window.removeEventListener('keydown', escape); };
  }, []);
  useEffect(() => { try { localStorage.setItem('coto.sidebar.collapsed', String(collapsed)); } catch { /* La navegación sigue funcionando sin almacenamiento local. */ } }, [collapsed]);
  const title = views.find(v => v.id === route.view)?.name || 'Stock';
  useEffect(() => { document.title = `${title} · COTO Gestión de stock`; }, [title]);
  const low = store.data?.productos.filter(p => store.data?.politicas[p.id] && p.disponible < store.data.politicas[p.id].umbralAlerta) || [];
  const online = !!store.data && !store.error;
  return <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : 'sidebar-expanded'}`}><button className="skip-link" onClick={() => document.getElementById('contenido')?.focus()}>Ir al contenido</button>
    <header className="topbar shell-header"><Button variant="ghost" className="icon-button mobile-menu-toggle" icon={Menu} aria-label={collapsed ? 'Abrir navegación' : 'Cerrar navegación'} aria-expanded={!collapsed} aria-controls="app-sidebar" onClick={() => setCollapsed(v => !v)} /><a href="#/stock/inventario" className="header-brand" aria-label="COTO, ir a Stock"><img className="coto-logo" src={cotoLogo} alt="COTO" width="543" height="324" /><span><strong>Back Office</strong><span>Gestión de Stock</span></span></a>
      <nav className="breadcrumb" aria-label="Ruta de navegación"><a href="#/stock/inventario">Gestión</a><ChevronRight size={24} aria-hidden="true" /><a href={`#/${route.view}`}>{title}</a>{subtitles[route.tab] && <><ChevronRight size={24} aria-hidden="true" /><strong aria-current="page">{subtitles[route.tab]}</strong></>}</nav>
      <div className="topbar-actions"><Button variant="ghost" className="icon-button" icon={RefreshCw} aria-label="Actualizar datos" title="Actualizar datos" loading={store.loading} disabled={store.busy} onClick={() => void store.refresh().catch(() => {})} /><button className="icon-link notification-button" aria-label={`Ver notificaciones: ${low.length} ${low.length === 1 ? 'artículo' : 'artículos'} con stock bajo`} title="Notificaciones" aria-expanded={alerts} aria-controls="notifications-panel" onClick={() => { setAlerts(v => !v); setHelp(false); }}><Bell size={24} aria-hidden="true" />{low.length > 0 && <span>{low.length}</span>}</button><button className="operator-avatar" aria-label="Información del operador" title="Información del operador" aria-expanded={help} onClick={() => { setHelp(v => !v); setAlerts(false); }}>OP</button></div>
    </header>
    <aside className="sidebar" id="app-sidebar" aria-label="Menú principal"><div className="sidebar-toggle-row"><Button variant="ghost" className="icon-button" icon={collapsed ? PanelLeftOpen : PanelLeftClose} aria-label={collapsed ? 'Expandir menú' : 'Contraer menú'} title={collapsed ? 'Expandir menú' : 'Contraer menú'} aria-expanded={!collapsed} aria-controls="app-sidebar" onClick={() => setCollapsed(v => !v)} /></div><div className="nav-section-label">OPERACIONES</div>
      <nav className="main-nav" aria-label="Secciones del sistema">{views.map(({ id, name, icon: Icon }) => <a href={`#/${id}`} key={id} aria-label={name} title={collapsed ? name : undefined} className={route.view === id ? 'active' : ''} aria-current={route.view === id ? 'page' : undefined}><Icon size={24} aria-hidden="true" /><span className="nav-label">{name}</span>{id === 'stock' && low.length > 0 && <span className="nav-counter" aria-hidden="true">{low.length}</span>}</a>)}</nav>
      <nav className="sidebar-secondary" aria-label="Seguimiento"><div className="nav-section-label">SEGUIMIENTO</div><a href="#/compras/actividad" aria-label="Actividad del stock" title={collapsed ? 'Actividad del stock' : undefined}><Boxes size={24} aria-hidden="true" /><span className="nav-label">Actividad del stock</span></a></nav>
      <div className="sidebar-footer"><div className="connection-line" title={online ? 'API conectada' : store.loading ? 'Conectando…' : 'Sin conexión'}><span className={`dot ${online ? 'success' : store.loading ? 'warning' : 'danger'}`} /><span className="nav-label">{online ? 'API conectada' : store.loading ? 'Conectando…' : 'Sin conexión'}</span></div></div>
    </aside>
    {!collapsed && <button className="sidebar-backdrop" aria-label="Cerrar menú" onClick={() => setCollapsed(true)} />}
    {alerts && <section className="header-popover notifications-popover" id="notifications-panel" aria-label="Avisos de stock"><div className="split-line"><h2>Avisos de stock</h2><Button variant="ghost" icon={X} className="icon-button" aria-label="Cerrar notificaciones" onClick={() => setAlerts(false)} /></div>{low.length ? <ul>{low.map(p => <li key={p.id}><strong>{p.nombre}</strong><span className="caption">{p.disponible} disponibles · bajo su umbral</span></li>)}</ul> : <p>No hay artículos por debajo de su umbral de alerta.</p>}<a className="text-link" href="#/compras/reposicion">Revisar reposición</a><a className="text-link" href="#/configuracion/avisos">Configurar alertas y avisos</a></section>}
    {help && <section className="header-popover" aria-label="Información del operador"><div className="split-line"><CircleHelp size={24} aria-hidden="true" /><Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar información del operador" onClick={() => setHelp(false)} /></div><p>Los formularios permiten indicar el operador responsable de cada movimiento.</p></section>}
    <div className="workspace"><main id="contenido" tabIndex={-1} className={`main-content view-${route.view}`}>
      {store.error && <div className="message danger connection-error" role="alert"><AlertCircle size={24} aria-hidden="true" /><div><strong>{store.error.message}</strong>{store.data && <p>Los datos visibles corresponden a la última actualización confirmada.</p>}</div><Button loading={store.loading} disabled={store.busy} onClick={() => void store.refresh().catch(() => {})}>Reintentar conexión</Button></div>}
      {!store.data ? store.loading ? <div className="loading-page" aria-busy="true" aria-label="Cargando inventario"><div className="skeleton line title" /><div className="skeleton line" /><div className="metrics-grid">{Array.from({ length: 4 }, (_, i) => <div key={i} className="skeleton metric" />)}</div><div className="skeleton skeleton-table" /><p>Conectando con el inventario de la sucursal…</p></div> : <Empty title="El inventario no está disponible" text="Comprobá que esté iniciado el backend actualizado y reintentá la conexión." action={<Button variant="primary" icon={RefreshCw} onClick={() => void store.refresh().catch(() => {})}>Volver a conectar</Button>} /> : <>
        {route.view === 'stock' && <StockPage tab={route.tab} />}{route.view === 'recepcion' && <RecepcionPage />}{route.view === 'compras' && <PurchasesPage tab={route.tab} />}{route.view === 'configuracion' && <SettingsPage tab={route.tab} />}
      </>}
      <footer className="app-footer"><span>COTO · Gestión de stock</span><span>{store.updated ? `Última actualización: ${new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }).format(store.updated)}` : 'Esperando conexión'}<span className="footer-separator">·</span>Datos de una única sucursal</span></footer>
    </main></div>{store.toast && <div className={`toast ${store.toast.tone}`} role={store.toast.tone === 'danger' ? 'alert' : 'status'}><span>{store.toast.tone === 'success' ? <CheckCircle2 size={24} aria-hidden="true" /> : <AlertCircle size={24} aria-hidden="true" />}</span><p>{store.toast.message}</p><Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar aviso" onClick={store.dismissToast} /></div>}
  </div>;
}
