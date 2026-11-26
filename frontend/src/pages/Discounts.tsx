import { PanelPromociones } from '../components/Promociones';
import { PageHeading } from '../components/ui';

export function DiscountsPage() {
  return (
    <>
      <PageHeading
        eyebrow="Backoffice"
        title="Descuentos y promociones"
        text="Administrá los descuentos, 2×1 y beneficios por medio de pago de cada artículo."
      />
      <PanelPromociones />
    </>
  );
}
