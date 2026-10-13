import React, { useState } from 'react';
import { Tag } from 'lucide-react';
import { Modal, Field } from '../UI';
import { parseDecimalInput } from '../../utils/decimal';
import type { Categoria } from './types';

export interface FastSaleAvulsaModalProps {
  categorias?: Categoria[];
  onClose: () => void;
  onConfirm: (dados: {
    descricao: string;
    preco: string;
    categoriaId?: number | string;
  }) => void;
}

export const FastSaleAvulsaModal: React.FC<FastSaleAvulsaModalProps> = ({
  categorias = [],
  onClose,
  onConfirm,
}) => {
  const [descricao, setDescricao] = useState('');
  const [valor, setValor] = useState('');
  const [categoriaId, setCategoriaId] = useState<string>('');
  const [erro, setErro] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const valorDec = parseDecimalInput(valor);
    if (!valorDec || valorDec.lte(0)) {
      setErro('Indique um valor válido maior que zero.');
      return;
    }

    const nomeItem = descricao.trim() || 'Item avulso (Diversos)';
    onConfirm({
      descricao: nomeItem,
      preco: valorDec.toFixed(2),
      categoriaId: categoriaId ? Number(categoriaId) : undefined,
    });
  };

  return (
    <Modal title="Venda Avulsa / Sem Cadastro" onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 16 }}>
          Adicione rapidamente um produto não cadastrado ao carrinho indicando o preço e descrição.
        </p>

        {erro && (
          <div style={{ color: '#dc2626', fontSize: 13, marginBottom: 12, fontWeight: 500 }}>
            {erro}
          </div>
        )}

        <Field id="avulso-valor" label="Preço de venda (MT)">
          <input
            id="avulso-valor"
            type="number"
            step="0.01"
            min="0.01"
            inputMode="decimal"
            placeholder="0.00"
            value={valor}
            onChange={(e) => {
              setValor(e.target.value);
              setErro(null);
            }}
            required
            autoFocus
            style={{ fontSize: 18, fontWeight: 600 }}
          />
        </Field>

        <Field id="avulso-desc" label="Descrição do item (opcional)">
          <input
            id="avulso-desc"
            type="text"
            placeholder="Ex: Recarga, Embalagem, Serviço, etc."
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
          />
        </Field>

        {categorias.length > 0 && (
          <Field id="avulso-cat" label="Categoria (opcional)">
            <select
              id="avulso-cat"
              value={categoriaId}
              onChange={(e) => setCategoriaId(e.target.value)}
            >
              <option value="">Nenhuma / Diversos</option>
              {categorias.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome}
                </option>
              ))}
            </select>
          </Field>
        )}

        <div className="modal-actions" style={{ marginTop: 20 }}>
          <button type="button" className="btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button
            type="submit"
            className="btn-primary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <Tag size={16} /> Adicionar ao Carrinho
          </button>
        </div>
      </form>
    </Modal>
  );
};
