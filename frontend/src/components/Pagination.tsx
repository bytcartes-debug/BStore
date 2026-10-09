import { useId } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

export default function Pagination({
  page,
  pageSize,
  total,
  loading,
  onPage,
  onPageSize,
}: {
  page: number;
  pageSize: number;
  total: number;
  loading: boolean;
  onPage: (page: number) => void;
  onPageSize: (size: number) => void;
}) {
  const id = useId();
  const pages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <nav className="pagination" aria-label="Paginação da lista">
      <p aria-live="polite">
        {total === 0
          ? '0 registos'
          : `${(page - 1) * pageSize + 1}–${Math.min(page * pageSize, total)} de ${total} registos`}
      </p>
      <div className="pagination-size">
        <label htmlFor={id}>Por página</label>
        <select
          id={id}
          value={pageSize}
          onChange={(e) => onPageSize(Number(e.target.value))}
          disabled={loading}
        >
          <option value={25}>25</option>
          <option value={50}>50</option>
          <option value={100}>100</option>
        </select>
      </div>
      <div className="pagination-actions">
        <button
          className="icon-btn"
          aria-label="Página anterior"
          disabled={loading || page <= 1}
          onClick={() => onPage(page - 1)}
        >
          <ChevronLeft size={16} aria-hidden="true" />
        </button>
        <span className="pagination-current">
          Página {page} de {pages}
        </span>
        <button
          className="icon-btn"
          aria-label="Próxima página"
          disabled={loading || page >= pages}
          onClick={() => onPage(page + 1)}
        >
          <ChevronRight size={16} aria-hidden="true" />
        </button>
      </div>
    </nav>
  );
}
