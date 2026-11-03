import { render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { expect } from 'vitest';
import { StoreProvider, useStore } from '../store';

type StoreActual = ReturnType<typeof useStore>;

function Sonda({ store, children }: { store: { actual?: StoreActual }; children?: ReactNode }) {
  const actual = useStore();
  store.actual = actual;
  return actual.data ? <><span data-testid="revision">{actual.revision}</span>{children}</> : null;
}

/** Monta la vista dentro del store real y espera la primera carga completa. */
export async function renderConStore(ui?: ReactNode) {
  const store: { actual?: StoreActual } = {};
  const resultado = render(<StoreProvider><Sonda store={store}>{ui}</Sonda></StoreProvider>);
  await waitFor(() => expect(screen.getByTestId('revision')).toBeInTheDocument());
  return { ...resultado, store: () => store.actual!, revision: () => Number(screen.getByTestId('revision').textContent) };
}
