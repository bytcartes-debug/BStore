import React from 'react';
import { Plus, ScanLine, Search } from 'lucide-react';
import { Field, Spinner } from '../UI';
import { formatMoney, formatQuantity } from '../../utils/decimal';
import type { Produto } from './types';

interface ProductCatalogProps {
  busca: string;
  onBuscaChange: (val: string) => void;
  selectedProd: Produto | null;
  onSelectProduct: (p: Produto) => void;
  suggestions: Produto[];
  showSuggestions: boolean;
  onSetShowSuggestions: (show: boolean) => void;
  activeOption: number;
  onSetActiveOption: React.Dispatch<React.SetStateAction<number>>;
  produtos: Produto[];
  quantity: string;
  onQuantityChange: (val: string) => void;
  onAddSelected: () => void;
  onScan: () => void;
  isScanning: boolean;
  continuousScan: boolean;
  onContinuousScanChange: (val: boolean) => void;
  inputRef: React.RefObject<HTMLInputElement | null>;
  suggestionsRef: React.RefObject<HTMLUListElement | null>;
}

export const ProductCatalog: React.FC<ProductCatalogProps> = ({
  busca,
  onBuscaChange,
  selectedProd,
  onSelectProduct,
  suggestions,
  showSuggestions,
  onSetShowSuggestions,
  activeOption,
  onSetActiveOption,
  produtos,
  quantity,
  onQuantityChange,
  onAddSelected,
  onScan,
  isScanning,
  continuousScan,
  onContinuousScanChange,
  inputRef,
  suggestionsRef,
}) => {
  return (
    <div className="sale-add-row">
      <div className="product-combobox">
        <label htmlFor="sale-product" className="field-label">
          Pesquisar produto
        </label>
        <div className="search-field">
          <span className="search-icon" aria-hidden="true">
            <Search size={18} strokeWidth={2} />
          </span>
          <input
            id="sale-product"
            ref={inputRef}
            role="combobox"
            aria-autocomplete="list"
            aria-expanded={showSuggestions}
            aria-controls="sale-suggestions"
            aria-activedescendant={
              showSuggestions && activeOption >= 0
                ? `sale-option-${suggestions[activeOption]?.id}`
                : undefined
            }
            autoComplete="off"
            value={busca}
            placeholder="Nome do produto"
            onChange={(e) => {
              onBuscaChange(e.target.value);
              onSetShowSuggestions(true);
              onSetActiveOption(-1);
            }}
            onFocus={() => {
              if (!selectedProd) onSetShowSuggestions(true);
            }}
            onBlur={() => onSetShowSuggestions(false)}
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown') {
                event.preventDefault();
                onSetShowSuggestions(true);
                onSetActiveOption((index) =>
                  Math.min(index + 1, suggestions.length - 1),
                );
              }
              if (event.key === 'ArrowUp') {
                event.preventDefault();
                onSetActiveOption((index) => Math.max(0, index - 1));
              }
              if (event.key === 'Escape' && showSuggestions) {
                event.preventDefault();
                onSetShowSuggestions(false);
              }
              if (event.key === 'Enter') {
                event.preventDefault();
                if (showSuggestions && activeOption >= 0 && suggestions[activeOption]) {
                  onSelectProduct(suggestions[activeOption]);
                } else if (selectedProd) {
                  onAddSelected();
                }
              }
            }}
          />
        </div>
        {showSuggestions && (
          <ul
            id="sale-suggestions"
            ref={suggestionsRef}
            role="listbox"
            className="product-suggestions"
          >
            {suggestions.length === 0 ? (
              <li className="suggestion-empty" role="option" aria-disabled="true">
                {produtos.length === 0
                  ? 'Adicione produtos na página Produtos para começar.'
                  : 'Nenhum produto encontrado.'}
              </li>
            ) : (
              suggestions.map((p, index) => (
                <li
                  id={`sale-option-${p.id}`}
                  role="option"
                  key={p.id}
                  aria-selected={index === activeOption}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => onSelectProduct(p)}
                >
                  <strong>{p.nome}</strong>
                  <span>
                    {formatMoney(p.preco)} · Stock:{' '}
                    {formatQuantity(p.stock, p.unidade || 'un')}
                  </span>
                </li>
              ))
            )}
          </ul>
        )}
      </div>

      <Field
        id="sale-quantity"
        label={selectedProd ? `Qtd (${selectedProd.unidade || 'un'})` : 'Qtd'}
      >
        <input
          id="sale-quantity"
          type="number"
          min="0.001"
          step="0.001"
          inputMode="decimal"
          value={quantity}
          onChange={(e) => onQuantityChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              onAddSelected();
            }
          }}
        />
      </Field>

      <button
        type="button"
        className="btn-primary"
        onClick={onAddSelected}
        disabled={!selectedProd}
      >
        <Plus size={16} strokeWidth={2.2} aria-hidden="true" /> Adicionar
      </button>

      <div
        style={{
          gridColumn: '1 / -1',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <button
          type="button"
          className="btn-secondary"
          onClick={onScan}
        >
          {isScanning ? (
            <Spinner size="small" />
          ) : (
            <ScanLine size={16} strokeWidth={2} aria-hidden="true" />
          )}{' '}
          Ler código
        </button>
        <label
          style={{
            display: 'flex',
            gap: 6,
            alignItems: 'center',
            fontSize: 13,
            cursor: 'pointer',
          }}
        >
          <input
            type="checkbox"
            checked={continuousScan}
            onChange={(e) => onContinuousScanChange(e.target.checked)}
          />
          Scanner contínuo (reabre após leitura)
        </label>
      </div>
    </div>
  );
};
