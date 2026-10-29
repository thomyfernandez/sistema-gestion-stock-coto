#!/usr/bin/env node
// Memoria compartida del equipo (3 integrantes + sus agentes de IA).
// memoria.db es la base SQLite que consultan los agentes; memoria.sql es su espejo en texto,
// una fila por línea, que git combina con merge=union. Ambos archivos se versionan.
//
// Uso: node --experimental-sqlite --no-warnings .claude/memoria/memoria.mjs <comando> [opciones]

import { DatabaseSync } from 'node:sqlite';
import { execSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';

const DIR = dirname(fileURLToPath(import.meta.url));
const DB = join(DIR, 'memoria.db');
const SQL = join(DIR, 'memoria.sql');
const TIPOS = ['decision', 'convencion', 'aprendizaje', 'tarea', 'evento', 'pendiente'];
const ESTADOS = ['abierta', 'en-curso', 'hecha', 'descartada'];
const COLUMNAS = ['id', 'fecha', 'autor', 'agente', 'tipo', 'pedido', 'titulo', 'detalle', 'archivos', 'estado', 'reemplaza'];

const AYUDA = `Memoria compartida del equipo

Comandos:
  contexto [--pedido slug] [--limite N]   Resumen para empezar a trabajar (leer SIEMPRE al iniciar)
  registrar <tipo> --titulo "..." [--detalle "..."] [--agente nombre] [--pedido slug]
            [--archivos a,b] [--estado abierta] [--reemplaza id]
  estado <id> <${ESTADOS.join('|')}>      Cambia el estado de una tarea o pendiente
  buscar <texto> [--limite N]             Busca en títulos, detalles, archivos y pedidos
  listar [--tipo t] [--pedido slug] [--autor a] [--limite N]
  ver <id>                                Muestra una entrada completa
  sincronizar                             Reconstruye memoria.db desde memoria.sql (tras un merge)

Tipos: ${TIPOS.join(', ')}`;

function abrir() {
  const db = new DatabaseSync(DB);
  db.exec(`CREATE TABLE IF NOT EXISTS memoria (
    id TEXT PRIMARY KEY,
    fecha TEXT NOT NULL,
    autor TEXT NOT NULL,
    agente TEXT,
    tipo TEXT NOT NULL CHECK (tipo IN (${TIPOS.map(t => `'${t}'`).join(', ')})),
    pedido TEXT,
    titulo TEXT NOT NULL,
    detalle TEXT,
    archivos TEXT,
    estado TEXT CHECK (estado IS NULL OR estado IN (${ESTADOS.map(e => `'${e}'`).join(', ')})),
    reemplaza TEXT
  );
  CREATE INDEX IF NOT EXISTS memoria_tipo ON memoria (tipo, fecha);
  CREATE INDEX IF NOT EXISTS memoria_pedido ON memoria (pedido, fecha);`);
  importar(db);
  return db;
}

// memoria.sql manda: cualquier fila que llegó por git y falta en la base se incorpora.
// El estado de una fila puede cambiar, así que se reemplaza la versión local.
function importar(db) {
  if (!existsSync(SQL)) return;
  db.exec('BEGIN');
  try { db.exec(readFileSync(SQL, 'utf8')); db.exec('COMMIT'); }
  catch (e) { db.exec('ROLLBACK'); throw new Error(`memoria.sql inválido: ${e.message}. Resolvé el conflicto de git en ese archivo.`); }
}

function literal(v) {
  if (v === null || v === undefined) return 'NULL';
  return String(v).split('\n').map(p => `'${p.replaceAll("'", "''")}'`).join(' || char(10) || ');
}

function exportar(db) {
  const filas = db.prepare(`SELECT ${COLUMNAS.join(', ')} FROM memoria ORDER BY id`).all();
  const lineas = filas.map(f => `INSERT OR REPLACE INTO memoria (${COLUMNAS.join(', ')}) VALUES (${COLUMNAS.map(c => literal(f[c])).join(', ')});`);
  writeFileSync(SQL, lineas.join('\n') + (lineas.length ? '\n' : ''));
}

function autorGit() {
  try { return execSync('git config user.name', { cwd: DIR, encoding: 'utf8' }).trim() || 'desconocido'; }
  catch { return process.env.USER || 'desconocido'; }
}

function nuevoId() {
  return `${Date.now().toString(36)}-${randomBytes(3).toString('hex')}`;
}

function opciones(args) {
  const posicionales = [], op = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i].startsWith('--')) op[args[i].slice(2)] = args[i + 1]?.startsWith('--') || args[i + 1] === undefined ? 'true' : args[++i];
    else posicionales.push(args[i]);
  }
  return { posicionales, op };
}

function linea(f) {
  const marcas = [f.tipo, f.estado, f.pedido && `#${f.pedido}`].filter(Boolean).join(' · ');
  return `- [${f.id}] ${f.fecha.slice(0, 10)} ${f.autor}${f.agente ? `/${f.agente}` : ''} (${marcas}) ${f.titulo}`;
}

function detalle(f) {
  return [linea(f), f.detalle && `    ${f.detalle.replaceAll('\n', '\n    ')}`, f.archivos && `    archivos: ${f.archivos}`, f.reemplaza && `    reemplaza: ${f.reemplaza}`].filter(Boolean).join('\n');
}

// Una entrada reemplazada por otra deja de mostrarse en el contexto.
const VIGENTES = `NOT EXISTS (SELECT 1 FROM memoria r WHERE r.reemplaza = m.id)`;

function contexto(db, { pedido, limite = '15' }) {
  const n = Number(limite);
  const secciones = [
    ['Convenciones del equipo', `tipo = 'convencion'`, 50],
    ['Decisiones vigentes', `tipo = 'decision'`, n],
    ['Tareas y pendientes abiertos', `tipo IN ('tarea', 'pendiente') AND estado IN ('abierta', 'en-curso')`, 50],
    ['Aprendizajes', `tipo = 'aprendizaje'`, n],
    ['Actividad reciente', `tipo = 'evento'`, n],
  ];
  const filtro = pedido ? ` AND (m.pedido = ? OR m.tipo = 'convencion')` : '';
  const salida = [`# Memoria del equipo${pedido ? ` — #${pedido}` : ''}`];
  for (const [titulo, where, max] of secciones) {
    const filas = db.prepare(`SELECT * FROM memoria m WHERE ${where} AND ${VIGENTES}${filtro} ORDER BY id DESC LIMIT ${max}`).all(...(pedido ? [pedido] : []));
    if (filas.length) salida.push(`\n## ${titulo}`, ...filas.map(f => f.tipo === 'evento' ? linea(f) : detalle(f)));
  }
  if (salida.length === 1) salida.push('\n(Sin entradas todavía.)');
  console.log(salida.join('\n'));
}

function main() {
  const [comando, ...resto] = process.argv.slice(2);
  const { posicionales, op } = opciones(resto);
  if (!comando || comando === 'ayuda' || op.help) return console.log(AYUDA);
  // Tras un conflicto de git en el binario, se descarta y se reconstruye desde el espejo de texto.
  if (comando === 'sincronizar') rmSync(DB, { force: true });
  const db = abrir();

  switch (comando) {
    case 'contexto': return contexto(db, op);
    case 'registrar': {
      const tipo = posicionales[0];
      if (!TIPOS.includes(tipo)) throw new Error(`Tipo inválido. Usá: ${TIPOS.join(', ')}`);
      if (!op.titulo) throw new Error('Falta --titulo');
      const estado = op.estado ?? (['tarea', 'pendiente'].includes(tipo) ? 'abierta' : null);
      const fila = { id: nuevoId(), fecha: new Date().toISOString(), autor: autorGit(), agente: op.agente ?? null, tipo,
        pedido: op.pedido ?? null, titulo: op.titulo, detalle: op.detalle ?? null, archivos: op.archivos ?? null, estado, reemplaza: op.reemplaza ?? null };
      db.prepare(`INSERT INTO memoria (${COLUMNAS.join(', ')}) VALUES (${COLUMNAS.map(() => '?').join(', ')})`).run(...COLUMNAS.map(c => fila[c]));
      exportar(db);
      return console.log(`Registrado ${fila.id}`);
    }
    case 'estado': {
      const [id, estado] = posicionales;
      if (!ESTADOS.includes(estado)) throw new Error(`Estado inválido. Usá: ${ESTADOS.join(', ')}`);
      const { changes } = db.prepare('UPDATE memoria SET estado = ? WHERE id = ?').run(estado, id);
      if (!changes) throw new Error(`No existe la entrada ${id}`);
      exportar(db);
      return console.log(`${id} → ${estado}`);
    }
    case 'buscar': {
      const texto = `%${posicionales.join(' ')}%`;
      const filas = db.prepare(`SELECT * FROM memoria WHERE titulo LIKE ?1 OR detalle LIKE ?1 OR archivos LIKE ?1 OR pedido LIKE ?1 ORDER BY id DESC LIMIT ${Number(op.limite ?? 20)}`).all(texto);
      return console.log(filas.map(detalle).join('\n') || 'Sin resultados.');
    }
    case 'listar': {
      const filtros = [], valores = [];
      for (const c of ['tipo', 'pedido', 'autor']) if (op[c]) { filtros.push(`${c} = ?`); valores.push(op[c]); }
      const filas = db.prepare(`SELECT * FROM memoria ${filtros.length ? `WHERE ${filtros.join(' AND ')}` : ''} ORDER BY id DESC LIMIT ${Number(op.limite ?? 30)}`).all(...valores);
      return console.log(filas.map(linea).join('\n') || 'Sin entradas.');
    }
    case 'ver': {
      const fila = db.prepare('SELECT * FROM memoria WHERE id = ?').get(posicionales[0]);
      if (!fila) throw new Error(`No existe la entrada ${posicionales[0]}`);
      return console.log(detalle(fila));
    }
    case 'sincronizar': {
      exportar(db);
      return console.log(`Base sincronizada: ${db.prepare('SELECT count(*) n FROM memoria').get().n} entradas.`);
    }
    default: throw new Error(`Comando desconocido: ${comando}\n\n${AYUDA}`);
  }
}

try { main(); }
catch (e) { console.error(e.message); process.exit(1); }
