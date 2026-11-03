/** Mismas reglas que el backend: minúsculas, sin duplicados y hasta 20 direcciones por lista. */
export const MAXIMO_EMAILS = 20;
const formato = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function parsearEmails(texto: string): { emails: string[]; invalidos: string[] } {
  const emails: string[] = [];
  const invalidos: string[] = [];
  for (const valor of texto.split(/[\s,;]+/).map(e => e.trim()).filter(Boolean)) {
    const email = valor.toLowerCase();
    if (email.length > 200 || !formato.test(email)) invalidos.push(valor);
    else if (!emails.includes(email)) emails.push(email);
  }
  return { emails, invalidos };
}

export function validarLista(texto: string): { emails: string[]; error: string | null } {
  const { emails, invalidos } = parsearEmails(texto);
  if (invalidos.length) return { emails, error: `${invalidos.length === 1 ? 'Este email no es válido' : 'Estos emails no son válidos'}: ${invalidos.join(', ')}` };
  if (emails.length > MAXIMO_EMAILS) return { emails, error: `Cada lista admite hasta ${MAXIMO_EMAILS} emails.` };
  return { emails, error: null };
}
