import { SearchField, Field } from '../UI';
import type { Categoria } from './types';

interface CategoryFilterProps {
  search: string;
  onSearchChange: (value: string) => void;
  category: string;
  onCategoryChange: (value: string) => void;
  categorias: Categoria[];
  stockFilter: string;
  onStockFilterChange: (value: string) => void;
  statusFilter: string;
  onStatusFilterChange: (value: string) => void;
  sort: string;
  onSortChange: (value: string) => void;
}

export const CategoryFilter: React.FC<CategoryFilterProps> = ({
  search,
  onSearchChange,
  category,
  onCategoryChange,
  categorias,
  stockFilter,
  onStockFilterChange,
  statusFilter,
  onStatusFilterChange,
  sort,
  onSortChange,
}) => {
  return (
    <div className="toolbar">
      <SearchField value={search} onChange={onSearchChange} label="Pesquisar por nome ou código" />
      <Field id="product-category-filter" label="Categoria">
        <select
          id="product-category-filter"
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
        >
          <option value="">Todas as categorias</option>
          {categorias.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nome}
            </option>
          ))}
        </select>
      </Field>
      <Field id="product-stock-filter" label="Stock">
        <select
          id="product-stock-filter"
          value={stockFilter}
          onChange={(e) => onStockFilterChange(e.target.value)}
        >
          <option value="all">Todos os produtos</option>
          <option value="low">Stock baixo</option>
        </select>
      </Field>
      <Field id="product-status-filter" label="Estado">
        <select
          id="product-status-filter"
          value={statusFilter}
          onChange={(e) => onStatusFilterChange(e.target.value)}
        >
          <option value="active">Apenas ativos</option>
          <option value="archived">Apenas arquivados</option>
          <option value="all">Todos (ativos e arquivados)</option>
        </select>
      </Field>
      <Field id="product-sort" label="Ordenar por">
        <select id="product-sort" value={sort} onChange={(e) => onSortChange(e.target.value)}>
          <option value="name">Nome</option>
          <option value="price">Menor preço</option>
          <option value="stock">Menor stock</option>
        </select>
      </Field>
    </div>
  );
};
