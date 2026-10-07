import { useToast } from '../utils/toast';
import { useState } from 'react';
import { apiFetch, apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { abrirScanner } from '../utils/scanner';
import { buscarNaOpenFoodFacts } from '../utils/openFoodFacts';
import { decimal, formatMoney, formatQuantity } from '../utils/decimal';
import {
  PageHeading,
  SearchField,
  Loading,
  LoadError,
  EmptyState,
  Modal,
  Field,
  Notice,
  Spinner,
  ConfirmDialog,
} from '../components/UI';

const UNIDADES = ['un', 'kg', 'L', 'g', 'ml'];
interface Categoria {
  id: number;
  nome: string;
}
interface Produto {
  id: number;
  nome: string;
  preco: string;
  stock: string;
  stockMinimo: string;
  unidade: string;
  categoriaId: number;
  categoriaNome?: string;
  codigoBarras?: string;
}
const FORM_VAZIO = {
  nome: '',
  preco: '',
  stock: '',
  stockMinimo: '5',
  unidade: 'un',
  categoriaId: '',
  codigoBarras: '',
};
const loadProducts = async (signal: AbortSignal) => {
  const [produtos, categorias] = await Promise.all([
    apiRequest<Produto[]>('/api/produtos', { signal }),
    apiRequest<Categoria[]>('/api/categorias', { signal }),
  ]);
  return { produtos, categorias };
};

export default function ProdutosPage() {
  const { data, loading, error, reload } = useResource(loadProducts);
  const produtos = data?.produtos || [];
  const categorias = data?.categorias || [];
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [stockFilter, setStockFilter] = useState('all');
  const [sort, setSort] = useState('name');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Produto | null>(null);
  const [form, setForm] = useState(FORM_VAZIO);
  const [offMsg, setOffMsg] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<Produto | null>(null);
  const save = useMutation();
  const scan = useMutation();
  const remove = useMutation();
  const toast = useToast();
  const filtered = produtos
    .filter(
      (p) =>
        (p.nome.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()) ||
          (p.codigoBarras || '').includes(search.trim())) &&
        (!category || p.categoriaId.toString() === category) &&
        (stockFilter === 'all' || decimal(p.stock).lte(p.stockMinimo)),
    )
    .sort((a, b) =>
      sort === 'price'
        ? decimal(a.preco).comparedTo(b.preco)
        : sort === 'stock'
          ? decimal(a.stock).comparedTo(b.stock)
          : a.nome.localeCompare(b.nome, 'pt'),
    );
  const setF = (patch: Partial<typeof form>) => setForm((previous) => ({ ...previous, ...patch }));
  const openNew = () => {
    setEditing(null);
    setForm({ ...FORM_VAZIO, categoriaId: categorias[0]?.id.toString() || '' });
    save.setError(null);
    scan.setError(null);
    setOffMsg(null);
    setShowModal(true);
  };
  const openEdit = (p: Produto) => {
    setEditing(p);
    setForm({
      nome: p.nome,
      preco: p.preco,
      stock: p.stock,
      stockMinimo: p.stockMinimo,
      unidade: p.unidade || 'un',
      categoriaId: p.categoriaId.toString(),
      codigoBarras: p.codigoBarras || '',
    });
    save.setError(null);
    scan.setError(null);
    setOffMsg(null);
    setShowModal(true);
  };
  const handleScan = () =>
    void scan.run(async () => {
      setOffMsg(null);
      const codigo = await abrirScanner();
      if (!codigo) return;
      setF({ codigoBarras: codigo });
      const response = await apiFetch(`/api/produtos/barcode/${encodeURIComponent(codigo)}`);
      if (response.ok) {
        const p = await response.json();
        throw new Error(`Este código já está registado no produto “${p.nome}”.`);
      }
      if (response.status !== 404)
        throw new Error('Não foi possível verificar este código. Tente novamente.');
      setOffMsg('A procurar produto online…');
      const result = await buscarNaOpenFoodFacts(codigo);
      if (result) {
        setForm((previous) => ({
          ...previous,
          nome:
            result.nome +
            (result.marca && !result.nome.toLowerCase().includes(result.marca.toLowerCase())
              ? ` ${result.marca}`
              : ''),
          unidade: result.unidade || previous.unidade,
        }));
        setOffMsg(`Produto encontrado: ${result.nome}. Confirme os dados antes de guardar.`);
      } else setOffMsg('Produto não encontrado online. Preencha o nome manualmente.');
    });
  const handleSave = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.nome.trim()) {
      save.setError('Preencha o nome do produto.');
      return;
    }
    void save.run(async () => {
      const body = {
        nome: form.nome.trim(),
        preco: form.preco,
        stock: form.stock || '0',
        stockMinimo: form.stockMinimo || '0',
        unidade: form.unidade || 'un',
        categoriaId: Number(form.categoriaId),
        ...(form.codigoBarras.trim() ? { codigoBarras: form.codigoBarras.trim() } : {}),
      };
      await apiRequest(editing ? `/api/produtos/${editing.id}` : '/api/produtos', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      });
      setShowModal(false);
      toast(editing ? 'Produto guardado.' : 'Produto criado.');
      void reload();
    });
  };
  const handleDelete = () =>
    void remove.run(async () => {
      if (!deleting) return;
      await apiRequest(`/api/produtos/${deleting.id}`, { method: 'DELETE' });
      setDeleting(null);
      toast('Produto removido.');
      void reload();
    });
  const clearFilters = () => {
    setSearch('');
    setCategory('');
    setStockFilter('all');
  };

  return (
    <div>
      <PageHeading
        title="Produtos"
        description="Preços, quantidades e reposição. O seu stock, à vista."
      >
        <button className="btn-primary" onClick={openNew} disabled={!data}>
          <span aria-hidden="true">➕</span> Adicionar produto
        </button>
      </PageHeading>
      <div className="toolbar">
        <SearchField value={search} onChange={setSearch} label="Pesquisar por nome ou código" />
        <Field id="product-category-filter" label="Categoria">
          <select
            id="product-category-filter"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
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
            onChange={(e) => setStockFilter(e.target.value)}
          >
            <option value="all">Todos os produtos</option>
            <option value="low">Stock baixo</option>
          </select>
        </Field>
        <Field id="product-sort" label="Ordenar por">
          <select id="product-sort" value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="name">Nome</option>
            <option value="price">Menor preço</option>
            <option value="stock">Menor stock</option>
          </select>
        </Field>
      </div>
      {error && <LoadError message={error} retry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : (
        data && (
          <div className="card">
            {produtos.length === 0 ? (
              <EmptyState
                title="O seu catálogo começa aqui"
                description="Adicione produtos para acompanhar o stock e começar a vender."
                icon="📦"
              >
                <button className="btn-secondary" onClick={openNew}>
                  ➕ Adicionar primeiro produto
                </button>
              </EmptyState>
            ) : filtered.length === 0 ? (
              <EmptyState
                title="Nenhum produto corresponde à pesquisa"
                description="Altere o nome, o código ou os filtros para encontrar o que procura."
                icon="🔍"
              >
                <button className="btn-secondary" onClick={clearFilters}>
                  🔄 Limpar filtros
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
                          </td>
                          <td data-label="Preço" className="numeric">
                            {formatMoney(p.preco)}
                          </td>
                          <td data-label="Stock" className="numeric">
                            <div className="stock-cell">
                              <span>{formatQuantity(p.stock, p.unidade)}</span>
                              {decimal(p.stock).lte(p.stockMinimo) && (
                                <span className="badge badge-warning">
                                  ⚠️ Stock baixo
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
                                onClick={() => openEdit(p)}
                                aria-label={`Editar ${p.nome}`}
                                title="Editar produto"
                              >
                                ✏️
                              </button>
                              <button
                                className="icon-btn delete"
                                onClick={() => {
                                  remove.setError(null);
                                  setDeleting(p);
                                }}
                                aria-label={`Remover ${p.nome}`}
                                title="Remover produto"
                              >
                                🗑️
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
        )
      )}
      {showModal && (
        <Modal
          title={editing ? 'Editar produto' : 'Adicionar produto'}
          onClose={() => setShowModal(false)}
          busy={save.pending || scan.pending}
        >
          <form onSubmit={handleSave}>
            <p className="required-note">Os campos com * são obrigatórios.</p>
            {save.error && <Notice>{save.error}</Notice>}
            {scan.error && <Notice>{scan.error}</Notice>}
            {offMsg && <Notice kind="info">{offMsg}</Notice>}
            {categorias.length === 0 && (
              <Notice kind="info">
                Crie uma categoria na página Categorias antes de guardar um produto.
              </Notice>
            )}
            <fieldset disabled={save.pending || scan.pending}>
              <Field id="product-barcode" label="Código de barras">
                <div className="input-action">
                  <input
                    id="product-barcode"
                    value={form.codigoBarras}
                    onChange={(e) => setF({ codigoBarras: e.target.value })}
                    placeholder="Opcional"
                  />
                  <button type="button" className="btn-secondary" onClick={handleScan}>
                    {scan.pending ? <Spinner size="small" /> : <span aria-hidden="true">📷</span>} Ler código
                  </button>
                </div>
              </Field>
              <Field id="product-name" label="Nome *">
                <input
                  id="product-name"
                  value={form.nome}
                  onChange={(e) => setF({ nome: e.target.value })}
                  placeholder="Nome do produto"
                  required
                />
              </Field>
              <div className="form-grid">
                <Field id="product-price" label="Preço (MT) *">
                  <input
                    id="product-price"
                    type="number"
                    min="0"
                    step="0.01"
                    inputMode="decimal"
                    value={form.preco}
                    onChange={(e) => setF({ preco: e.target.value })}
                    placeholder="0,00"
                    required
                  />
                </Field>
                <Field id="product-stock" label="Stock atual">
                  <input
                    id="product-stock"
                    type="number"
                    min="0"
                    step="0.001"
                    inputMode="decimal"
                    value={form.stock}
                    onChange={(e) => setF({ stock: e.target.value })}
                    placeholder="0"
                  />
                </Field>
                <Field id="product-minimum" label="Stock mínimo">
                  <input
                    id="product-minimum"
                    type="number"
                    min="0"
                    step="0.001"
                    inputMode="decimal"
                    value={form.stockMinimo}
                    onChange={(e) => setF({ stockMinimo: e.target.value })}
                  />
                </Field>
                <Field id="product-unit" label="Unidade">
                  <select
                    id="product-unit"
                    value={form.unidade}
                    onChange={(e) => setF({ unidade: e.target.value })}
                  >
                    {UNIDADES.map((unit) => (
                      <option key={unit}>{unit}</option>
                    ))}
                    {!UNIDADES.includes(form.unidade) && <option>{form.unidade}</option>}
                  </select>
                </Field>
              </div>
              <Field id="product-category" label="Categoria *">
                <select
                  id="product-category"
                  value={form.categoriaId}
                  onChange={(e) => setF({ categoriaId: e.target.value })}
                  required
                >
                  <option value="">Selecionar categoria</option>
                  {categorias.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nome}
                    </option>
                  ))}
                </select>
              </Field>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary" disabled={!categorias.length}>
                  {save.pending && <Spinner />}
                  {save.pending ? 'A guardar…' : editing ? 'Guardar alterações' : 'Criar produto'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}
      {deleting && (
        <ConfirmDialog
          title="Remover produto?"
          onClose={() => setDeleting(null)}
          onConfirm={handleDelete}
          busy={remove.pending}
          error={remove.error}
          danger
          label="Remover produto"
        >
          <p>
            Quer remover <strong>{deleting.nome}</strong>? Esta ação é permanente.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}
