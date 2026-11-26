import { useState, type FormEvent } from 'react';
import { useStore } from '../store';
import { Button, Field } from './ui';
import { LogIn, UserCheck, ShieldCheck, Truck, ShoppingCart, AlertCircle } from 'lucide-react';
import logo from '../assets/coto-superapp.png';
import type { RolUsuario } from '../types';

interface DemoUser {
  username: string;
  nombre: string;
  rol: RolUsuario;
  icono: typeof UserCheck;
  descripcion: string;
}

const DEMO_PASSWORD = 'coto123';

const USUARIOS_DEMO: DemoUser[] = [
  { username: 'supervisor.demo', nombre: 'Supervisor de Sucursal', rol: 'SUPERVISOR', icono: ShieldCheck, descripcion: 'Control total de inventario y canales' },
  { username: 'repositor.demo', nombre: 'Repositor de Salón', rol: 'REPOSITOR', icono: UserCheck, descripcion: 'Stock, armado de combos y góndolas' },
  { username: 'recepcion.demo', nombre: 'Operador de Recepción', rol: 'RECEPCION', icono: Truck, descripcion: 'Recepción e ingreso de pedidos' },
  { username: 'compras.demo', nombre: 'Analista de Compras', rol: 'COMPRAS', icono: ShoppingCart, descripcion: 'Reposición, políticas y promociones' },
];

export function LoginScreen() {
  const { login } = useStore();
  const [username, setUsername] = useState('supervisor.demo');
  const [password, setPassword] = useState(DEMO_PASSWORD);
  const [cargando, setCargando] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) return;
    setCargando(true);
    setErrorMsg(null);
    try {
      await login({ username: username.trim(), password });
    } catch (err: unknown) {
      const e = err as Error;
      setErrorMsg(e.message || 'Credenciales incorrectas');
    } finally {
      setCargando(false);
    }
  };

  const seleccionarDemo = (u: DemoUser) => {
    setUsername(u.username);
    setPassword(DEMO_PASSWORD);
    setErrorMsg(null);
  };

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-card">
          <img className="login-logo" src={logo} alt="COTO" />
          <h1 className="login-title">Gestión de Stock</h1>
          <p className="login-text">Ingresá tus credenciales para acceder al inventario de sucursal.</p>

          {errorMsg && (
            <div className="message danger login-error">
              <AlertCircle size={24} aria-hidden="true" />
              <div><strong>Error al iniciar sesión</strong><p>{errorMsg}</p></div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="form-stack login-form">
            <Field label="Usuario">
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="ej. supervisor.demo"
                required
                disabled={cargando}
                autoComplete="username"
              />
            </Field>

            <Field label="Contraseña">
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Contraseña de sucursal"
                required
                disabled={cargando}
                autoComplete="current-password"
              />
            </Field>

            <Button type="submit" variant="primary" className="block login-submit" disabled={cargando} loading={cargando} icon={LogIn}>
              Iniciar sesión
            </Button>
          </form>

          <div className="login-demo">
            <span className="eyebrow login-demo-label">Acceso rápido para demostración</span>
            <div className="login-demo-grid">
              {USUARIOS_DEMO.map(u => <UsuarioDemoBoton key={u.username} usuario={u} seleccionado={username === u.username} onSelect={() => seleccionarDemo(u)} />)}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function UsuarioDemoBoton({ usuario, seleccionado, onSelect }: { usuario: DemoUser; seleccionado: boolean; onSelect: () => void }) {
  const Icono = usuario.icono;
  return (
    <button type="button" className={`login-demo-user ${seleccionado ? 'selected' : ''}`} aria-pressed={seleccionado} onClick={onSelect}>
      <span className="login-demo-role">
        <Icono size={24} aria-hidden="true" />
        <span>{usuario.rol}</span>
      </span>
      <span className="login-demo-username">{usuario.username}</span>
    </button>
  );
}
