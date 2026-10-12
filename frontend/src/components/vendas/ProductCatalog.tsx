import React from 'react';
import { LayoutGrid, List, Plus, ScanLine, Search, Sparkles } from 'lucide-react';
import { Field, Spinner } from '../UI';
import { decimalSeguro, formatMoney, formatQuantity } from '../../utils/decimal';
import type { Categoria, Produto } from './types';

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
  categorias?: Categoria[];
  selectedCategoriaId?: number | string | null;
  onSelectCategoria?: (id: number | string | null) => void;
  onQuickAdd?: (p: Produto) => void;
  loading?: boolean;
  viewMode?: 'grid' | 'list';
  onToggleViewMode?: () => void;
  showGrid?: boolean;
}

const ProductCatalogComponent: React.FC<ProductCatalogProps> = ({
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
  categorias = [],
  selectedCategoriaId = null,
  onSelectCategoria,
  onQuickAdd,
  loading = false,
  viewMode = 'grid',
  onToggleViewMode,
  showGrid = true,
}) => {
  // Filtro de produtos para grelha visual rápida
  const gridProdutos = React.useMemo(() => {
    let lista = produtos;
    if (selectedCategoriaId !== null && selectedCategoriaId !== undefined && selectedCategoriaId !== 'todas') {
      lista = lista.filter((p) => String(p.categoriaId) === String(selectedCategoriaId));
    }
    if (busca.trim()) {
      const q = busca.trim().toLowerCase();
      lista = lista.filter(
        (p) =>
          p.nome.toLowerCase().includes(q) ||
          (p.codigoBarras && p.codigoBarras.toLowerCase().includes(q))
      );
    }
    return lista;
  }, [produtos, selectedCategoriaId, busca]);

  const handleCardClick = (p: Produto) => {
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(40);
      } catch {}
    }
    if (onQuickAdd) {
      onQuickAdd(p);
    } else {
      onSelectProduct(p);
      onAddSelected();
    }
  };

  return (
    <div className="vendas-pos-container">
      {/* 1. BARRA DE PESQUISA, QUANTIDADE E SCANNER */}
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
              placeholder="Nome ou código de barras"
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
                  } else if (gridProdutos.length === 1) {
                    handleCardClick(gridProdutos[0]);
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
            justifyContent: 'space-between',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
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
              Scanner contínuo
            </label>
          </div>

          {onToggleViewMode && (
            <button
              type="button"
              className="btn-secondary"
              onClick={onToggleViewMode}
              title="Alternar entre visualização em grelha e lista"
              style={{ padding: '6px 10px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 5 }}
            >
              {viewMode === 'grid' ? <List size={14} /> : <LayoutGrid size={14} />}
              {viewMode === 'grid' ? 'Lista compacta' : 'Grelha'}
            </button>
          )}
        </div>
      </div>

      {/* 2. FILTRO DE CATEGORIAS EM CHIPS HORIZONTAIS */}
      {showGrid && categorias.length > 0 && onSelectCategoria && (
        <div className="category-chips-bar" role="toolbar" aria-label="Filtro de categorias de produtos">
          <button
            type="button"
            className={`category-chip ${selectedCategoriaId === null || selectedCategoriaId === 'todas' ? 'active' : ''}`}
            onClick={() => onSelectCategoria(null)}
          >
            <Sparkles size={14} /> Todas as categorias
          </button>
          {categorias.map((cat) => {
            const isSelected = String(selectedCategoriaId) === String(cat.id);
            return (
              <button
                key={cat.id}
                type="button"
                className={`category-chip ${isSelected ? 'active' : ''}`}
                onClick={() => onSelectCategoria(isSelected ? null : cat.id)}
              >
                {cat.nome}
              </button>
            );
          })}
        </div>
      )}

      {/* 3. GRELHA DE PRODUTOS TOUCH / LISTA COMPACTA */}
      {showGrid && (
        <div className={`vendas-products-grid ${viewMode === 'list' ? 'list-view' : ''}`}>
          {loading ? (
            Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="product-card-skeleton" aria-hidden="true">
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div className="skeleton-line medium" />
                  <div className="skeleton-line short" />
                </div>
                <div className="skeleton-line full" />
              </div>
            ))
          ) : gridProdutos.length === 0 ? (
            <div
              style={{
                gridColumn: '1 / -1',
                padding: '30px 16px',
                textAlign: 'center',
                background: 'var(--bg-subtle)',
                borderRadius: 10,
                border: '1px dashed var(--border-color)',
              }}
            >
              <p style={{ margin: '0 0 6px 0', fontSize: 14, fontWeight: 600 }}>
                {produtos.length === 0
                  ? 'Ainda não tens produtos cadastrados.'
                  : 'Nenhum produto encontrado nesta categoria ou pesquisa.'}
              </p>
              <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                {produtos.length === 0
                  ? 'Cadastre novos produtos para iniciar as vendas rápidas.'
                  : 'Tente alterar os termos da pesquisa ou selecionar outra categoria.'}
              </span>
            </div>
          ) : (
            gridProdutos.map((p) => {
              const stockNum = decimalSeguro(p.stock).toNumber();
              const isStockLow = stockNum <= 5;
              const catObj = categorias.find((c) => c.id === p.categoriaId);

              return (
                <div
                  key={p.id}
                  className={`product-card-pos ${viewMode === 'list' ? 'list-mode' : ''}`}
                  onClick={() => handleCardClick(p)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      handleCardClick(p);
                    }
                  }}
                  aria-label={`${p.nome}, Preço: ${formatMoney(p.preco)}, Stock: ${formatQuantity(p.stock, p.unidade || 'un')}`}
                >
                  <div className="prod-info">
                    <span className="prod-title">{p.nome}</span>
                    {catObj && <span className="prod-category">{catObj.nome}</span>}
                  </div>
                  <div className="prod-footer">
                    <span className="prod-price">{formatMoney(p.preco)}</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      {isStockLow && (
                        <span className="stock-low-badge" title="Stock baixo">
                          Baixo
                        </span>
                      )}
                      <span className="prod-stock">
                        {formatQuantity(p.stock, p.unidade || 'un')}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
};

export const ProductCatalog = React.memo(ProductCatalogComponent);
