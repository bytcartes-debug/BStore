import { useState } from 'react';
import { Pencil, Plus, RefreshCw, Search, Tag, Trash2 } from 'lucide-react';
import { useToast } from '../utils/toast';
import { apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { formatMoney, formatQuantity } from '../utils/decimal';
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
import CategoryIcon from '../components/CategoryIcon';
import { categoryIcons, categoryIconKey } from '../utils/categoryIcons';

interface Categoria {
  id: number;
  nome: string;
  descricao: string;
  icone: string;
  totalProdutos?: number;
}
interface ProdutoSimples {
  id: number;
  nome: string;
  preco: string;
  stock: string;
  unidade?: string;
}
const loadCategories = (signal: AbortSignal) =>
  apiRequest<Categoria[]>('/api/categorias', { signal });
const emptyForm = { nome: '', descricao: '', icone: 'tag' };

export default function CategoriasPage() {
  const { data, loading, error, reload } = useResource(loadCategories);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Categoria | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [confirmDelete, setConfirmDelete] = useState<{
    cat: Categoria;
    produtos: ProdutoSimples[];
  } | null>(null);
  const save = useMutation();
  const remove = useMutation();
  const inspect = useMutation();
  const toast = useToast();
  const categories = data || [];
  const filtered = categories.filter((item) =>
    item.nome.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  );
  const openNew = () => {
    setEditing(null);
    setForm(emptyForm);
    save.setError(null);
    setShowModal(true);
  };
  const openEdit = (cat: Categoria) => {
    setEditing(cat);
    setForm({ nome: cat.nome, descricao: cat.descricao || '', icone: cat.icone || 'tag' });
    save.setError(null);
    setShowModal(true);
  };
  const handleSave = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.nome.trim()) {
      save.setError('Preencha o nome da categoria.');
      return;
    }
    void save.run(async () => {
      await apiRequest(editing ? `/api/categorias/${editing.id}` : '/api/categorias', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify({ ...form, nome: form.nome.trim(), descricao: form.descricao.trim() }),
      });
      setShowModal(false);
      toast(editing ? 'Categoria guardada.' : 'Categoria criada.');
      void reload();
    });
  };
  const inspectDelete = (cat: Categoria) =>
    void inspect.run(async () => {
      const produtos = await apiRequest<ProdutoSimples[]>(`/api/categorias/${cat.id}/produtos`);
      remove.setError(null);
      setConfirmDelete({ cat, produtos });
    });
  const handleDelete = () =>
    void remove.run(async () => {
      if (!confirmDelete) return;
      await apiRequest(`/api/categorias/${confirmDelete.cat.id}`, { method: 'DELETE' });
      setConfirmDelete(null);
      toast('Categoria removida.');
      void reload();
    });

  return (
    <div className="categorias-page">
      <PageHeading
        title="Categorias"
        description="Um lugar para cada produto. Organize o seu catálogo."
      >
        <button className="btn-primary" onClick={openNew}>
          <Plus size={16} strokeWidth={2.2} aria-hidden="true" /> Nova categoria
        </button>
      </PageHeading>

      <div className="toolbar">
        <SearchField value={search} onChange={setSearch} label="Pesquisar categorias" />
        <span className="result-count">
          {filtered.length} de {categories.length} categorias
        </span>
      </div>

      {error && <LoadError message={error} retry={reload} />}
      {inspect.error && <Notice>{inspect.error}</Notice>}

      {loading && !data ? (
        <Loading label="A carregar as categorias da loja…" />
      ) : (
        data &&
        (categories.length === 0 ? (
          <EmptyState
            title="Comece a organizar a sua loja"
            description="Crie a primeira categoria para depois adicionar os seus produtos."
            icon={<Tag size={36} strokeWidth={1.8} aria-hidden="true" />}
          >
            <button className="btn-secondary" onClick={openNew}>
              <Plus size={16} strokeWidth={2} aria-hidden="true" /> Criar primeira categoria
            </button>
          </EmptyState>
        ) : filtered.length === 0 ? (
          <EmptyState
            title="Nenhuma categoria encontrada"
            description="Experimente outro nome ou limpe a pesquisa."
            icon={<Search size={36} strokeWidth={1.8} aria-hidden="true" />}
          >
            <button className="btn-secondary" onClick={() => setSearch('')}>
              <RefreshCw size={14} strokeWidth={2} aria-hidden="true" /> Limpar pesquisa
            </button>
          </EmptyState>
        ) : (
          <>
            {loading && (
              <p className="refresh-state" role="status">
                <Spinner size="small" /> A atualizar categorias…
              </p>
            )}
            <div className="category-grid">
              {filtered.map((cat) => (
                <article key={cat.id} className="card category-card">
                  <div className="category-icon">
                    <CategoryIcon value={cat.icone || 'tag'} size={28} />
                  </div>
                  <div className="category-details">
                    <h3>{cat.nome}</h3>
                    <p className="category-description">{cat.descricao || 'Sem descrição'}</p>
                  </div>
                  <div className="category-footer">
                    <span className="category-count">
                      {cat.totalProdutos !== undefined
                        ? `${cat.totalProdutos} produto${cat.totalProdutos === 1 ? '' : 's'}`
                        : 'Categoria de produtos'}
                    </span>
                    <div className="action-group">
                      <button
                        className="icon-btn"
                        title={`Editar ${cat.nome}`}
                        aria-label={`Editar categoria ${cat.nome}`}
                        onClick={() => openEdit(cat)}
                      >
                        <Pencil size={15} strokeWidth={2} aria-hidden="true" />
                      </button>
                      <button
                        className="icon-btn delete"
                        title={`Remover ${cat.nome}`}
                        aria-label={`Remover categoria ${cat.nome}`}
                        disabled={inspect.pending}
                        onClick={() => inspectDelete(cat)}
                      >
                        <Trash2 size={15} strokeWidth={2} aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </>
        ))
      )}

      {showModal && (
        <Modal
          title={editing ? 'Editar categoria' : 'Nova categoria'}
          onClose={() => setShowModal(false)}
          busy={save.pending}
        >
          <form onSubmit={handleSave}>
            <p className="required-note">Os campos com * são obrigatórios.</p>
            {save.error && <Notice>{save.error}</Notice>}
            <fieldset disabled={save.pending}>
              <Field id="category-name" label="Nome *">
                <input
                  id="category-name"
                  value={form.nome}
                  onChange={(e) => setForm({ ...form, nome: e.target.value })}
                  placeholder="Ex.: Bebidas"
                  required
                />
              </Field>
              <Field id="category-description" label="Descrição">
                <textarea
                  id="category-description"
                  value={form.descricao}
                  onChange={(e) => setForm({ ...form, descricao: e.target.value })}
                  placeholder="Que produtos pertencem a esta categoria?"
                />
              </Field>
              <fieldset className="form-group">
                <legend className="field-label">Símbolo da categoria</legend>
                <div className="icon-picker">
                  {categoryIcons.map(({ key, label }) => (
                    <button
                      key={key}
                      type="button"
                      title={label}
                      aria-label={label}
                      aria-pressed={categoryIconKey(form.icone) === key}
                      onClick={() => setForm({ ...form, icone: key })}
                    >
                      <CategoryIcon value={key} size={20} />
                    </button>
                  ))}
                </div>
              </fieldset>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>
                  Cancelar
                </button>
                <button className="btn-primary" type="submit">
                  {save.pending && <Spinner size="small" />}
                  {save.pending
                    ? ' A guardar…'
                    : editing
                      ? 'Guardar alterações'
                      : 'Criar categoria'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}

      {confirmDelete && (
        <ConfirmDialog
          title="Remover categoria?"
          onClose={() => setConfirmDelete(null)}
          onConfirm={handleDelete}
          busy={remove.pending}
          error={remove.error}
          danger
          label="Remover categoria"
        >
          <p>
            Está a remover <strong>{confirmDelete.cat.nome}</strong>. Esta ação não pode ser
            desfeita.
          </p>
          {confirmDelete.produtos.length > 0 ? (
            <>
              <Notice>
                Esta categoria contém {confirmDelete.produtos.length} produto(s). A remoção inclui
                esses produtos e as vendas associadas.
              </Notice>
              <ul className="confirm-list">
                {confirmDelete.produtos.map((item) => (
                  <li key={item.id}>
                    {item.nome} — {formatMoney(item.preco)}; stock:{' '}
                    {formatQuantity(item.stock, item.unidade || '')}
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p>Esta categoria não tem produtos associados.</p>
          )}
        </ConfirmDialog>
      )}
    </div>
  );
}
