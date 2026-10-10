import React from 'react';
import { ArrowDownLeft, Camera, ChevronDown, ChevronUp, SlidersHorizontal } from 'lucide-react';
import { Modal, Field, Notice, Spinner } from '../UI';
import { formatQuantity } from '../../utils/decimal';
import { UNIDADES, type Categoria, type Produto, type ProdutoFormData } from './types';

interface ProductFormModalProps {
  editing: Produto | null;
  form: ProdutoFormData;
  onChangeForm: (patch: Partial<ProdutoFormData>) => void;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  isSaving: boolean;
  saveError: string | null;
  isScanning: boolean;
  scanError: string | null;
  offMsg: string | null;
  categorias: Categoria[];
  showDetails: boolean;
  onToggleDetails: () => void;
  onScan: () => void;
  onOpenEntrada: (p: Produto) => void;
  onOpenAjuste: (p: Produto) => void;
}

export const ProductFormModal: React.FC<ProductFormModalProps> = ({
  editing,
  form,
  onChangeForm,
  onClose,
  onSubmit,
  isSaving,
  saveError,
  isScanning,
  scanError,
  offMsg,
  categorias,
  showDetails,
  onToggleDetails,
  onScan,
  onOpenEntrada,
  onOpenAjuste,
}) => {
  return (
    <Modal
      title={editing ? 'Editar produto' : 'Adicionar produto'}
      onClose={onClose}
      busy={isSaving || isScanning}
    >
      <form onSubmit={onSubmit}>
        <p className="required-note">Os campos com * são obrigatórios.</p>
        {saveError && <Notice>{saveError}</Notice>}
        {scanError && <Notice>{scanError}</Notice>}
        {offMsg && <Notice kind="info">{offMsg}</Notice>}

        <fieldset disabled={isSaving || isScanning}>
          <div className="product-scan-cta-card">
            <button
              type="button"
              className="btn-product-scan-cta"
              onClick={onScan}
              disabled={isSaving || isScanning}
              title="Ler QR code ou código de barras com a câmara"
            >
              <span className="scan-cta-icon-wrapper" aria-hidden="true">
                {isScanning ? <Spinner size="small" /> : <Camera size={20} strokeWidth={2.2} />}
              </span>
              <div className="scan-cta-text">
                <span className="scan-cta-title">
                  {form.codigoBarras ? 'Ler outro código com câmara' : 'Ler código com câmara'}
                </span>
                <span className="scan-cta-sub">
                  {form.codigoBarras
                    ? `Código associado: ${form.codigoBarras}`
                    : 'Aponte a câmara para preencher os dados'}
                </span>
              </div>
            </button>
          </div>
          <Field id="product-name" label="Nome *">
            <input
              id="product-name"
              value={form.nome}
              onChange={(e) => onChangeForm({ nome: e.target.value })}
              placeholder="Nome do produto"
              required
            />
          </Field>

          <Field id="product-price" label="Preço (MT) *">
            <input
              id="product-price"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={form.preco}
              onChange={(e) => onChangeForm({ preco: e.target.value })}
              placeholder="0,00"
              required
            />
          </Field>

          {editing && (
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: 'block', fontSize: '0.875rem', marginBottom: 4 }}>
                Stock atual (apenas leitura)
              </label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: '1.1rem', fontWeight: 600 }}>
                  {formatQuantity(editing.stock, editing.unidade)}
                </span>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => {
                    onClose();
                    onOpenEntrada(editing);
                  }}
                >
                  <ArrowDownLeft size={15} strokeWidth={2} aria-hidden="true" /> Entrada
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => {
                    onClose();
                    onOpenAjuste(editing);
                  }}
                >
                  <SlidersHorizontal size={15} strokeWidth={2} aria-hidden="true" /> Ajuste
                </button>
              </div>
            </div>
          )}

          <div style={{ margin: '16px 0' }}>
            <button
              type="button"
              className="btn-secondary"
              onClick={onToggleDetails}
              style={{
                width: '100%',
                textAlign: 'left',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                {showDetails ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                {showDetails ? 'Ocultar detalhes' : 'Mais detalhes (Custo, unidade, categoria...)'}
              </span>
            </button>
          </div>

          {showDetails && (
            <div className="more-details-panel">
              <div className="form-grid">
                <Field id="product-cost" label="Preço de custo (MT)">
                  <input
                    id="product-cost"
                    type="number"
                    min="0"
                    step="0.01"
                    inputMode="decimal"
                    value={form.custo}
                    onChange={(e) => onChangeForm({ custo: e.target.value })}
                    placeholder="0,00"
                  />
                </Field>

                {!editing && (
                  <Field id="product-stock-initial" label="Stock inicial">
                    <input
                      id="product-stock-initial"
                      type="number"
                      min="0"
                      step="0.001"
                      inputMode="decimal"
                      value={form.stock}
                      onChange={(e) => onChangeForm({ stock: e.target.value })}
                      placeholder="0"
                    />
                  </Field>
                )}

                <Field id="product-minimum" label="Stock mínimo">
                  <input
                    id="product-minimum"
                    type="number"
                    min="0"
                    step="0.001"
                    inputMode="decimal"
                    value={form.stockMinimo}
                    onChange={(e) => onChangeForm({ stockMinimo: e.target.value })}
                  />
                </Field>

                <Field id="product-unit" label="Unidade">
                  <select
                    id="product-unit"
                    value={form.unidade}
                    onChange={(e) => onChangeForm({ unidade: e.target.value })}
                  >
                    {UNIDADES.map((unit) => (
                      <option key={unit}>{unit}</option>
                    ))}
                    {!UNIDADES.includes(form.unidade) && <option>{form.unidade}</option>}
                  </select>
                </Field>
              </div>

              <Field id="product-category" label="Categoria">
                <select
                  id="product-category"
                  value={form.categoriaId}
                  onChange={(e) => onChangeForm({ categoriaId: e.target.value })}
                >
                  <option value="">Geral (automática se não escolhida)</option>
                  {categorias.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nome}
                    </option>
                  ))}
                </select>
              </Field>

              <Field id="product-barcode" label="Código de barras ou QR Code">
                <div className="input-action">
                  <input
                    id="product-barcode"
                    value={form.codigoBarras}
                    onChange={(e) => onChangeForm({ codigoBarras: e.target.value })}
                    placeholder="Opcional (ex: 560123456789)"
                  />
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={onScan}
                    disabled={isSaving || isScanning}
                    title="Ler com a câmara"
                  >
                    {isScanning ? (
                      <Spinner size="small" />
                    ) : (
                      <Camera size={16} strokeWidth={2} aria-hidden="true" />
                    )}{' '}
                    Ler câmara
                  </button>
                </div>
              </Field>
            </div>
          )}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn-primary">
              {isSaving && <Spinner />}
              {isSaving ? 'A guardar…' : editing ? 'Guardar alterações' : 'Criar produto'}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  );
};
