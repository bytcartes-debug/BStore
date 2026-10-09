import { useToast } from '../utils/toast';
import { useState } from 'react';
import { apiFetch, apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { abrirScanner } from '../utils/scanner';
import { buscarNaOpenFoodFacts } from '../utils/openFoodFacts';
import { decimal, formatMoney, formatQuantity, parseDecimalInput } from '../utils/decimal';
import {
  AlertTriangle,
  ArrowDownLeft,
  ChevronDown,
  ChevronUp,
  ClipboardList,
  History,
  Package,
  Pencil,
  Plus,
  RefreshCw,
  ScanLine,
  Search,
  SlidersHorizontal,
  Trash2,
  X,
} from 'lucide-react';
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
  custo?: string;
  margem?: string;
  stock: string;
  stockMinimo: string;
  unidade: string;
  categoriaId: number;
  categoriaNome?: string;
  codigoBarras?: string;
  ativo?: boolean;
}

interface MovimentoStock {
  id: number;
  produtoId: number;
  produtoNome: string;
  tipo: string;
  quantidade: string;
  custoUnitario: string;
  motivo?: string;
  referenciaTipo?: string;
  referenciaId?: number;
  criadoEm: string;
}

interface ItemEntrada {
  produtoId: number;
  produtoNome: string;
  quantidade: string;
  custoUnitario: string;
  motivo: string;
}

const FORM_VAZIO = {
  nome: '',
  preco: '',
  custo: '',
  stock: '',
  stockMinimo: '5',
  unidade: 'un',
  categoriaId: '',
  codigoBarras: '',
};

const loadProductsData = async (signal: AbortSignal) => {
  const [produtos, categorias] = await Promise.all([
    apiRequest<Produto[]>('/api/produtos?incluirArquivados=true', { signal }),
    apiRequest<Categoria[]>('/api/categorias', { signal }),
  ]);
  return { produtos, categorias };
};

export default function ProdutosPage() {
  const { data, loading, error, reload } = useResource(loadProductsData);
  const produtos = data?.produtos || [];
  const categorias = data?.categorias || [];

  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [stockFilter, setStockFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('active'); // active | archived | all
  const [sort, setSort] = useState('name');

  // Modais
  const [showModal, setShowModal] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [editing, setEditing] = useState<Produto | null>(null);
  const [form, setForm] = useState(FORM_VAZIO);
  const [offMsg, setOffMsg] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<Produto | null>(null);

  // Entrada de stock (compra)
  const [showEntradaModal, setShowEntradaModal] = useState(false);
  const [entradaItens, setEntradaItens] = useState<ItemEntrada[]>([]);
  const [entradaProdutoId, setEntradaProdutoId] = useState('');
  const [entradaQtd, setEntradaQtd] = useState('');
  const [entradaCusto, setEntradaCusto] = useState('');
  const [entradaMotivo, setEntradaMotivo] = useState('Compra de stock');
  const [entradaErro, setEntradaErro] = useState<string | null>(null);

  // Ajuste / Perda
  const [showAjusteModal, setShowAjusteModal] = useState(false);
  const [ajusteProdutoId, setAjusteProdutoId] = useState('');
  const [ajusteModo, setAjusteModo] = useState<'novaQtd' | 'diferenca'>('novaQtd');
  const [ajusteValor, setAjusteValor] = useState('');
  const [ajusteTipo, setAjusteTipo] = useState<'AJUSTE' | 'PERDA'>('AJUSTE');
  const [ajusteMotivo, setAjusteMotivo] = useState('');

  // Contagem de inventario
  const [showInventarioModal, setShowInventarioModal] = useState(false);
  const [contagens, setContagens] = useState<Record<number, string>>({});

  // Historico de movimentos
  const [movimentoProduto, setMovimentoProduto] = useState<Produto | null>(null);
  const [movimentos, setMovimentos] = useState<MovimentoStock[]>([]);
  const [carregandoMovimentos, setCarregandoMovimentos] = useState(false);

  const save = useMutation();
  const scan = useMutation();
  const remove = useMutation();
  const stockAction = useMutation();
  const toast = useToast();

  const filtered = produtos
    .filter((p) => {
      const matchSearch =
        p.nome.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()) ||
        (p.codigoBarras || '').includes(search.trim());
      const matchCategory = !category || p.categoriaId.toString() === category;
      const matchStock = stockFilter === 'all' || decimal(p.stock).lte(p.stockMinimo);
      const matchStatus =
        statusFilter === 'all'
          ? true
          : statusFilter === 'archived'
            ? p.ativo === false
            : p.ativo !== false;
      return matchSearch && matchCategory && matchStock && matchStatus;
    })
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
    setShowDetails(false);
    setForm({ ...FORM_VAZIO, categoriaId: categorias[0]?.id.toString() || '' });
    save.setError(null);
    scan.setError(null);
    setOffMsg(null);
    setShowModal(true);
  };

  const openEdit = (p: Produto) => {
    setEditing(p);
    setShowDetails(false);
    setForm({
      nome: p.nome,
      preco: p.preco,
      custo: p.custo || '0.00',
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

  const openEntradaPara = (p?: Produto) => {
    setEntradaItens([]);
    setEntradaProdutoId(p ? p.id.toString() : produtos[0]?.id.toString() || '');
    setEntradaQtd('1');
    setEntradaCusto(p?.custo || '0.00');
    setEntradaMotivo('Compra de stock');
    setEntradaErro(null);
    setShowEntradaModal(true);
  };

  const openAjustePara = (p?: Produto) => {
    setAjusteProdutoId(p ? p.id.toString() : produtos[0]?.id.toString() || '');
    setAjusteModo('novaQtd');
    setAjusteValor(p ? p.stock : '0');
    setAjusteTipo('AJUSTE');
    setAjusteMotivo('');
    stockAction.setError(null);
    setShowAjusteModal(true);
  };

  const openInventario = () => {
    const iniciais: Record<number, string> = {};
    produtos.filter((p) => p.ativo !== false).forEach((p) => {
      iniciais[p.id] = p.stock;
    });
    setContagens(iniciais);
    stockAction.setError(null);
    setShowInventarioModal(true);
  };

  const openHistorico = async (p: Produto) => {
    setMovimentoProduto(p);
    setCarregandoMovimentos(true);
    try {
      const res = await apiRequest<{ items: MovimentoStock[] }>(
        `/api/produtos/${p.id}/movimentos?pageSize=50`,
      );
      setMovimentos(res.items || []);
    } catch {
      setMovimentos([]);
    } finally {
      setCarregandoMovimentos(false);
    }
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
      let catId = form.categoriaId ? Number(form.categoriaId) : 0;
      if (!catId || isNaN(catId)) {
        let catGeral = categorias.find((c) => c.nome.toLowerCase() === 'geral');
        if (!catGeral) {
          catGeral = await apiRequest<Categoria>('/api/categorias', {
            method: 'POST',
            body: JSON.stringify({ nome: 'Geral', descricao: 'Categoria geral' }),
          });
        }
        catId = catGeral.id;
      }

      if (editing) {
        const body: Record<string, unknown> = {
          nome: form.nome.trim(),
          preco: form.preco,
          custo: form.custo || '0.00',
          stockMinimo: form.stockMinimo || '0',
          unidade: form.unidade || 'un',
          categoriaId: catId,
          ...(form.codigoBarras.trim() ? { codigoBarras: form.codigoBarras.trim() } : {}),
        };
        await apiRequest(`/api/produtos/${editing.id}`, {
          method: 'PUT',
          body: JSON.stringify(body),
        });
      } else {
        const body: Record<string, unknown> = {
          nome: form.nome.trim(),
          preco: form.preco,
          custo: form.custo || '0.00',
          stock: form.stock || '0',
          stockMinimo: form.stockMinimo || '0',
          unidade: form.unidade || 'un',
          categoriaId: catId,
          ...(form.codigoBarras.trim() ? { codigoBarras: form.codigoBarras.trim() } : {}),
        };
        await apiRequest('/api/produtos', {
          method: 'POST',
          body: JSON.stringify(body),
        });
      }
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
      toast('Produto arquivado ou removido.');
      void reload();
    });

  const adicionarLinhaEntrada = () => {
    setEntradaErro(null);
    const pid = Number(entradaProdutoId);
    const prod = produtos.find((p) => p.id === pid);
    if (!prod) {
      setEntradaErro('Selecione um produto.');
      return;
    }
    const q = parseDecimalInput(entradaQtd);
    if (!q || q.lte(0)) {
      setEntradaErro('A quantidade deve ser positiva.');
      return;
    }
    const c = parseDecimalInput(entradaCusto || '0');
    if (!c || c.lt(0)) {
      setEntradaErro('O custo unitário deve ser zero ou positivo.');
      return;
    }

    setEntradaItens((prev) => [
      ...prev,
      {
        produtoId: prod.id,
        produtoNome: prod.nome,
        quantidade: q.toFixed(3),
        custoUnitario: c.toFixed(2),
        motivo: entradaMotivo.trim() || 'Compra de stock',
      },
    ]);
    setEntradaQtd('1');
    setEntradaErro(null);
  };

  const handleConfirmarEntrada = () => {
    if (entradaItens.length === 0) {
      setEntradaErro('Adicione pelo menos um item à lista de entrada.');
      return;
    }
    void stockAction.run(async () => {
      await apiRequest('/api/stock/entradas', {
        method: 'POST',
        body: JSON.stringify({ itens: entradaItens, motivo: 'Compra de stock' }),
      });
      setShowEntradaModal(false);
      toast('Entrada de stock registada com sucesso.');
      void reload();
    });
  };

  const handleConfirmarAjuste = (e: React.FormEvent) => {
    e.preventDefault();
    if (!ajusteMotivo.trim()) {
      stockAction.setError('O motivo do ajuste é obrigatório.');
      return;
    }
    const pid = Number(ajusteProdutoId);
    if (!pid) {
      stockAction.setError('Selecione um produto.');
      return;
    }
    const val = parseDecimalInput(ajusteValor);
    if (!val) {
      stockAction.setError('Introduza um valor válido.');
      return;
    }

    void stockAction.run(async () => {
      const body: Record<string, unknown> = {
        produtoId: pid,
        tipo: ajusteTipo,
        motivo: ajusteMotivo.trim(),
      };
      if (ajusteModo === 'novaQtd') {
        body.novaQuantidade = val.toFixed(3);
      } else {
        body.diferenca = val.toFixed(3);
      }
      await apiRequest('/api/stock/ajustes', {
        method: 'POST',
        body: JSON.stringify(body),
      });
      setShowAjusteModal(false);
      toast('Ajuste de stock registado.');
      void reload();
    });
  };

  const handleConfirmarInventario = () => {
    const itens = Object.entries(contagens).map(([idStr, qtdStr]) => ({
      produtoId: Number(idStr),
      quantidadeContada: qtdStr || '0',
    }));
    void stockAction.run(async () => {
      await apiRequest('/api/stock/contagem', {
        method: 'POST',
        body: JSON.stringify({ itens }),
      });
      setShowInventarioModal(false);
      toast('Contagem de inventário confirmada.');
      void reload();
    });
  };

  const clearFilters = () => {
    setSearch('');
    setCategory('');
    setStockFilter('all');
    setStatusFilter('active');
  };

  return (
    <div>
      <PageHeading
        title="Produtos"
        description="Preços, custos, quantidades e histórico de movimentos."
      >
        <div className="btn-group">
          <button className="btn-primary" onClick={openNew} disabled={!data}>
            <Plus size={16} strokeWidth={2.2} aria-hidden="true" /> Adicionar produto
          </button>
          <button
            className="btn-secondary"
            onClick={() => openEntradaPara()}
            disabled={!data || produtos.length === 0}
          >
            <ArrowDownLeft size={16} strokeWidth={2} aria-hidden="true" /> Entrada
          </button>
          <button
            className="btn-secondary"
            onClick={() => openAjustePara()}
            disabled={!data || produtos.length === 0}
          >
            <SlidersHorizontal size={16} strokeWidth={2} aria-hidden="true" /> Ajuste
          </button>
          <button
            className="btn-secondary"
            onClick={openInventario}
            disabled={!data || produtos.length === 0}
          >
            <ClipboardList size={16} strokeWidth={2} aria-hidden="true" /> Inventário
          </button>
        </div>
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
        <Field id="product-status-filter" label="Estado">
          <select
            id="product-status-filter"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="active">Apenas ativos</option>
            <option value="archived">Apenas arquivados</option>
            <option value="all">Todos (ativos e arquivados)</option>
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
                icon={<Package size={36} strokeWidth={1.8} aria-hidden="true" />}
              >
                <button className="btn-secondary" onClick={openNew}>
                  <Plus size={16} strokeWidth={2} aria-hidden="true" /> Adicionar primeiro produto
                </button>
              </EmptyState>
            ) : filtered.length === 0 ? (
              <EmptyState
                title="Nenhum produto corresponde à pesquisa"
                description="Altere o nome, o código ou os filtros para encontrar o que procura."
                icon={<Search size={36} strokeWidth={1.8} aria-hidden="true" />}
              >
                <button className="btn-secondary" onClick={clearFilters}>
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
                                onClick={() => openEdit(p)}
                                aria-label={`Editar ${p.nome}`}
                                title="Editar produto"
                              >
                                <Pencil size={15} strokeWidth={2} aria-hidden="true" />
                              </button>
                              <button
                                className="icon-btn"
                                onClick={() => void openHistorico(p)}
                                aria-label={`Histórico de ${p.nome}`}
                                title="Histórico de movimentos"
                              >
                                <History size={15} strokeWidth={2} aria-hidden="true" />
                              </button>
                              <button
                                className="icon-btn delete"
                                onClick={() => {
                                  remove.setError(null);
                                  setDeleting(p);
                                }}
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
        )
      )}

      {/* Modal Adicionar / Editar Produto */}
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

            <fieldset disabled={save.pending || scan.pending}>
              <Field id="product-name" label="Nome *">
                <input
                  id="product-name"
                  value={form.nome}
                  onChange={(e) => setF({ nome: e.target.value })}
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
                  onChange={(e) => setF({ preco: e.target.value })}
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
                        setShowModal(false);
                        openEntradaPara(editing);
                      }}
                    >
                      <ArrowDownLeft size={15} strokeWidth={2} aria-hidden="true" /> Entrada
                    </button>
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => {
                        setShowModal(false);
                        openAjustePara(editing);
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
                  onClick={() => setShowDetails(!showDetails)}
                  style={{ width: '100%', textAlign: 'left', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
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
                        onChange={(e) => setF({ custo: e.target.value })}
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
                          onChange={(e) => setF({ stock: e.target.value })}
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

                  <Field id="product-category" label="Categoria">
                    <select
                      id="product-category"
                      value={form.categoriaId}
                      onChange={(e) => setF({ categoriaId: e.target.value })}
                    >
                      <option value="">Geral (automática se não escolhida)</option>
                      {categorias.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.nome}
                        </option>
                      ))}
                    </select>
                  </Field>

                  <Field id="product-barcode" label="Código de barras">
                    <div className="input-action">
                      <input
                        id="product-barcode"
                        value={form.codigoBarras}
                        onChange={(e) => setF({ codigoBarras: e.target.value })}
                        placeholder="Opcional"
                      />
                      <button type="button" className="btn-secondary" onClick={handleScan}>
                        {scan.pending ? <Spinner size="small" /> : <ScanLine size={16} strokeWidth={2} aria-hidden="true" />} Ler código
                      </button>
                    </div>
                  </Field>
                </div>
              )}

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary">
                  {save.pending && <Spinner />}
                  {save.pending ? 'A guardar…' : editing ? 'Guardar alterações' : 'Criar produto'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}

      {/* Modal Entrada de Stock (Compra) */}
      {showEntradaModal && (
        <Modal
          title="Entrada de stock (Compra)"
          onClose={() => setShowEntradaModal(false)}
          busy={stockAction.pending}
        >
          <div>
            <p className="required-note">Registe a compra de produtos com atualização do custo médio ponderado.</p>
            {entradaErro && <Notice>{entradaErro}</Notice>}
            {stockAction.error && <Notice>{stockAction.error}</Notice>}

            <div style={{ background: 'var(--bg-subtle)', padding: 12, borderRadius: 8, marginBottom: 16 }}>
              <div className="form-grid">
                <Field id="entrada-prod" label="Produto *">
                  <select
                    id="entrada-prod"
                    value={entradaProdutoId}
                    onChange={(e) => {
                      setEntradaProdutoId(e.target.value);
                      const sel = produtos.find((p) => p.id === Number(e.target.value));
                      if (sel && sel.custo) setEntradaCusto(sel.custo);
                    }}
                  >
                    {produtos
                      .filter((p) => p.ativo !== false)
                      .map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.nome} (Atual: {p.stock})
                        </option>
                      ))}
                  </select>
                </Field>
                <Field id="entrada-qtd" label="Quantidade a entrar *">
                  <input
                    id="entrada-qtd"
                    type="number"
                    min="0.001"
                    step="0.001"
                    value={entradaQtd}
                    onChange={(e) => setEntradaQtd(e.target.value)}
                    placeholder="1"
                  />
                </Field>
                <Field id="entrada-custo" label="Custo unitário (MT) *">
                  <input
                    id="entrada-custo"
                    type="number"
                    min="0"
                    step="0.01"
                    value={entradaCusto}
                    onChange={(e) => setEntradaCusto(e.target.value)}
                    placeholder="0,00"
                  />
                </Field>
              </div>
              <div style={{ marginTop: 8, display: 'flex', justifyContent: 'flex-end' }}>
                <button type="button" className="btn-secondary" onClick={adicionarLinhaEntrada}>
                  <Plus size={15} strokeWidth={2} aria-hidden="true" /> Adicionar à lista
                </button>
              </div>
            </div>

            {entradaItens.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <h4>Itens a registar ({entradaItens.length}):</h4>
                <table className="responsive-table" style={{ marginTop: 8 }}>
                  <thead>
                    <tr>
                      <th>Produto</th>
                      <th className="numeric">Quantidade</th>
                      <th className="numeric">Custo unit.</th>
                      <th className="numeric">Subtotal</th>
                      <th>Remover</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entradaItens.map((item, idx) => (
                      <tr key={idx}>
                        <td>{item.produtoNome}</td>
                        <td className="numeric">{item.quantidade}</td>
                        <td className="numeric">{formatMoney(item.custoUnitario)}</td>
                        <td className="numeric">
                          {formatMoney(
                            decimal(item.quantidade).times(decimal(item.custoUnitario)).toFixed(2),
                          )}
                        </td>
                        <td>
                          <button
                            type="button"
                            className="icon-btn delete"
                            aria-label={`Remover ${item.produtoNome} da lista`}
                            onClick={() => setEntradaItens((prev) => prev.filter((_, i) => i !== idx))}
                          >
                            <X size={15} strokeWidth={2} aria-hidden="true" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="modal-actions">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowEntradaModal(false)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={handleConfirmarEntrada}
                disabled={stockAction.pending || entradaItens.length === 0}
              >
                {stockAction.pending ? 'A guardar…' : 'Confirmar entrada'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal Ajuste / Perda de Stock */}
      {showAjusteModal && (
        <Modal
          title="Ajuste / Perda de stock"
          onClose={() => setShowAjusteModal(false)}
          busy={stockAction.pending}
        >
          <form onSubmit={handleConfirmarAjuste}>
            <p className="required-note">O motivo do ajuste é obrigatório.</p>
            {stockAction.error && <Notice>{stockAction.error}</Notice>}

            <Field id="ajuste-prod" label="Produto *">
              <select
                id="ajuste-prod"
                value={ajusteProdutoId}
                onChange={(e) => setAjusteProdutoId(e.target.value)}
              >
                {produtos.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nome} (Stock atual: {p.stock})
                  </option>
                ))}
              </select>
            </Field>

            <div className="form-grid">
              <Field id="ajuste-modo" label="Modo de alteração">
                <select
                  id="ajuste-modo"
                  value={ajusteModo}
                  onChange={(e) => setAjusteModo(e.target.value as 'novaQtd' | 'diferenca')}
                >
                  <option value="novaQtd">Definir nova quantidade</option>
                  <option value="diferenca">Informar diferença (+ ou -)</option>
                </select>
              </Field>

              <Field
                id="ajuste-valor"
                label={ajusteModo === 'novaQtd' ? 'Nova quantidade *' : 'Diferença *'}
              >
                <input
                  id="ajuste-valor"
                  type="number"
                  step="0.001"
                  value={ajusteValor}
                  onChange={(e) => setAjusteValor(e.target.value)}
                  placeholder="0"
                  required
                />
              </Field>

              <Field id="ajuste-tipo" label="Tipo de movimento">
                <select
                  id="ajuste-tipo"
                  value={ajusteTipo}
                  onChange={(e) => setAjusteTipo(e.target.value as 'AJUSTE' | 'PERDA')}
                >
                  <option value="AJUSTE">Ajuste de stock</option>
                  <option value="PERDA">Perda / Quebra / Validade</option>
                </select>
              </Field>
            </div>

            <Field id="ajuste-motivo" label="Motivo do ajuste *">
              <input
                id="ajuste-motivo"
                value={ajusteMotivo}
                onChange={(e) => setAjusteMotivo(e.target.value)}
                placeholder="Ex.: Danificado no transporte, recontagem, validade"
                required
              />
            </Field>

            <div className="modal-actions">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowAjusteModal(false)}
              >
                Cancelar
              </button>
              <button type="submit" className="btn-primary" disabled={stockAction.pending}>
                {stockAction.pending ? 'A guardar…' : 'Confirmar ajuste'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* Modal Contagem de Inventario */}
      {showInventarioModal && (
        <Modal
          title="Contagem de inventário"
          onClose={() => setShowInventarioModal(false)}
          busy={stockAction.pending}
        >
          <div>
            <p className="required-note">
              Introduza a quantidade contada em prateleira para cada produto.
            </p>
            {stockAction.error && <Notice>{stockAction.error}</Notice>}

            <div style={{ maxHeight: 350, overflowY: 'auto', marginBottom: 16 }}>
              <table className="responsive-table">
                <thead>
                  <tr>
                    <th>Produto</th>
                    <th className="numeric">Stock atual</th>
                    <th className="numeric" style={{ width: 140 }}>
                      Qtd. contada
                    </th>
                    <th className="numeric">Diferença</th>
                  </tr>
                </thead>
                <tbody>
                  {produtos
                    .filter((p) => p.ativo !== false)
                    .map((p) => {
                      const contada = contagens[p.id] ?? p.stock;
                      const dif = decimal(contada || '0').minus(decimal(p.stock));
                      return (
                        <tr key={p.id}>
                          <td>{p.nome}</td>
                          <td className="numeric">{p.stock}</td>
                          <td className="numeric">
                            <input
                              type="number"
                              step="0.001"
                              min="0"
                              value={contada}
                              onChange={(e) =>
                                setContagens((prev) => ({ ...prev, [p.id]: e.target.value }))
                              }
                              style={{ width: '100%', textAlign: 'right' }}
                            />
                          </td>
                          <td
                            className="numeric"
                            style={{
                              color: dif.isNegative()
                                ? 'var(--color-danger)'
                                : dif.isPositive()
                                  ? 'var(--color-brand)'
                                  : 'inherit',
                              fontWeight: dif.isZero() ? 400 : 600,
                            }}
                          >
                            {dif.isPositive() ? `+${dif.toFixed(3)}` : dif.toFixed(3)}
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>

            <div className="modal-actions">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowInventarioModal(false)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={handleConfirmarInventario}
                disabled={stockAction.pending}
              >
                {stockAction.pending ? 'A guardar…' : 'Confirmar inventário'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal Historico de Movimentos */}
      {movimentoProduto && (
        <Modal
          title={`Movimentos — ${movimentoProduto.nome}`}
          onClose={() => setMovimentoProduto(null)}
        >
          <div>
            <div style={{ marginBottom: 12, display: 'flex', gap: 16, fontSize: '0.9rem' }}>
              <span>
                Stock: <strong>{formatQuantity(movimentoProduto.stock, movimentoProduto.unidade)}</strong>
              </span>
              <span>
                Custo: <strong>{formatMoney(movimentoProduto.custo || '0.00')}</strong>
              </span>
              <span>
                Preço: <strong>{formatMoney(movimentoProduto.preco)}</strong>
              </span>
            </div>

            {carregandoMovimentos ? (
              <Loading />
            ) : movimentos.length === 0 ? (
              <p>Nenhum movimento registado para este produto.</p>
            ) : (
              <div style={{ maxHeight: 350, overflowY: 'auto' }}>
                <table className="responsive-table">
                  <thead>
                    <tr>
                      <th>Data</th>
                      <th>Tipo</th>
                      <th className="numeric">Qtd.</th>
                      <th className="numeric">Custo unit.</th>
                      <th>Motivo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {movimentos.map((m) => (
                      <tr key={m.id}>
                        <td>{m.criadoEm ? new Date(m.criadoEm).toLocaleDateString('pt-MZ') : '-'}</td>
                        <td>
                          <span
                            className={
                              m.tipo === 'ENTRADA' || m.tipo === 'STOCK_INICIAL'
                                ? 'badge badge-success'
                                : m.tipo === 'VENDA' || m.tipo === 'PERDA'
                                  ? 'badge badge-warning'
                                  : 'badge'
                            }
                          >
                            {m.tipo}
                          </span>
                        </td>
                        <td
                          className="numeric"
                          style={{
                            fontWeight: 600,
                            color: decimal(m.quantidade).isPositive()
                              ? 'var(--color-brand)'
                              : 'var(--color-danger)',
                          }}
                        >
                          {decimal(m.quantidade).isPositive()
                            ? `+${m.quantidade}`
                            : m.quantidade}
                        </td>
                        <td className="numeric">{formatMoney(m.custoUnitario)}</td>
                        <td>{m.motivo || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="modal-actions" style={{ marginTop: 16 }}>
              <button
                type="button"
                className="btn-primary"
                onClick={() => setMovimentoProduto(null)}
              >
                Fechar
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Confirmar Exclusao / Arquivamento */}
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
            Quer remover <strong>{deleting.nome}</strong>? Se o produto tiver vendas ou movimentos associados, será arquivado para preservar o histórico.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}
