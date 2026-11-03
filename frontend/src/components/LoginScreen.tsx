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

const USUARIOS_DEMO: DemoUser[] = [
  { username: 'supervisor.demo', nombre: 'Supervisor de Sucursal', rol: 'SUPERVISOR', icono: ShieldCheck, descripcion: 'Control total de inventario y canales' },
  { username: 'repositor.demo', nombre: 'Repositor de Salón', rol: 'REPOSITOR', icono: UserCheck, descripcion: 'Stock, armado de combos y góndolas' },
  { username: 'recepcion.demo', nombre: 'Operador de Recepción', rol: 'RECEPCION', icono: Truck, descripcion: 'Recepción e ingreso de pedidos' },
  { username: 'compras.demo', nombre: 'Analista de Compras', rol: 'COMPRAS', icono: ShoppingCart, descripcion: 'Reposición, políticas y promociones' },
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
      minHeight: '100dvh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: 'var(--neutral-50)',
      padding: 'var(--space-3)',
      boxSizing: 'border-box',
    }}>
      <div style={{ width: '100%', maxWidth: '440px' }}>
        <div style={{
          backgroundColor: 'var(--neutral-00)',
          borderRadius: 'var(--radius-medium)',
          border: '1px solid var(--neutral-100)',
          padding: 'var(--space-4)',
          textAlign: 'center',
          boxShadow: 'var(--shadow-300)',
          boxSizing: 'border-box',
        }}>
          <img
            src={logo}
            alt="COTO"
            style={{
              width: '160px',
              height: 'auto',
              maxHeight: '64px',
              objectFit: 'contain',
              display: 'block',
              margin: '0 auto var(--space-2)',
            }}
          />
          <h1 style={{
            fontSize: 'var(--heading-h2)',
            fontWeight: 'var(--weight-bold)',
            margin: '0 0 var(--space-1) 0',
            color: 'var(--neutral-900)',
            lineHeight: 1.25,
          }}>
            Gestión de Stock
          </h1>
          <p style={{
            margin: '0 0 var(--space-3) 0',
            color: 'var(--neutral-700)',
            fontSize: 'var(--body-medium)',
            lineHeight: 1.4,
          }}>
            Ingresá tus credenciales para acceder al inventario de sucursal.
          </p>

          {errorMsg && (
            <div className="message danger" style={{ marginBottom: 'var(--space-3)', textAlign: 'left' }}>
              <AlertCircle size={20} aria-hidden="true" />
              <div><strong>Error al iniciar sesión</strong><p>{errorMsg}</p></div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="form-stack" style={{ textAlign: 'left' }}>
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

            <Button
              type="submit"
              variant="primary"
              className="block"
              disabled={cargando}
              loading={cargando}
              icon={LogIn}
              style={{ marginTop: 'var(--space-1)' }}
            >
              Iniciar sesión
            </Button>
          </form>

          <div style={{
            marginTop: 'var(--space-4)',
            borderTop: '1px solid var(--neutral-100)',
            paddingTop: 'var(--space-3)',
          }}>
            <span className="eyebrow" style={{ display: 'block', marginBottom: 'var(--space-2)', textAlign: 'center' }}>
              Acceso rápido para demostración
            </span>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: 'var(--space-1)',
            }}>
              {USUARIOS_DEMO.map(u => {
                const Icono = u.icono;
                const seleccionado = username === u.username;
                return (
                  <button
                    key={u.username}
                    type="button"
                    onClick={() => seleccionarDemo(u)}
                    style={{
                      background: seleccionado ? 'color-mix(in srgb, var(--primary-100) 45%, var(--neutral-00))' : 'var(--neutral-00)',
                      border: `1.5px solid ${seleccionado ? 'var(--primary-500)' : 'var(--neutral-200)'}`,
                      borderRadius: 'var(--radius-small)',
                      padding: 'var(--space-1) var(--space-2)',
                      cursor: 'pointer',
                      textAlign: 'left',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '2px',
                      transition: 'all 0.15s ease',
                      boxSizing: 'border-box',
                      boxShadow: seleccionado ? 'var(--shadow-100)' : 'none',
                    }}
                  >
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 'var(--space-1)',
                      fontWeight: 'var(--weight-bold)',
                      fontSize: 'var(--caption)',
                      color: seleccionado ? 'var(--primary-700)' : 'var(--neutral-900)',
                    }}>
                      <Icono size={14} aria-hidden="true" />
                      <span>{u.rol}</span>
                    </div>
                    <span style={{
                      fontSize: '11px',
                      color: 'var(--neutral-700)',
                      fontFamily: 'monospace',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}>
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
