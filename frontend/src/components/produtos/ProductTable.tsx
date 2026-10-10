import React from 'react';
import { AlertTriangle, History, Package, Pencil, RefreshCw, Search, Trash2, Plus } from 'lucide-react';
import { EmptyState, Spinner } from '../UI';
import { decimal, formatMoney, formatQuantity } from '../../utils/decimal';
import type { Produto } from './types';

interface ProductTableProps {
  produtos: Produto[];
  filtered: Produto[];
  loading: boolean;
  onOpenNew: () => void;
  onClearFilters: () => void;
  onEdit: (produto: Produto) => void;
  onHistorico: (produto: Produto) => void;
  onDelete: (produto: Produto) => void;
}

export const ProductTable: React.FC<ProductTableProps> = ({
  produtos,
  filtered,
  loading,
  onOpenNew,
  onClearFilters,
  onEdit,
  onHistorico,
  onDelete,
}) => {
  return (
    <div className="card">
      {produtos.length === 0 ? (
        <EmptyState
          title="O seu catálogo começa aqui"
          description="Adicione produtos para acompanhar o stock e começar a vender."
          icon={<Package size={36} strokeWidth={1.8} aria-hidden="true" />}
        >
          <button className="btn-secondary" onClick={onOpenNew}>
            <Plus size={16} strokeWidth={2} aria-hidden="true" /> Adicionar primeiro produto
          </button>
        </EmptyState>
      ) : filtered.length === 0 ? (
        <EmptyState
          title="Nenhum produto corresponde à pesquisa"
          description="Altere o nome, o código ou os filtros para encontrar o que procura."
          icon={<Search size={36} strokeWidth={1.8} aria-hidden="true" />}
        >
          <button className="btn-secondary" onClick={onClearFilters}>
            <RefreshCw size={14} strokeWidth={2} aria-hidden="true" /> Limpar filtros
          </button>
        </EmptyState>
      ) : (
        <>
          <div className="table-summary">
            <span>
              {filtered.length} de {produtos.length} produtos
            </span>
            {loading && (
              <span role="status">
                <Spinner /> A atualizar…
              </span>
            )}
          </div>
          <div className="table-wrapper">
            <table className="responsive-table">
              <caption className="sr-only">Produtos e níveis de stock</caption>
              <thead>
                <tr>
                  <th scope="col">Produto</th>
                  <th scope="col" className="numeric">
                    Preço
                  </th>
                  <th scope="col" className="numeric">
                    Custo
                  </th>
                  <th scope="col" className="numeric">
                    Margem
                  </th>
                  <th scope="col" className="numeric">
                    Stock
                  </th>
                  <th scope="col">Categoria</th>
                  <th scope="col">Código</th>
                  <th scope="col">Ações</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((p) => (
                  <tr key={p.id}>
                    <td data-label="Produto" className="cell-name">
                      {p.nome}
                      {p.ativo === false && (
                        <span className="badge badge-subtle" style={{ marginLeft: 6 }}>
                          Arquivado
                        </span>
                      )}
                    </td>
                    <td data-label="Preço" className="numeric">
                      {formatMoney(p.preco)}
                    </td>
                    <td data-label="Custo" className="numeric">
                      {formatMoney(p.custo || '0.00')}
                    </td>
                    <td data-label="Margem" className="numeric">
                      {p.margem ? `${p.margem}%` : '-'}
                    </td>
                    <td data-label="Stock" className="numeric">
                      <div className="stock-cell">
                        <span>{formatQuantity(p.stock, p.unidade)}</span>
                        {decimal(p.stock).lte(p.stockMinimo) && (
                          <span className="badge badge-warning">
                            <AlertTriangle size={12} strokeWidth={2} aria-hidden="true" /> Stock baixo
                          </span>
                        )}
                      </div>
                    </td>
                    <td data-label="Categoria" className="cell-secondary">
                      {p.categoriaNome || 'Sem categoria'}
                    </td>
                    <td data-label="Código" className="cell-secondary">
                      {p.codigoBarras || 'Não definido'}
                    </td>
                    <td data-label="Ações">
                      <div className="action-group">
                        <button
                          className="icon-btn"
                          onClick={() => onEdit(p)}
                          aria-label={`Editar ${p.nome}`}
                          title="Editar produto"
                        >
                          <Pencil size={15} strokeWidth={2} aria-hidden="true" />
                        </button>
                        <button
                          className="icon-btn"
                          onClick={() => void onHistorico(p)}
                          aria-label={`Histórico de ${p.nome}`}
                          title="Histórico de movimentos"
                        >
                          <History size={15} strokeWidth={2} aria-hidden="true" />
                        </button>
                        <button
                          className="icon-btn delete"
                          onClick={() => onDelete(p)}
                          aria-label={`Remover ${p.nome}`}
                          title="Arquivar ou remover produto"
                        >
                          <Trash2 size={15} strokeWidth={2} aria-hidden="true" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
};
