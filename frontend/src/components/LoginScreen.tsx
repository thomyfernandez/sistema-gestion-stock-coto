import { useState, type FormEvent } from 'react';
import { useStore } from '../store';
import { Button, Field } from './ui';
import { LogIn, UserCheck, ShieldCheck, Truck, ShoppingCart } from 'lucide-react';
import logo from '../assets/coto-superapp.png';
import type { RolUsuario } from '../types';

interface DemoUser {
  username: string;
  nombre: string;
  rol: RolUsuario;
  icono: typeof UserCheck;
  descripcion: string;
}

const USUARIOS_DEMO: DemoUser[] = [
  { username: 'recepcion.demo', nombre: 'Operador de Recepción', rol: 'RECEPCION', icono: Truck, descripcion: 'Recepción y descarga de pedidos' },
  { username: 'repositor.demo', nombre: 'Repositor de Salón', rol: 'REPOSITOR', icono: UserCheck, descripcion: 'Góndolas, devoluciones y mermas' },
  { username: 'compras.demo', nombre: 'Analista de Compras', rol: 'COMPRAS', icono: ShoppingCart, descripcion: 'Altas, políticas y promociones' },
  { username: 'supervisor.demo', nombre: 'Supervisor de Sucursal', rol: 'SUPERVISOR', icono: ShieldCheck, descripcion: 'Control total de inventario y canal digital' },
];

export function LoginScreen() {
  const { login } = useStore();
  const [username, setUsername] = useState('supervisor.demo');
  const [password, setPassword] = useState('coto123');
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
    setPassword('coto123');
    setErrorMsg(null);
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: 'var(--color-bg)',
      padding: 'var(--space-24)',
    }}>
      <div style={{ width: '100%', maxWidth: '440px' }}>
        <div style={{
          backgroundColor: 'var(--color-surface)',
          borderRadius: 'var(--radius-md)',
          border: '1px solid var(--color-border)',
          padding: 'var(--space-32)',
          textAlign: 'center',
          boxShadow: 'var(--shadow-300)',
        }}>
          <img src={logo} alt="COTO" style={{ height: '56px', objectFit: 'contain', marginBottom: 'var(--space-16)' }} />
          <h1 style={{ fontSize: 'var(--text-heading-24)', fontWeight: 'bold', margin: '0 0 var(--space-8) 0', color: 'var(--color-text)' }}>
            Gestión de Stock
          </h1>
          <p style={{ margin: '0 0 var(--space-24) 0', color: 'var(--color-text-secondary)', fontSize: 'var(--text-body-14)' }}>
            Ingresá tus credenciales para acceder al inventario de sucursal.
          </p>

          {errorMsg && (
            <div style={{
              backgroundColor: 'var(--danger-100)',
              color: 'var(--danger-700)',
              padding: 'var(--space-12)',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 'var(--space-16)',
              fontSize: 'var(--text-body-14)',
              textAlign: 'left',
              border: '1px solid var(--danger-300)',
            }}>
              {errorMsg}
            </div>
          )}

          <form onSubmit={handleSubmit} style={{ textAlign: 'left', display: 'flex', flexDirection: 'column', gap: 'var(--space-16)' }}>
            <Field label="Usuario">
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="ej. supervisor.demo"
                required
                disabled={cargando}
                style={{ width: '100%', boxSizing: 'border-box' }}
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
                style={{ width: '100%', boxSizing: 'border-box' }}
              />
            </Field>

            <Button
              type="submit"
              variant="primary"
              disabled={cargando}
              style={{ width: '100%', justifyContent: 'center', marginTop: 'var(--space-8)' }}
            >
              <LogIn size={18} />
              {cargando ? 'Iniciando sesión…' : 'Iniciar sesión'}
            </Button>
          </form>

          <div style={{ marginTop: 'var(--space-32)', borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-20)' }}>
            <span style={{ fontSize: 'var(--text-caption-12)', textTransform: 'uppercase', letterSpacing: '0.5px', color: 'var(--color-text-tertiary)', fontWeight: 'bold' }}>
              Acceso rápido para demostración
            </span>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-8)', marginTop: 'var(--space-12)' }}>
              {USUARIOS_DEMO.map(u => {
                const Icono = u.icono;
                const seleccionado = username === u.username;
                return (
                  <button
                    key={u.username}
                    type="button"
                    onClick={() => seleccionarDemo(u)}
                    style={{
                      background: seleccionado ? 'var(--primary-100)' : 'var(--color-surface)',
                      border: `1px solid ${seleccionado ? 'var(--primary-500)' : 'var(--color-border)'}`,
                      borderRadius: 'var(--radius-sm)',
                      padding: 'var(--space-8)',
                      cursor: 'pointer',
                      textAlign: 'left',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '4px',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-6)', fontWeight: 'bold', fontSize: 'var(--text-body-12)', color: seleccionado ? 'var(--primary-700)' : 'var(--color-text)' }}>
                      <Icono size={14} />
                      <span>{u.rol}</span>
                    </div>
                    <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {u.username}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
