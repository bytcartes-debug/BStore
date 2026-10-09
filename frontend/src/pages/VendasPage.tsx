import { useToast } from '../utils/toast';
import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { apiFetch, apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { abrirScanner } from '../utils/scanner';
import { notificarVendaRegistada } from '../utils/notificacoes';
import { decimal, formatMoney, formatQuantity, parseDecimalInput } from '../utils/decimal';
import {
  gerarTextoRecibo,
  imprimirReciboTexto,
  partilharReciboWhatsApp,
  formatarMetodoPagamento,
  type ReciboDados,
} from '../utils/recibo';
import { FechoCaixaModal } from '../components/FechoCaixaModal';
import type Decimal from 'decimal.js';
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
} from '../components/UI';
import './VendasPage.css';

interface Produto {
  id: number;
  nome: string;
  preco: string;
  stock: string;
  unidade: string;
  codigoBarras?: string;
  custo?: string;
}

interface Categoria {
  id: number;
  nome: string;
}

interface Devedor {
  id: number;
  nome: string;
  saldo?: string;
}

interface ItemVendaDetalhe {
  id: number;
  produto: string;
  quantidade: string;
  precoUnitario: string;
  total: string;
}

interface PagamentoVendaDetalhe {
  id: number;
  metodo: string;
  valor: string;
  troco: string;
}

interface VendaDocumento {
  id: number;
  numero: number;
  total: string;
  totalCusto?: string;
  lucro?: string;
  estado: string;
  data: string;
  hora: string;
  criadaEm: string;
  clienteId?: number;
  observacao?: string;
  anuladaEm?: string;
  motivoAnulacao?: string;
  itens?: ItemVendaDetalhe[];
  pagamentos?: PagamentoVendaDetalhe[];
  produto?: string;
  quantidade?: string;
  troco?: string;
}

interface ItemCarrinho {
  produto: Produto;
  quantidade: Decimal;
}

interface LinhaPagamento {
  id: string;
  metodo: string;
  valor: string;
}

const loadSalesData = async (signal: AbortSignal) => {
  const [vendas, produtos, categorias, devedores] = await Promise.all([
    apiRequest<VendaDocumento[]>('/api/vendas', { signal }),
    apiRequest<Produto[]>('/api/produtos', { signal }),
    apiRequest<Categoria[]>('/api/categorias', { signal }).catch(() => [] as Categoria[]),
    apiRequest<Devedor[]>('/api/devedores', { signal }).catch(() => [] as Devedor[]),
  ]);
  return { vendas, produtos, categorias, devedores };
};

export default function VendasPage() {
  const { data, loading, error, reload } = useResource(loadSalesData);
  const [search, setSearch] = useState('');
  const [filterEstado, setFilterEstado] = useState('todos');
  const [filterMetodo, setFilterMetodo] = useState('todos');

  // Modais principais
  const [showModal, setShowModal] = useState(false);
  const [showFechoModal, setShowFechoModal] = useState(false);
  const [vendaDetalhes, setVendaDetalhes] = useState<VendaDocumento | null>(null);
  const [vendaParaAnular, setVendaParaAnular] = useState<VendaDocumento | null>(null);
  const [motivoAnulacao, setMotivoAnulacao] = useState('');
  const [reciboSucesso, setReciboSucesso] = useState<ReciboDados | null>(null);

  // Scanner e criação rápida
  const [continuousScan, setContinuousScan] = useState(false);
  const [quickCreateBarcode, setQuickCreateBarcode] = useState<string | null>(null);
  const [quickNome, setQuickNome] = useState('');
  const [quickPreco, setQuickPreco] = useState('');
  const [quickCusto, setQuickCusto] = useState('0.00');
  const [quickStock, setQuickStock] = useState('10.000');
  const [quickCategoriaId, setQuickCategoriaId] = useState<number | string>('');

  // Carrinho
  const [carrinho, setCarrinho] = useState<ItemCarrinho[]>([]);
  const [busca, setBusca] = useState('');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [activeOption, setActiveOption] = useState(-1);
  const [selectedProd, setSelectedProd] = useState<Produto | null>(null);
  const [quantity, setQuantity] = useState('1');
  const [cartError, setCartError] = useState<string | null>(null);
  const [scanMessage, setScanMessage] = useState<string | null>(null);

  // Pagamentos
  const [pagamentos, setPagamentos] = useState<LinhaPagamento[]>([
    { id: '1', metodo: 'DINHEIRO', valor: '' },
  ]);
  const [clienteId, setClienteId] = useState<string>('');
  const [novoClienteNome, setNovoClienteNome] = useState('');
  const [criandoNovoCliente, setCriandoNovoCliente] = useState(false);
  const [observacao, setObservacao] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => crypto.randomUUID());

  const inputRef = useRef<HTMLInputElement>(null);
  const suggestionsRef = useRef<HTMLUListElement>(null);

  const save = useMutation();
  const scan = useMutation();
  const anularMutation = useMutation();
  const toast = useToast();

  const vendas = useMemo(() => data?.vendas || [], [data?.vendas]);
  const produtos = useMemo(() => data?.produtos || [], [data?.produtos]);
  const categorias = useMemo(() => data?.categorias || [], [data?.categorias]);
  const devedores = useMemo(() => data?.devedores || [], [data?.devedores]);

  const filteredSales = vendas.filter((v) => {
    const termo = search.trim().toLocaleLowerCase();
    const matchSearch = !termo ||
      `${v.numero} ${v.produto || ''} ${v.data} ${v.observacao || ''}`
        .toLocaleLowerCase()
        .includes(termo);
    const matchEstado = filterEstado === 'todos' || v.estado === filterEstado;
    const matchMetodo = filterMetodo === 'todos' || (
      v.pagamentos && v.pagamentos.some((p) => p.metodo.toUpperCase() === filterMetodo.toUpperCase())
    );
    return matchSearch && matchEstado && matchMetodo;
  });

  const suggestions = produtos
    .filter((p) => p.nome.toLocaleLowerCase().includes(busca.trim().toLocaleLowerCase()))
    .slice(0, 30);

  const total = carrinho.reduce(
    (sum, item) => sum.plus(decimal(item.produto.preco).times(item.quantidade)),
    decimal(0),
  );

  const totalPago = pagamentos.reduce((sum, p) => {
    const val = parseDecimalInput(p.valor) || (pagamentos.length === 1 && !p.valor ? total : null);
    return val && val.gt(0) ? sum.plus(val) : sum;
  }, decimal(0));

  const temDinheiro = pagamentos.some((p) => p.metodo === 'DINHEIRO');
  const temFiado = pagamentos.some((p) => p.metodo === 'FIADO');
  const trocoCalculado = temDinheiro && totalPago.gt(total) ? totalPago.minus(total) : decimal(0);
  const faltaPagar = total.gt(totalPago) ? total.minus(totalPago) : decimal(0);

  useEffect(() => {
    suggestionsRef.current?.children[activeOption]?.scrollIntoView({ block: 'nearest' });
  }, [activeOption]);

  const selectProduct = (product: Produto) => {
    setSelectedProd(product);
    setBusca(product.nome);
    setQuantity('1');
    setShowSuggestions(false);
    setActiveOption(-1);
    setCartError(null);
  };

  const addProduct = useCallback((product: Produto, qty: Decimal) => {
    const existing = carrinho.find((item) => item.produto.id === product.id);
    if (qty.lte(0) || qty.decimalPlaces() > 3) {
      setCartError('Introduza uma quantidade positiva com até três casas decimais.');
      return false;
    }
    if ((existing?.quantidade || decimal(0)).plus(qty).gt(product.stock)) {
      setCartError(
        `Stock insuficiente de ${product.nome}. Disponível: ${formatQuantity(product.stock, product.unidade)}.`,
      );
      return false;
    }
    setCarrinho((previous) => {
      const exists = previous.find((item) => item.produto.id === product.id);
      if (exists) {
        return previous.map((item) =>
          item.produto.id === product.id
            ? { ...item, quantidade: item.quantidade.plus(qty) }
            : item,
        );
      }
      return [...previous, { produto: product, quantidade: qty }];
    });
    setCartError(null);
    return true;
  }, [carrinho]);

  const addSelected = () => {
    if (!selectedProd) {
      setCartError('Selecione um produto da lista.');
      return;
    }
    const qty = parseDecimalInput(quantity);
    if (!qty) {
      setCartError('Introduza uma quantidade válida.');
      return;
    }
    if (!addProduct(selectedProd, qty)) return;
    setBusca('');
    setSelectedProd(null);
    setQuantity('1');
    inputRef.current?.focus();
  };

  const changeQuantity = (id: number, qty: Decimal) => {
    const item = carrinho.find((entry) => entry.produto.id === id);
    if (item && qty.gt(item.produto.stock)) {
      setCartError(`Stock insuficiente de ${item.produto.nome}.`);
      return;
    }
    setCartError(null);
    setCarrinho((previous) =>
      qty.lte(0)
        ? previous.filter((entry) => entry.produto.id !== id)
        : previous.map((entry) =>
            entry.produto.id === id ? { ...entry, quantidade: qty } : entry,
          ),
    );
  };

  const resetCart = () => {
    setCarrinho([]);
    setBusca('');
    setSelectedProd(null);
    setQuantity('1');
    setPagamentos([{ id: '1', metodo: 'DINHEIRO', valor: '' }]);
    setClienteId('');
    setObservacao('');
    setIdempotencyKey(crypto.randomUUID());
    setCartError(null);
    setScanMessage(null);
    setShowSuggestions(false);
    save.setError(null);
    scan.setError(null);
  };

  const closeModal = () => {
    if (
      carrinho.length > 0 &&
      !window.confirm('Descartar esta venda? Os produtos do carrinho não serão registados.')
    )
      return;
    setShowModal(false);
    resetCart();
  };

  const handleScan = useCallback(async () => {
    setScanMessage(null);
    setCartError(null);
    try {
      const code = await abrirScanner();
      if (!code) return;

      const response = await apiFetch(`/api/produtos/barcode/${encodeURIComponent(code)}`);
      if (response.status === 404) {
        // Passo 2.3: Pergunta se deseja cadastrar agora e abre modal de cadastro rápido
        setQuickCreateBarcode(code);
        setQuickNome('');
        setQuickPreco('');
        setQuickCusto('0.00');
        setQuickStock('10.000');
        setQuickCategoriaId(categorias[0]?.id || '');
        return;
      }
      if (!response.ok) {
        throw new Error('Não foi possível consultar este código. Tente novamente.');
      }
      const product: Produto = await response.json();
      const existing = carrinho.some((item) => item.produto.id === product.id);
      const step = decimal(
        existing && ['kg', 'g', 'L', 'ml'].includes(product.unidade) ? '0.5' : '1',
      );
      if (addProduct(product, step)) {
        setScanMessage(`✓ ${product.nome} adicionado ao carrinho.`);
        // Scanner contínuo: reabre se ativado
        if (continuousScan) {
          setTimeout(() => void handleScan(), 300);
        }
      }
    } catch (e: unknown) {
      setCartError(e instanceof Error ? e.message : 'Falha na leitura do código.');
    }
  }, [addProduct, carrinho, categorias, continuousScan]);

  const handleSalvarProdutoRapido = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!quickNome.trim() || !quickPreco || !quickCreateBarcode) return;
    try {
      const novoProduto = await apiRequest<Produto>('/api/produtos', {
        method: 'POST',
        body: JSON.stringify({
          nome: quickNome.trim(),
          preco: quickPreco,
          custo: quickCusto || '0.00',
          stock: quickStock || '10.000',
          unidade: 'un',
          stockMinimo: '5.000',
          categoriaId: quickCategoriaId || categorias[0]?.id,
          codigoBarras: quickCreateBarcode,
        }),
      });
      setQuickCreateBarcode(null);
      toast(`Produto ${novoProduto.nome} cadastrado!`);
      addProduct(novoProduto, decimal(1));
      void reload();
      if (continuousScan) {
        setTimeout(() => void handleScan(), 300);
      }
    } catch (err: unknown) {
      setCartError(err instanceof Error ? err.message : 'Falha ao salvar novo produto.');
    }
  };

  const criarClienteRapido = async () => {
    if (!novoClienteNome.trim()) return;
    try {
      const res = await apiRequest<Devedor>('/api/devedores', {
        method: 'POST',
        body: JSON.stringify({ nome: novoClienteNome.trim(), divida: '0.01' }),
      });
      setCriandoNovoCliente(false);
      setNovoClienteNome('');
      setClienteId(String(res.id));
      toast(`Cliente ${novoClienteNome} registado.`);
      void reload();
    } catch (err: unknown) {
      setCartError(err instanceof Error ? err.message : 'Falha ao registar cliente.');
    }
  };

  // Manipulação de pagamentos divididos
  const addLinhaPagamento = () => {
    setPagamentos((prev) => [
      ...prev,
      { id: crypto.randomUUID(), metodo: 'MPESA', valor: faltaPagar.gt(0) ? faltaPagar.toFixed(2) : '' },
    ]);
  };

  const updateLinhaPagamento = (id: string, field: 'metodo' | 'valor', value: string) => {
    setPagamentos((prev) =>
      prev.map((p) => (p.id === id ? { ...p, [field]: value } : p)),
    );
  };

  const removeLinhaPagamento = (id: string) => {
    if (pagamentos.length <= 1) return;
    setPagamentos((prev) => prev.filter((p) => p.id !== id));
  };

  const finishSale = (event: React.FormEvent) => {
    event.preventDefault();
    if (carrinho.length === 0) return;

    if (temFiado && !clienteId) {
      setCartError('Selecione ou registe o cliente para a venda a fiado.');
      return;
    }

    if (!temFiado && faltaPagar.gt(0)) {
      setCartError(`Ainda falta receber ${formatMoney(faltaPagar)}. Complete os pagamentos.`);
      return;
    }

    void save.run(async () => {
      const payload = {
        itens: carrinho.map((item) => ({
          produtoId: item.produto.id,
          quantidade: item.quantidade.toFixed(3),
        })),
        pagamentos: pagamentos.map((p) => ({
          metodo: p.metodo,
          valor: p.valor || (pagamentos.length === 1 ? total.toFixed(2) : '0.00'),
        })),
        clienteId: clienteId ? Number(clienteId) : undefined,
        observacao: observacao.trim() || undefined,
      };

      const result = await apiRequest<{
        id: number;
        numero: number;
        itens: number;
        total: string;
        troco: string;
      }>('/api/vendas/lote', {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(payload),
      });

      const clienteObj = devedores.find((d) => String(d.id) === String(clienteId));
      const dadosRecibo: ReciboDados = {
        id: result.id,
        numero: result.numero,
        data: new Date().toLocaleDateString('pt-MZ'),
        hora: new Date().toLocaleTimeString('pt-MZ', { hour: '2-digit', minute: '2-digit' }),
        total: result.total,
        troco: result.troco,
        observacao: observacao.trim() || undefined,
        clienteNome: clienteObj?.nome,
        itens: carrinho.map((item) => ({
          produto: item.produto.nome,
          quantidade: item.quantidade.toFixed(3),
          unidade: item.produto.unidade,
          precoUnitario: item.produto.preco,
          total: decimal(item.produto.preco).times(item.quantidade).toFixed(2),
        })),
        pagamentos: pagamentos.map((p) => ({
          metodo: p.metodo,
          valor: p.valor || total.toFixed(2),
        })),
      };

      setShowModal(false);
      resetCart();
      setReciboSucesso(dadosRecibo);
      toast(`Venda registada: ${formatMoney(result.total)}.`);
      void reload();
      void notificarVendaRegistada(`${result.itens} produtos`, result.total).catch(() => {});
    });
  };

  const abrirDetalhes = async (venda: VendaDocumento) => {
    try {
      const detalhada = await apiRequest<VendaDocumento>(`/api/vendas/${venda.id}`);
      setVendaDetalhes(detalhada);
    } catch {
      setVendaDetalhes(venda);
    }
  };

  const handleAnularVenda = (e: React.FormEvent) => {
    e.preventDefault();
    if (!vendaParaAnular || !motivoAnulacao.trim()) return;

    void anularMutation.run(async () => {
      await apiRequest(`/api/vendas/${vendaParaAnular.id}/anular`, {
        method: 'POST',
        body: JSON.stringify({ motivo: motivoAnulacao.trim() }),
      });
      setVendaParaAnular(null);
      setMotivoAnulacao('');
      if (vendaDetalhes?.id === vendaParaAnular.id) {
        setVendaDetalhes(null);
      }
      toast('Venda anulada e stock reposto.');
      void reload();
    });
  };

  return (
    <div>
      <PageHeading
        title="Vendas"
        description="Emissão de documentos, pagamentos múltiplos, fiado e fecho diário."
      >
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <button className="btn-secondary" onClick={() => setShowFechoModal(true)}>
            📊 Fecho de Caixa
          </button>
          <button className="btn-primary" disabled={!data} onClick={() => setShowModal(true)}>
            <span aria-hidden="true">➕</span> Registar venda
          </button>
        </div>
      </PageHeading>

      <div className="toolbar" style={{ flexWrap: 'wrap', gap: 12 }}>
        <SearchField value={search} onChange={setSearch} label="Pesquisar venda" />

        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            aria-label="Filtrar por estado"
            value={filterEstado}
            onChange={(e) => setFilterEstado(e.target.value)}
            style={{ padding: '7px 12px', borderRadius: 6, border: '1px solid var(--border-color, #ccc)' }}
          >
            <option value="todos">Todos os estados</option>
            <option value="CONCLUIDA">Concluídas</option>
            <option value="ANULADA">Anuladas</option>
          </select>

          <select
            aria-label="Filtrar por método de pagamento"
            value={filterMetodo}
            onChange={(e) => setFilterMetodo(e.target.value)}
            style={{ padding: '7px 12px', borderRadius: 6, border: '1px solid var(--border-color, #ccc)' }}
          >
            <option value="todos">Todos os métodos</option>
            <option value="DINHEIRO">Dinheiro</option>
            <option value="MPESA">M-Pesa</option>
            <option value="EMOLA">e-Mola</option>
            <option value="MKESH">mKesh</option>
            <option value="CARTAO">Cartão</option>
            <option value="FIADO">A Fiado</option>
          </select>
        </div>

        <span className="result-count">
          {filteredSales.length} de {vendas.length} registos
        </span>
      </div>

      {error && <LoadError message={error} retry={reload} />}

      {loading && !data ? (
        <Loading />
      ) : (
        data && (
          <div className="card">
            {vendas.length === 0 ? (
              <EmptyState
                title="A primeira venda começa aqui"
                description="Adicione produtos ao carrinho e confirme a venda para a ver neste histórico."
                icon="🛒"
              >
                <button className="btn-secondary" onClick={() => setShowModal(true)}>
                  ➕ Registar venda
                </button>
              </EmptyState>
            ) : filteredSales.length === 0 ? (
              <EmptyState
                title="Nenhuma venda encontrada"
                description="Experimente alterar os filtros de pesquisa."
                icon="🔍"
              >
                <button
                  className="btn-secondary"
                  onClick={() => {
                    setSearch('');
                    setFilterEstado('todos');
                    setFilterMetodo('todos');
                  }}
                >
                  🔄 Limpar filtros
                </button>
              </EmptyState>
            ) : (
              <div className="table-wrapper">
                <table className="responsive-table">
                  <caption className="sr-only">Histórico de vendas</caption>
                  <thead>
                    <tr>
                      <th scope="col">Venda #</th>
                      <th scope="col">Data / Hora</th>
                      <th scope="col">Resumo</th>
                      <th scope="col">Método(s)</th>
                      <th scope="col" className="numeric">Total</th>
                      <th scope="col">Estado</th>
                      <th scope="col">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSales.map((v) => {
                      const metodosStr = v.pagamentos && v.pagamentos.length > 0
                        ? v.pagamentos.map((p) => formatarMetodoPagamento(p.metodo)).join(' + ')
                        : 'Dinheiro';
                      const isAnulada = v.estado === 'ANULADA';

                      return (
                        <tr key={v.id} style={{ opacity: isAnulada ? 0.65 : 1 }}>
                          <td data-label="Venda #" className="cell-name font-mono">
                            <strong>#{v.numero}</strong>
                          </td>
                          <td data-label="Data / Hora" className="cell-secondary">
                            {v.data} {v.hora && <span style={{ fontSize: 12 }}>{v.hora}</span>}
                          </td>
                          <td data-label="Resumo" className="cell-name">
                            {v.produto || `Venda #${v.numero}`}
                            {v.observacao && (
                              <div style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                                {v.observacao}
                              </div>
                            )}
                          </td>
                          <td data-label="Método(s)">
                            <span style={{ fontSize: 13 }}>{metodosStr}</span>
                          </td>
                          <td data-label="Total" className="numeric font-mono">
                            <strong style={{ textDecoration: isAnulada ? 'line-through' : 'none' }}>
                              {formatMoney(v.total)}
                            </strong>
                          </td>
                          <td data-label="Estado">
                            {isAnulada ? (
                              <span
                                className="badge"
                                style={{ background: '#fee2e2', color: '#991b1b', border: '1px solid #f87171' }}
                                title={v.motivoAnulacao ? `Motivo: ${v.motivoAnulacao}` : 'Venda anulada'}
                              >
                                ✕ Anulada
                              </span>
                            ) : (
                              <span
                                className="badge"
                                style={{ background: '#dcfce7', color: '#166534', border: '1px solid #86efac' }}
                              >
                                ✓ Concluída
                              </span>
                            )}
                          </td>
                          <td data-label="Ações">
                            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                              <button
                                type="button"
                                className="btn-secondary"
                                style={{ padding: '3px 8px', fontSize: 12 }}
                                onClick={() => void abrirDetalhes(v)}
                              >
                                👁️ Detalhes
                              </button>
                              <button
                                type="button"
                                className="btn-secondary"
                                style={{ padding: '3px 8px', fontSize: 12 }}
                                onClick={() => {
                                  const clienteObj = devedores.find((d) => d.id === v.clienteId);
                                  const dados: ReciboDados = {
                                    id: v.id,
                                    numero: v.numero,
                                    data: v.data,
                                    hora: v.hora,
                                    total: v.total,
                                    troco: v.troco,
                                    observacao: v.observacao,
                                    clienteNome: clienteObj?.nome,
                                    itens: v.itens && v.itens.length > 0
                                      ? v.itens.map((it) => ({
                                          produto: it.produto,
                                          quantidade: it.quantidade,
                                          precoUnitario: it.precoUnitario,
                                          total: it.total,
                                        }))
                                      : [{ produto: v.produto || `Venda #${v.numero}`, quantidade: v.quantidade || '1', precoUnitario: v.total, total: v.total }],
                                    pagamentos: v.pagamentos?.map((p) => ({
                                      metodo: p.metodo,
                                      valor: p.valor,
                                      troco: p.troco,
                                    })),
                                  };
                                  imprimirReciboTexto(dados);
                                }}
                              >
                                🖨️ Recibo
                              </button>
                              {!isAnulada && (
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ padding: '3px 8px', fontSize: 12, color: 'var(--color-danger)' }}
                                  onClick={() => {
                                    setMotivoAnulacao('');
                                    setVendaParaAnular(v);
                                  }}
                                >
                                  ❌ Anular
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )
      )}

      {/* MODAL DE REGISTO DE VENDA */}
      {showModal && (
        <Modal title="Registar Venda" onClose={closeModal} busy={save.pending || scan.pending} wide>
          <form onSubmit={finishSale}>
            {save.error && <Notice>{save.error}</Notice>}
            {scan.error && <Notice>{scan.error}</Notice>}
            {cartError && <Notice>{cartError}</Notice>}
            {scanMessage && <Notice kind="success">{scanMessage}</Notice>}

            <fieldset disabled={save.pending || scan.pending}>
              <div className="sale-add-row">
                <div className="product-combobox">
                  <label htmlFor="sale-product" className="field-label">
                    Pesquisar produto
                  </label>
                  <div className="search-field">
                    <span className="search-icon" aria-hidden="true">🔍</span>
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
                        setBusca(e.target.value);
                        setSelectedProd(null);
                        setShowSuggestions(true);
                        setActiveOption(-1);
                      }}
                      onFocus={() => {
                        if (!selectedProd) setShowSuggestions(true);
                      }}
                      onBlur={() => setShowSuggestions(false)}
                      onKeyDown={(event) => {
                        if (event.key === 'ArrowDown') {
                          event.preventDefault();
                          setShowSuggestions(true);
                          setActiveOption((index) => Math.min(index + 1, suggestions.length - 1));
                        }
                        if (event.key === 'ArrowUp') {
                          event.preventDefault();
                          setActiveOption((index) => Math.max(0, index - 1));
                        }
                        if (event.key === 'Escape' && showSuggestions) {
                          event.preventDefault();
                          setShowSuggestions(false);
                        }
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          if (showSuggestions && activeOption >= 0 && suggestions[activeOption]) {
                            selectProduct(suggestions[activeOption]);
                          } else if (selectedProd) {
                            addSelected();
                          }
                        }
                      }}
                    />
                  </div>
                  {showSuggestions && (
                    <ul id="sale-suggestions" ref={suggestionsRef} role="listbox" className="product-suggestions">
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
                            onClick={() => selectProduct(p)}
                          >
                            <strong>{p.nome}</strong>
                            <span>
                              {formatMoney(p.preco)} · Stock: {formatQuantity(p.stock, p.unidade || 'un')}
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
                    onChange={(e) => setQuantity(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        addSelected();
                      }
                    }}
                  />
                </Field>

                <button
                  type="button"
                  className="btn-primary"
                  onClick={addSelected}
                  disabled={!selectedProd}
                >
                  <span aria-hidden="true">➕</span> Adicionar
                </button>

                <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                  <button type="button" className="btn-secondary" onClick={() => void handleScan()}>
                    {scan.pending ? <Spinner size="small" /> : <span aria-hidden="true">📷</span>} Ler código
                  </button>
                  <label style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 13, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={continuousScan}
                      onChange={(e) => setContinuousScan(e.target.checked)}
                    />
                    Scanner contínuo (reabre após leitura)
                  </label>
                </div>
              </div>

              {/* LISTA DO CARRINHO */}
              <div className="cart-heading">
                <h4>🛒 Carrinho</h4>
                <span>
                  {carrinho.length} produto{carrinho.length === 1 ? '' : 's'}
                </span>
              </div>

              {carrinho.length === 0 ? (
                <div className="cart-empty">
                  <p>O carrinho está vazio.</p>
                  <span>Pesquise um produto ou leia o código de barras para o adicionar.</span>
                </div>
              ) : (
                <div className="cart-list">
                  {carrinho.map((item) => {
                    const unit = item.produto.unidade || 'un';
                    const step = decimal(['kg', 'L', 'g', 'ml', 'm'].includes(unit) ? '0.5' : '1');
                    return (
                      <div className="cart-item" key={item.produto.id}>
                        <div className="cart-product">
                          <strong>{item.produto.nome}</strong>
                          <span>{formatMoney(item.produto.preco)} / {unit}</span>
                        </div>
                        <div className="quantity-control">
                          <button
                            type="button"
                            className="icon-btn"
                            onClick={() => changeQuantity(item.produto.id, item.quantidade.minus(step))}
                          >
                            ➖
                          </button>
                          <span>{formatQuantity(item.quantidade, unit)}</span>
                          <button
                            type="button"
                            className="icon-btn"
                            onClick={() => changeQuantity(item.produto.id, item.quantidade.plus(step))}
                          >
                            ➕
                          </button>
                        </div>
                        <strong className="cart-subtotal numeric">
                          {formatMoney(decimal(item.produto.preco).times(item.quantidade))}
                        </strong>
                        <button
                          type="button"
                          className="icon-btn delete cart-remove"
                          onClick={() => setCarrinho((prev) => prev.filter((e) => e.produto.id !== item.produto.id))}
                        >
                          🗑️
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* SEÇÃO DE PAGAMENTO */}
              <div style={{ marginTop: 24, borderTop: '2px solid var(--border-color)', paddingTop: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                  <div>
                    <span style={{ fontSize: 13, color: 'var(--text-muted, #666)' }}>Total do Carrinho:</span>
                    <strong style={{ display: 'block', fontSize: 24, color: 'var(--color-brand)' }}>
                      {formatMoney(total)}
                    </strong>
                  </div>

                  <button
                    type="button"
                    className="btn-secondary"
                    style={{ fontSize: 13 }}
                    onClick={addLinhaPagamento}
                  >
                    ➕ Dividir Pagamento
                  </button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {pagamentos.map((p, idx) => (
                    <div key={p.id} className="sale-payment-split-row">
                      <Field id={`metodo-${p.id}`} label={`Forma ${idx + 1}`}>
                        <select
                          id={`metodo-${p.id}`}
                          value={p.metodo}
                          onChange={(e) => updateLinhaPagamento(p.id, 'metodo', e.target.value)}
                        >
                          <option value="DINHEIRO">Dinheiro</option>
                          <option value="MPESA">M-Pesa</option>
                          <option value="EMOLA">e-Mola</option>
                          <option value="MKESH">mKesh</option>
                          <option value="CARTAO">Cartão</option>
                          <option value="FIADO">A Fiado</option>
                        </select>
                      </Field>

                      <Field id={`valor-${p.id}`} label="Valor (MT)">
                        <input
                          id={`valor-${p.id}`}
                          type="number"
                          step="0.01"
                          min="0.01"
                          value={p.valor}
                          placeholder={pagamentos.length === 1 ? total.toFixed(2) : '0,00'}
                          onChange={(e) => updateLinhaPagamento(p.id, 'valor', e.target.value)}
                        />
                      </Field>

                      <button
                        type="button"
                        className="icon-btn delete"
                        disabled={pagamentos.length <= 1}
                        onClick={() => removeLinhaPagamento(p.id)}
                        style={{ marginBottom: 6 }}
                        title="Remover linha"
                      >
                        🗑️
                      </button>
                    </div>
                  ))}
                </div>

                {/* CAMPO DE CLIENTE CASO TENHA FIADO */}
                {temFiado && (
                  <div style={{ marginTop: 16, padding: 12, background: '#fffbeb', borderRadius: 8, border: '1px solid #fef3c7' }}>
                    <label htmlFor="cliente-fiado" style={{ display: 'block', fontWeight: 600, marginBottom: 6 }}>
                      👤 Cliente Devedor (Obrigatório para Fiado):
                    </label>
                    <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                      <select
                        id="cliente-fiado"
                        value={clienteId}
                        onChange={(e) => setClienteId(e.target.value)}
                        style={{ flex: 1, padding: 8, borderRadius: 6, border: '1px solid #d1d5db' }}
                        required
                      >
                        <option value="">Selecione o cliente…</option>
                        {devedores.map((d) => (
                          <option key={d.id} value={d.id}>
                            {d.nome} {d.saldo ? `(Saldo: ${formatMoney(d.saldo)})` : ''}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => setCriandoNovoCliente((v) => !v)}
                      >
                        {criandoNovoCliente ? 'Cancelar' : '➕ Novo'}
                      </button>
                    </div>

                    {criandoNovoCliente && (
                      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                        <input
                          type="text"
                          placeholder="Nome do novo cliente"
                          value={novoClienteNome}
                          onChange={(e) => setNovoClienteNome(e.target.value)}
                          style={{ flex: 1, padding: 8, borderRadius: 6, border: '1px solid #d1d5db' }}
                        />
                        <button
                          type="button"
                          className="btn-primary"
                          onClick={() => void criarClienteRapido()}
                          disabled={!novoClienteNome.trim()}
                        >
                          Guardar
                        </button>
                      </div>
                    )}
                  </div>
                )}

                {/* RESUMO DE VALORES E TROCO */}
                <div style={{ marginTop: 16 }}>
                  {trocoCalculado.gt(0) && (
                    <Notice kind="success">
                      ✓ Troco a devolver ao cliente: <strong>{formatMoney(trocoCalculado)}</strong>
                    </Notice>
                  )}
                  {faltaPagar.gt(0) && !temFiado && (
                    <Notice kind="error">
                      Falta receber: <strong>{formatMoney(faltaPagar)}</strong>
                    </Notice>
                  )}
                </div>

                <div style={{ marginTop: 16 }}>
                  <label htmlFor="sale-obs" style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
                    Observações (Opcional):
                  </label>
                  <input
                    id="sale-obs"
                    type="text"
                    value={observacao}
                    onChange={(e) => setObservacao(e.target.value)}
                    placeholder="Ex: Entrega ao domicílio, cliente regular..."
                    style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid var(--border-color, #ccc)' }}
                  />
                </div>
              </div>

              <div className="modal-actions" style={{ marginTop: 24 }}>
                <button type="button" className="btn-secondary" onClick={closeModal}>
                  Cancelar
                </button>
                <button
                  className="btn-primary"
                  type="submit"
                  disabled={carrinho.length === 0 || save.pending}
                >
                  {save.pending ? <Spinner size="small" /> : <span aria-hidden="true">✅</span>}
                  {save.pending ? ' A registar…' : ' Confirmar venda'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}

      {/* MODAL DE CADASTRO RÁPIDO AO LER CÓDIGO INEXISTENTE (PASSO 2.3) */}
      {quickCreateBarcode && (
        <Modal
          title={`Novo Produto: Código ${quickCreateBarcode}`}
          onClose={() => setQuickCreateBarcode(null)}
        >
          <form onSubmit={handleSalvarProdutoRapido}>
            <p style={{ margin: '0 0 16px 0', fontSize: 14, color: 'var(--text-muted, #666)' }}>
              Este código de barras não existe no catálogo. Preencha os dados para cadastrar e adicionar diretamente ao carrinho:
            </p>
            <Field id="quick-nome" label="Nome do Produto *">
              <input
                id="quick-nome"
                value={quickNome}
                onChange={(e) => setQuickNome(e.target.value)}
                required
                autoFocus
                placeholder="Ex: Coca-Cola 330ml"
              />
            </Field>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field id="quick-preco" label="Preço de Venda (MT) *">
                <input
                  id="quick-preco"
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={quickPreco}
                  onChange={(e) => setQuickPreco(e.target.value)}
                  required
                  placeholder="0,00"
                />
              </Field>
              <Field id="quick-custo" label="Custo Unitário (MT)">
                <input
                  id="quick-custo"
                  type="number"
                  step="0.01"
                  min="0"
                  value={quickCusto}
                  onChange={(e) => setQuickCusto(e.target.value)}
                  placeholder="0,00"
                />
              </Field>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field id="quick-stock" label="Stock Inicial">
                <input
                  id="quick-stock"
                  type="number"
                  step="0.001"
                  min="0"
                  value={quickStock}
                  onChange={(e) => setQuickStock(e.target.value)}
                />
              </Field>
              <Field id="quick-cat" label="Categoria">
                <select
                  id="quick-cat"
                  value={quickCategoriaId}
                  onChange={(e) => setQuickCategoriaId(e.target.value)}
                >
                  {categorias.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nome}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
            <div className="modal-actions" style={{ marginTop: 20 }}>
              <button type="button" className="btn-secondary" onClick={() => setQuickCreateBarcode(null)}>
                Cancelar
              </button>
              <button type="submit" className="btn-primary">
                💾 Guardar e Adicionar
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL DE RECIBO / PÓS-VENDA CONCLUÍDA */}
      {reciboSucesso && (
        <Modal
          title={`🎉 Venda #${reciboSucesso.numero} Concluída!`}
          onClose={() => setReciboSucesso(null)}
        >
          <div style={{ padding: 12, background: '#f8fafc', borderRadius: 8, marginBottom: 16 }}>
            <pre
              style={{
                fontFamily: 'monospace',
                fontSize: 12,
                whiteSpace: 'pre-wrap',
                margin: 0,
                maxHeight: 280,
                overflowY: 'auto',
              }}
            >
              {gerarTextoRecibo(reciboSucesso)}
            </pre>
          </div>

          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
            <button
              type="button"
              className="btn-primary"
              onClick={() => imprimirReciboTexto(reciboSucesso)}
            >
              🖨️ Imprimir Recibo
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => partilharReciboWhatsApp(reciboSucesso)}
            >
              📱 Enviar por WhatsApp
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => {
                void navigator.clipboard.writeText(gerarTextoRecibo(reciboSucesso));
                toast('Recibo copiado para a área de transferência.');
              }}
            >
              📋 Copiar Texto
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setReciboSucesso(null)}
            >
              Fechar
            </button>
          </div>
        </Modal>
      )}

      {/* MODAL DE DETALHES DE VENDA */}
      {vendaDetalhes && (
        <Modal
          title={`Detalhes do Documento — Venda #${vendaDetalhes.numero}`}
          onClose={() => setVendaDetalhes(null)}
          wide
        >
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12, marginBottom: 16 }}>
            <div>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Data e Hora:</span>
              <strong style={{ display: 'block' }}>{vendaDetalhes.data} {vendaDetalhes.hora}</strong>
            </div>
            <div>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Estado:</span>
              <div>
                {vendaDetalhes.estado === 'ANULADA' ? (
                  <span className="badge" style={{ background: '#fee2e2', color: '#991b1b' }}>
                    ✕ ANULADA
                  </span>
                ) : (
                  <span className="badge" style={{ background: '#dcfce7', color: '#166534' }}>
                    ✓ CONCLUÍDA
                  </span>
                )}
              </div>
            </div>
            <div>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Total da Venda:</span>
              <strong style={{ display: 'block', fontSize: 18, color: 'var(--primary, #1e40af)' }}>
                {formatMoney(vendaDetalhes.total)}
              </strong>
            </div>
            {vendaDetalhes.lucro && (
              <div>
                <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Lucro Bruto:</span>
                <strong style={{ display: 'block', color: '#16a34a' }}>{formatMoney(vendaDetalhes.lucro)}</strong>
              </div>
            )}
          </div>

          {vendaDetalhes.motivoAnulacao && (
            <div style={{ padding: 12, background: '#fee2e2', borderRadius: 8, marginBottom: 16, color: '#991b1b' }}>
              <strong>Motivo da Anulação:</strong> {vendaDetalhes.motivoAnulacao}
              {vendaDetalhes.anuladaEm && <div style={{ fontSize: 12, marginTop: 4 }}>Em: {vendaDetalhes.anuladaEm.replace('T', ' ').slice(0, 16)}</div>}
            </div>
          )}

          {vendaDetalhes.observacao && (
            <p style={{ fontSize: 13, fontStyle: 'italic', marginBottom: 16 }}>
              Observação: {vendaDetalhes.observacao}
            </p>
          )}

          <h4 style={{ margin: '16px 0 8px 0' }}>Itens da Venda</h4>
          <table className="responsive-table" style={{ marginBottom: 16 }}>
            <thead>
              <tr>
                <th>Produto</th>
                <th className="numeric">Quantidade</th>
                <th className="numeric">Preço Unitário</th>
                <th className="numeric">Total</th>
              </tr>
            </thead>
            <tbody>
              {vendaDetalhes.itens && vendaDetalhes.itens.length > 0 ? (
                vendaDetalhes.itens.map((it) => (
                  <tr key={it.id}>
                    <td><strong>{it.produto}</strong></td>
                    <td className="numeric font-mono">{formatQuantity(it.quantidade, '')}</td>
                    <td className="numeric font-mono">{formatMoney(it.precoUnitario)}</td>
                    <td className="numeric font-mono" style={{ fontWeight: 600 }}>{formatMoney(it.total)}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td>{vendaDetalhes.produto || `Venda #${vendaDetalhes.numero}`}</td>
                  <td className="numeric font-mono">{formatQuantity(vendaDetalhes.quantidade || '1', '')}</td>
                  <td className="numeric font-mono">{formatMoney(vendaDetalhes.total)}</td>
                  <td className="numeric font-mono" style={{ fontWeight: 600 }}>{formatMoney(vendaDetalhes.total)}</td>
                </tr>
              )}
            </tbody>
          </table>

          {vendaDetalhes.pagamentos && vendaDetalhes.pagamentos.length > 0 && (
            <>
              <h4 style={{ margin: '16px 0 8px 0' }}>Formas de Pagamento</h4>
              <table className="responsive-table">
                <thead>
                  <tr>
                    <th>Método</th>
                    <th className="numeric">Valor</th>
                    <th className="numeric">Troco</th>
                  </tr>
                </thead>
                <tbody>
                  {vendaDetalhes.pagamentos.map((p) => (
                    <tr key={p.id}>
                      <td><strong>{formatarMetodoPagamento(p.metodo)}</strong></td>
                      <td className="numeric font-mono">{formatMoney(p.valor)}</td>
                      <td className="numeric font-mono">{formatMoney(p.troco || '0.00')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          <div className="modal-actions" style={{ marginTop: 20 }}>
            <button type="button" className="btn-secondary" onClick={() => setVendaDetalhes(null)}>
              Fechar
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => {
                const clienteObj = devedores.find((d) => d.id === vendaDetalhes.clienteId);
                const dados: ReciboDados = {
                  id: vendaDetalhes.id,
                  numero: vendaDetalhes.numero,
                  data: vendaDetalhes.data,
                  hora: vendaDetalhes.hora,
                  total: vendaDetalhes.total,
                  troco: vendaDetalhes.troco,
                  observacao: vendaDetalhes.observacao,
                  clienteNome: clienteObj?.nome,
                  itens: vendaDetalhes.itens && vendaDetalhes.itens.length > 0
                    ? vendaDetalhes.itens.map((it) => ({
                        produto: it.produto,
                        quantidade: it.quantidade,
                        precoUnitario: it.precoUnitario,
                        total: it.total,
                      }))
                    : [{ produto: vendaDetalhes.produto || `Venda #${vendaDetalhes.numero}`, quantidade: vendaDetalhes.quantidade || '1', precoUnitario: vendaDetalhes.total, total: vendaDetalhes.total }],
                  pagamentos: vendaDetalhes.pagamentos?.map((p) => ({
                    metodo: p.metodo,
                    valor: p.valor,
                    troco: p.troco,
                  })),
                };
                imprimirReciboTexto(dados);
              }}
            >
              🖨️ Imprimir Recibo
            </button>
            {vendaDetalhes.estado === 'CONCLUIDA' && (
              <button
                type="button"
                className="btn-secondary"
                style={{ color: '#dc2626' }}
                onClick={() => {
                  setMotivoAnulacao('');
                  setVendaParaAnular(vendaDetalhes);
                }}
              >
                ❌ Anular Esta Venda
              </button>
            )}
          </div>
        </Modal>
      )}

      {/* MODAL PARA ANULAR VENDA */}
      {vendaParaAnular && (
        <Modal
          title={`Anular Venda #${vendaParaAnular.numero}`}
          onClose={() => setVendaParaAnular(null)}
          busy={anularMutation.pending}
        >
          <form onSubmit={handleAnularVenda}>
            {anularMutation.error && <Notice>{anularMutation.error}</Notice>}
            <p>
              Ao anular a venda, as quantidades serão devolvidas ao stock e a dívida (se houver) será cancelada.
            </p>
            <Field id="anular-motivo" label="Motivo da anulação *">
              <input
                id="anular-motivo"
                type="text"
                value={motivoAnulacao}
                onChange={(e) => setMotivoAnulacao(e.target.value)}
                required
                autoFocus
                placeholder="Ex: Devolução pelo cliente / Erro na quantidade"
              />
            </Field>
            <div className="modal-actions" style={{ marginTop: 20 }}>
              <button type="button" className="btn-secondary" onClick={() => setVendaParaAnular(null)}>
                Cancelar
              </button>
              <button
                type="submit"
                className="btn-primary"
                style={{ background: '#dc2626', borderColor: '#b91c1c' }}
                disabled={!motivoAnulacao.trim()}
              >
                {anularMutation.pending ? <Spinner size="small" /> : 'Confirmar Anulação'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL DE FECHO DE CAIXA */}
      {showFechoModal && <FechoCaixaModal onClose={() => setShowFechoModal(false)} />}
    </div>
  );
}
