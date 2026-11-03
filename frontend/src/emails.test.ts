import { describe, expect, it } from 'vitest';
import { MAXIMO_EMAILS, parsearEmails, validarLista } from './emails';

describe('listas de emails', () => {
  it('acepta líneas, comas y punto y coma; normaliza y quita duplicados conservando el orden', () => {
    expect(parsearEmails(' Jefe@COTO.example\ndeposito@coto.example, jefe@coto.example;\n\n')).toEqual({
      emails: ['jefe@coto.example', 'deposito@coto.example'], invalidos: [],
    });
  });
  it('informa cada dirección inválida tal como se escribió', () => {
    expect(validarLista('ventas@coto, ok@coto.example, Sin-Arroba').error).toBe('Estos emails no son válidos: ventas@coto, Sin-Arroba');
    expect(validarLista('ventas@coto').error).toBe('Este email no es válido: ventas@coto');
  });
  it(`limita cada lista a ${MAXIMO_EMAILS} direcciones y permite vaciarla`, () => {
    const muchos = Array.from({ length: MAXIMO_EMAILS + 1 }, (_, i) => `u${i}@coto.example`).join('\n');
    expect(validarLista(muchos).error).toBe(`Cada lista admite hasta ${MAXIMO_EMAILS} emails.`);
    expect(validarLista('   ')).toEqual({ emails: [], error: null });
  });
});
