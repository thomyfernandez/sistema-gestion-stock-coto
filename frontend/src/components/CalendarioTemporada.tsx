import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { diaMes, diasDelMes, meses } from '../format';
import { Button } from './ui';

/** Día y mes sin año; 0 significa "sin elegir". */
export interface DiaDelAnio { dia: number; mes: number }
type Extremo = 'desde' | 'hasta';

const clave = (d: DiaDelAnio) => d.mes * 100 + d.dia;
const elegido = (d: DiaDelAnio) => d.dia > 0 && d.mes > 0;
/** Igual que Temporada.incluye en el backend: si el inicio es posterior al fin, el rango cruza el año. */
function enRango(dia: DiaDelAnio, desde: DiaDelAnio, hasta: DiaDelAnio) {
  if (!elegido(desde) || !elegido(hasta)) return false;
  const [k, d, h] = [clave(dia), clave(desde), clave(hasta)];
  return d <= h ? k >= d && k <= h : k >= d || k <= h;
}
const nombre = (d: DiaDelAnio) => `${d.dia} de ${meses[d.mes - 1].toLowerCase()}`;

/**
 * Calendario sin año para elegir el período de una temporada. El primer clic marca el inicio y el segundo el fin;
 * "Desde" y "Hasta" permiten corregir cualquiera de los dos. Las flechas del teclado recorren los días.
 * Febrero muestra el 29: en los años no bisiestos el backend lo trata como el 28 (fin) o el 1 de marzo (inicio).
 */
export function CalendarioTemporada({ desde, hasta, disabled, onChange }: {
  desde: DiaDelAnio; hasta: DiaDelAnio; disabled: boolean; onChange: (desde: DiaDelAnio, hasta: DiaDelAnio) => void;
}) {
  const [activo, setActivo] = useState<Extremo>(elegido(desde) && !elegido(hasta) ? 'hasta' : 'desde');
  const [mes, setMes] = useState(desde.mes || new Date().getMonth() + 1);
  const [foco, setFoco] = useState(desde.mes === mes && desde.dia ? desde.dia : 1);
  const enfocar = useRef(false);
  const grilla = useRef<HTMLDivElement>(null);
  const dias = diasDelMes(mes);

  useEffect(() => {
    if (!enfocar.current) return;
    enfocar.current = false;
    grilla.current?.querySelector<HTMLButtonElement>(`[data-dia="${foco}"]`)?.focus();
  }, [foco, mes]);

  function irAMes(nuevo: number, dia = 1) {
    const m = ((nuevo - 1 + 12) % 12) + 1;
    setMes(m);
    setFoco(Math.min(dia, diasDelMes(m)));
  }
  function elegir(dia: number) {
    const valor = { dia, mes };
    if (activo === 'desde') { onChange(valor, hasta); setActivo('hasta'); }
    else { onChange(desde, valor); setActivo('desde'); }
    setFoco(dia);
  }
  function teclado(e: KeyboardEvent<HTMLButtonElement>, dia: number) {
    const paso = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[e.key];
    let destino: number | null = paso === undefined ? null : dia + paso;
    if (e.key === 'Home') destino = 1;
    if (e.key === 'End') destino = dias;
    if (destino === null) return;
    e.preventDefault();
    enfocar.current = true;
    if (destino < 1) irAMes(mes - 1, diasDelMes(((mes - 2 + 12) % 12) + 1) + destino);
    else if (destino > dias) irAMes(mes + 1, destino - dias);
    else setFoco(destino);
  }

  const extremo = (cual: Extremo, valor: DiaDelAnio) => (
    <button type="button" className={`season-end ${activo === cual ? 'active' : ''}`} aria-pressed={activo === cual} disabled={disabled}
      onClick={() => { setActivo(cual); if (elegido(valor)) { setMes(valor.mes); setFoco(valor.dia); } }}>
      <span className="caption">{cual === 'desde' ? 'Desde' : 'Hasta'}</span>
      <strong>{elegido(valor) ? diaMes(valor.dia, valor.mes) : 'Elegí un día'}</strong>
    </button>
  );

  return <fieldset className="season-calendar">
    <legend>Período</legend>
    <div className="season-calendar-ends">{extremo('desde', desde)}{extremo('hasta', hasta)}</div>
    <p className="field-hint" aria-live="polite">{activo === 'desde' ? 'Tocá el día en que empieza la temporada.' : 'Tocá el día en que termina. Puede ser del año siguiente.'}</p>
    <div className="season-calendar-header">
      <Button variant="ghost" className="icon-button" icon={ChevronLeft} aria-label="Mes anterior" disabled={disabled} onClick={() => irAMes(mes - 1)} />
      <select aria-label="Mes del calendario" value={mes} disabled={disabled} onChange={e => irAMes(Number(e.target.value))}>
        {meses.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
      </select>
      <Button variant="ghost" className="icon-button" icon={ChevronRight} aria-label="Mes siguiente" disabled={disabled} onClick={() => irAMes(mes + 1)} />
    </div>
    <div ref={grilla} className="season-calendar-grid" role="group" aria-label={`Días de ${meses[mes - 1].toLowerCase()}`}>
      {Array.from({ length: dias }, (_, i) => i + 1).map(dia => {
        const valor = { dia, mes };
        const esDesde = clave(valor) === clave(desde), esHasta = clave(valor) === clave(hasta);
        const estado = esDesde && esHasta ? ', inicio y fin' : esDesde ? ', inicio' : esHasta ? ', fin' : enRango(valor, desde, hasta) ? ', dentro del período' : '';
        return <button key={dia} type="button" data-dia={dia} disabled={disabled} tabIndex={dia === foco ? 0 : -1}
          className={`season-day ${esDesde || esHasta ? 'extremo' : ''} ${enRango(valor, desde, hasta) ? 'en-rango' : ''}`}
          aria-label={`${nombre(valor)}${estado}`} aria-pressed={esDesde || esHasta}
          onClick={() => elegir(dia)} onKeyDown={e => teclado(e, dia)}>{dia}</button>;
      })}
    </div>
  </fieldset>;
}
