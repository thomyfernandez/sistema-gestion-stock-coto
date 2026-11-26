import { useEffect, useState } from 'react';
import { AlertCircle, ArrowDownToLine, Bell, Boxes, CheckCircle2, ChevronRight, ClipboardList, LogOut, Menu, PanelLeftClose, PanelLeftOpen, RefreshCw, Settings, ShieldCheck, Warehouse, X, Tag, type LucideIcon } from 'lucide-react';
import { Button, Empty } from './components/ui';
import { useStore, type Permiso } from './store';
import { LoginScreen } from './components/LoginScreen';
import { RecepcionPage } from './pages/Operations';
import { PurchasesPage } from './pages/Purchases';
import { StockPage } from './pages/Stock';
import { SettingsPage } from './pages/Settings';
import { DiscountsPage } from './pages/Discounts';
import cotoLogo from './assets/coto-superapp.png';

const SIDEBAR_STORAGE_KEY = 'coto.sidebar.collapsed';
const CONSULTA_MOVIL = '(max-width: 800px)';

type VistaId = 'stock' | 'descuentos' | 'recepcion' | 'compras' | 'configuracion';
interface Vista { id: VistaId; name: string; icon: LucideIcon; permiso: Permiso }

/** Los roles de cada vista viven en PERMISOS (store.tsx), junto con el resto de la matriz de la UI. */
const allViews: readonly Vista[] = [
  { id: 'stock', name: 'Stock', icon: Warehouse, permiso: 'verStock' },
  { id: 'descuentos', name: 'Descuentos', icon: Tag, permiso: 'verDescuentos' },
  { id: 'recepcion', name: 'Recepción', icon: ArrowDownToLine, permiso: 'verRecepcion' },
  { id: 'compras', name: 'Compras', icon: ClipboardList, permiso: 'verCompras' },
  { id: 'configuracion', name: 'Configuración', icon: Settings, permiso: 'verConfiguracion' },
];

/** Pestaña que se abre cuando la ruta sólo indica la vista. */
const TAB_POR_DEFECTO: Record<VistaId, string> = { stock: 'inventario', descuentos: '', recepcion: '', compras: 'reposicion', configuracion: 'avisos' };

const subtitles: Record<string, string> = { inventario: 'Existencias', digital: 'Reservas y despachos', avisos: 'Alertas y avisos', categorias: 'Categorías', reposicion: 'Reposición', catalogo: 'Productos y combos', promociones: 'Promociones', actividad: 'Actividad' };

const esVista = (id: string): id is VistaId => allViews.some(v => v.id === id);

function readRoute(): { view: VistaId; tab: string } {
  let [view, tab] = window.location.hash.replace(/^#\/?/, '').split('/');
  if (view === 'sucursal') { view = 'stock'; tab = 'inventario'; }
  if (view === 'digital') { view = 'stock'; tab = 'digital'; }
  if (view === 'compras' && tab === 'integraciones') tab = 'reposicion';
  const vista = esVista(view) ? view : 'stock';
  return { view: vista, tab: tab || TAB_POR_DEFECTO[vista] };
}

const horaArgentina = (fecha: Date) => new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }).format(fecha);

function estadoConexion(online: boolean, loading: boolean) {
  if (online) return { texto: 'API conectada', tono: 'success' };
  if (loading) return { texto: 'Conectando…', tono: 'warning' };
  return { texto: 'Sin conexión', tono: 'danger' };
}

function CargandoInventario() {
  return (
    <div className="loading-page" aria-busy="true" aria-label="Cargando inventario">
      <div className="skeleton line title" />
      <div className="skeleton line" />
      <div className="metrics-grid">{Array.from({ length: 4 }, (_, i) => <div key={i} className="skeleton metric" />)}</div>
      <div className="skeleton skeleton-table" />
      <p>Conectando con el inventario de la sucursal…</p>
    </div>
  );
}

/** Contenido de la vista actual: acceso restringido, carga inicial, backend caído o la página pedida. */
function ContenidoVista({ route, vista, visibles }: { route: { view: VistaId; tab: string }; vista?: Vista; visibles: readonly Vista[] }) {
  const store = useStore();
  if (!vista || !visibles.includes(vista)) {
    const primera = visibles[0];
    return (
      <Empty
        title="Acceso restringido"
        text={`Tu rol actual (${store.usuario?.rol}) no tiene permisos para acceder a la sección ${vista?.name ?? 'Stock'}.`}
        action={primera ? <Button variant="primary" onClick={() => { window.location.hash = `#/${primera.id}`; }}>Ir a {primera.name}</Button> : undefined}
      />
    );
  }
  if (!store.data && store.loading) return <CargandoInventario />;
  if (!store.data) {
    return (
      <Empty
        title="El inventario no está disponible"
        text="Comprobá que esté iniciado el backend actualizado y reintentá la conexión."
        action={<Button variant="primary" icon={RefreshCw} onClick={() => void store.refresh().catch(() => {})}>Volver a conectar</Button>}
      />
    );
  }
  switch (route.view) {
    case 'stock': return <StockPage tab={route.tab} />;
    case 'descuentos': return <DiscountsPage />;
    case 'recepcion': return <RecepcionPage />;
    case 'compras': return <PurchasesPage tab={route.tab} />;
    case 'configuracion': return <SettingsPage tab={route.tab} />;
  }
}

export default function App() {
  const store = useStore();
  const [route, setRoute] = useState(readRoute);

  useEffect(() => {
    if (window.location.hash === '#/compras/integraciones') window.history.replaceState(null, '', '#/compras/reposicion');
  }, [route]);

  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem(SIDEBAR_STORAGE_KEY) !== 'false'; } catch { return true; } });
  const [help, setHelp] = useState(false);
  const [alerts, setAlerts] = useState(false);

  useEffect(() => {
    const update = () => { setRoute(readRoute()); setAlerts(false); setHelp(false); window.scrollTo({ top: 0 }); document.getElementById('contenido')?.focus({ preventScroll: true }); if (window.matchMedia(CONSULTA_MOVIL).matches) setCollapsed(true); };
    const escape = (e: KeyboardEvent) => { if (e.key === 'Escape') { setAlerts(false); setHelp(false); if (window.matchMedia(CONSULTA_MOVIL).matches) setCollapsed(true); } };
    window.addEventListener('hashchange', update); window.addEventListener('keydown', escape);
    return () => { window.removeEventListener('hashchange', update); window.removeEventListener('keydown', escape); };
  }, []);

  useEffect(() => { try { localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed)); } catch { /* La navegación sigue funcionando sin almacenamiento local. */ } }, [collapsed]);

  const currentViewObj = allViews.find(v => v.id === route.view);
  const title = currentViewObj?.name || 'Stock';
  // Vistas del rol conectado: alimentan el menú, la redirección y el aviso de acceso restringido.
  const visibleViews = store.usuario ? allViews.filter(v => store.tienePermiso(v.permiso)) : [];

  useEffect(() => {
    document.title = store.usuario ? `${title} · COTO Gestión de stock` : 'Iniciar sesión · COTO Gestión de stock';
  }, [title, store.usuario]);

  // Si el usuario cambia o inicia sesión, verificar si la ruta actual es accesible por su rol; si no, redirigir a su primera vista permitida
  useEffect(() => {
    if (!store.usuario) return;
    const hasAccess = visibleViews.some(v => v.id === route.view);
    if (!hasAccess && visibleViews[0]) window.location.hash = `#/${visibleViews[0].id}`;
    // visibleViews se deriva de store.usuario, que ya figura entre las dependencias.
  }, [store.usuario, route.view]);

  // Si no hay usuario autenticado, mostramos la pantalla de login con el Design System
  if (!store.usuario) {
    return <LoginScreen />;
  }

  const puedeVerSeguimiento = store.tienePermiso('verSeguimiento');
  const low = store.data?.productos.filter(p => store.data?.politicas[p.id] && p.disponible < store.data.politicas[p.id].umbralAlerta) || [];
  const online = !!store.data && !store.error;
  const conexion = estadoConexion(online, store.loading);

  return (
    <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : 'sidebar-expanded'}`}>
      <button className="skip-link" onClick={() => document.getElementById('contenido')?.focus()}>Ir al contenido</button>
      
      <header className="topbar shell-header">
        <Button variant="ghost" className="icon-button mobile-menu-toggle" icon={Menu} aria-label={collapsed ? 'Abrir navegación' : 'Cerrar navegación'} aria-expanded={!collapsed} aria-controls="app-sidebar" onClick={() => setCollapsed(v => !v)} />
        <a href="#/stock/inventario" className="header-brand" aria-label="COTO, ir a Stock">
          <img className="coto-logo" src={cotoLogo} alt="COTO" width="543" height="324" />
          <span><strong>Back Office</strong><span>Gestión de Stock</span></span>
        </a>
        
        <nav className="breadcrumb" aria-label="Ruta de navegación">
          <a href="#/stock/inventario">Gestión</a>
          <ChevronRight size={24} aria-hidden="true" />
          <a href={`#/${route.view}`}>{title}</a>
          {subtitles[route.tab] && <>
            <ChevronRight size={24} aria-hidden="true" />
            <strong aria-current="page">{subtitles[route.tab]}</strong>
          </>}
        </nav>

        <div className="topbar-actions">
          <Button variant="ghost" className="icon-button" icon={RefreshCw} aria-label="Actualizar datos" title="Actualizar datos" loading={store.loading} disabled={store.busy} onClick={() => void store.refresh().catch(() => {})} />
          <button className="icon-link notification-button" aria-label={`Ver notificaciones: ${low.length} artículos con stock bajo`} title="Notificaciones" aria-expanded={alerts} aria-controls="notifications-panel" onClick={() => { setAlerts(v => !v); setHelp(false); }}>
            <Bell size={24} aria-hidden="true" />
            {low.length > 0 && <span>{low.length}</span>}
          </button>
          
          <div className="operator-chip">
            <button className="operator-avatar" aria-label={`Usuario: ${store.usuario.nombreCompleto} (${store.usuario.rol})`} title={`${store.usuario.nombreCompleto} - ${store.usuario.rol}`} aria-expanded={help} onClick={() => { setHelp(v => !v); setAlerts(false); }}>
              {store.usuario.username.slice(0, 2).toUpperCase()}
            </button>
            <div className="operator-info-chip">
              <span className="operator-name">{store.usuario.nombreCompleto}</span>
              <span className="operator-role">{store.usuario.rol}</span>
            </div>
            <Button variant="ghost" className="icon-button" icon={LogOut} aria-label="Cerrar sesión" title="Cerrar sesión" onClick={store.logout} />
          </div>
        </div>
      </header>

      <aside className="sidebar" id="app-sidebar" aria-label="Menú principal">
        <div className="sidebar-toggle-row">
          <Button variant="ghost" className="icon-button" icon={collapsed ? PanelLeftOpen : PanelLeftClose} aria-label={collapsed ? 'Expandir menú' : 'Contraer menú'} title={collapsed ? 'Expandir menú' : 'Contraer menú'} aria-expanded={!collapsed} aria-controls="app-sidebar" onClick={() => setCollapsed(v => !v)} />
        </div>
        
        <div className="nav-section-label">OPERACIONES</div>
        <nav className="main-nav" aria-label="Secciones del sistema">
          {visibleViews.map(({ id, name, icon: Icon }) => (
            <a href={`#/${id}`} key={id} aria-label={name} title={collapsed ? name : undefined} className={route.view === id ? 'active' : ''} aria-current={route.view === id ? 'page' : undefined}>
              <Icon size={24} aria-hidden="true" />
              <span className="nav-label">{name}</span>
              {id === 'stock' && low.length > 0 && <span className="nav-counter" aria-hidden="true">{low.length}</span>}
            </a>
          ))}
        </nav>

        {puedeVerSeguimiento && (
          <nav className="sidebar-secondary" aria-label="Seguimiento">
            <div className="nav-section-label">SEGUIMIENTO</div>
            <a href="#/compras/actividad" aria-label="Actividad del stock" title={collapsed ? 'Actividad del stock' : undefined}>
              <Boxes size={24} aria-hidden="true" />
              <span className="nav-label">Actividad del stock</span>
            </a>
          </nav>
        )}

        <div className="sidebar-footer">
          <div className="connection-line" title={conexion.texto}>
            <span className={`dot ${conexion.tono}`} />
            <span className="nav-label">{conexion.texto}</span>
          </div>
        </div>
      </aside>

      {!collapsed && <button className="sidebar-backdrop" aria-label="Cerrar menú" onClick={() => setCollapsed(true)} />}

      {alerts && (
        <section className="header-popover notifications-popover" id="notifications-panel" aria-label="Avisos de stock">
          <div className="split-line">
            <h2>Avisos de stock</h2>
            <Button variant="ghost" icon={X} className="icon-button" aria-label="Cerrar notificaciones" onClick={() => setAlerts(false)} />
          </div>
          {low.length ? (
            <ul>
              {low.map(p => (
                <li key={p.id}>
                  <strong>{p.nombre}</strong>
                  <span className="caption">{p.disponible} disponibles · bajo su umbral</span>
                </li>
              ))}
            </ul>
          ) : (
            <p>No hay artículos por debajo de su umbral de alerta.</p>
          )}
          {puedeVerSeguimiento && (
            <>
              <a className="text-link" href="#/compras/reposicion">Revisar reposición</a>
              <a className="text-link" href="#/configuracion/avisos">Configurar alertas y avisos</a>
            </>
          )}
        </section>
      )}

      {help && (
        <section className="header-popover" aria-label="Información del operador">
          <div className="split-line">
            <div className="popover-title">
              <ShieldCheck size={24} aria-hidden="true" />
              <h2>Sesión activa</h2>
            </div>
            <Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar información" onClick={() => setHelp(false)} />
          </div>
          <div className="session-details">
            <div><strong>Operador:</strong> {store.usuario.nombreCompleto}</div>
            <div><strong>Usuario:</strong> {store.usuario.username}</div>
            <div><strong>Rol:</strong> {store.usuario.rol}</div>
          </div>
          <Button variant="secondary" className="block session-logout" onClick={store.logout}>
            Cerrar sesión
          </Button>
        </section>
      )}

      <div className="workspace">
        <main id="contenido" tabIndex={-1} className={`main-content view-${route.view}`}>
          {store.error && (
            <div className="message danger connection-error" role="alert">
              <AlertCircle size={24} aria-hidden="true" />
              <div>
                <strong>{store.error.message}</strong>
                {store.data && <p>Los datos visibles corresponden a la última actualización confirmada.</p>}
              </div>
              <Button loading={store.loading} disabled={store.busy} onClick={() => void store.refresh().catch(() => {})}>
                Reintentar conexión
              </Button>
            </div>
          )}

          <ContenidoVista route={route} vista={currentViewObj} visibles={visibleViews} />

          <footer className="app-footer">
            <span>COTO · Gestión de stock</span>
            <span>
              {store.updated ? `Última actualización: ${horaArgentina(store.updated)}` : 'Esperando conexión'}
              <span className="footer-separator">·</span>
              Sesión: {store.usuario.username} ({store.usuario.rol})
            </span>
          </footer>
        </main>
      </div>

      {store.toast && (
        <div className={`toast ${store.toast.tone}`} role={store.toast.tone === 'danger' ? 'alert' : 'status'}>
          <span>{store.toast.tone === 'success' ? <CheckCircle2 size={24} aria-hidden="true" /> : <AlertCircle size={24} aria-hidden="true" />}</span>
          <p>{store.toast.message}</p>
          <Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar aviso" onClick={store.dismissToast} />
        </div>
      )}
    </div>
  );
}
