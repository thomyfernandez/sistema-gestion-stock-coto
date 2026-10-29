import { useEffect, useId, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { ImagePlus, X } from 'lucide-react';
import type { ImagenCarga } from '../types';
import { Button, ErrorMessage } from './ui';

export interface ImageSelection { imagen?: ImagenCarga; preview?: string; loading: boolean; error: Error | null }
export function ImageUpload({ value, onChange, disabled }: { value: ImageSelection; onChange: Dispatch<SetStateAction<ImageSelection>>; disabled: boolean }) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  useEffect(() => {
    if (!file) { onChange({ loading: false, error: null }); return; }
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size === 0 || file.size > 2 * 1024 * 1024) {
      onChange({ loading: false, error: new Error('Elegí una imagen PNG o JPEG de hasta 2 MB.') }); return;
    }
    const reader = new FileReader();
    onChange({ loading: true, error: null });
    reader.onload = () => {
      const preview = String(reader.result);
      onChange({ loading: false, error: null, preview, imagen: { tipo: file.type, contenido: preview.slice(preview.indexOf(',') + 1) } });
    };
    reader.onerror = () => onChange({ loading: false, error: new Error('No se pudo leer la imagen. Volvé a seleccionarla.') });
    reader.readAsDataURL(file);
    return () => { reader.onload = null; reader.onerror = null; if (reader.readyState === FileReader.LOADING) reader.abort(); };
  }, [file, onChange]);
  return <div className="field"><label htmlFor={id}>Imagen del artículo <span className="caption">(opcional)</span></label><div className="image-upload">{value.preview ? <img src={value.preview} alt="Vista previa del artículo" /> : <span className="image-upload-placeholder"><ImagePlus size={32} aria-hidden="true" /></span>}<div><input id={id} ref={input} type="file" accept="image/png,image/jpeg" disabled={disabled} aria-describedby={`${id}-hint`} onChange={e => setFile(e.target.files?.[0] || null)} /><span className="field-hint" id={`${id}-hint`}>{value.loading ? 'Leyendo imagen…' : 'PNG o JPEG · hasta 2 MB · máximo 4096 × 4096 px'}</span></div>{file && <Button variant="ghost" className="icon-button" icon={X} aria-label="Quitar imagen" disabled={disabled} onClick={() => { setFile(null); if (input.current) input.current.value = ''; }} />}</div><ErrorMessage error={value.error} /></div>;
}
