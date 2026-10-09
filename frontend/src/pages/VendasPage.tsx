import { useToast } from '../utils/toast';
import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { apiFetch, apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { abrirScanner } from '../utils/scanner';
import { notificarVendaRegistada } from '../utils/notificacoes';
import { decimal, decimalSeguro, formatMoney, formatQuantity, parseDecimalInput } from '../utils/decimal';
import {
  gerarTextoRecibo,
  imprimirReciboTexto,
  partilharReciboWhatsApp,
  formatarMetodoPagamento,
  type ReciboDados,
} from '../utils/recibo';
import { FechoCaixaModal } from '../components/FechoCaixaModal';
import {
  salvarVendaPendente,
  listarVendasPendentes,
  sincronizarVendasPendentes,
  type VendaPendente,
} from '../utils/offlineQueue';
import type Decimal from 'decimal.js';
import {
  Ban,
  BarChart3,
  Check,
  CheckCircle2,
  Copy,
  CreditCard,
  Eye,
  Minus,
  Monitor,
  Percent,
  Plus,
  Printer,
  RefreshCw,
  RotateCcw,
  Save,
  ScanLine,
  Search,
  Share2,
  ShoppingCart,
  Trash2,
  User,
  WifiOff,
  XCircle,
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
  descontoPercentual?: string;
  descontoValor?: string;
  notaDesconto?: string;
}

interface PagamentoVendaDetalhe {
  id: number;
  metodo: string;
  valor: string;
  troco: string;
  referencia?: string;
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

interface MetodoPagamentoConfig {
  id: number;
  nome: string;
  tipo: string;
  ativo: boolean;
  ordem: number;
}

interface DefinicoesLoja {
  controloCaixa: boolean;
  nomeLoja: string;
}

interface SessaoCaixaAtual {
  aberta: boolean;
  id?: number;
  abertaEm?: string;
  valorInicial?: string;
  vendasDinheiro?: string;
  devolucoesDinheiro?: string;
  valorEsperado?: string;
  totalVendas?: number;
}

interface ItemCarrinho {
  produto: Produto;
  quantidade: Decimal;
  descontoPercentual?: string;
  descontoValor?: string;
  notaDesconto?: string;
}

interface LinhaPagamento {
  id: string;
  metodoId?: number;
  metodo: string;
  valor: string;
  referencia?: string;
}

const loadSalesData = async (signal: AbortSignal) => {
  const [vendas, produtos, categorias, devedores, metodos, definicoes, sessaoCaixa] = await Promise.all([
    apiRequest<VendaDocumento[]>('/api/vendas', { signal }),
    apiRequest<Produto[]>('/api/produtos', { signal }),
    apiRequest<Categoria[]>('/api/categorias', { signal }).catch(() => [] as Categoria[]),
    apiRequest<Devedor[]>('/api/devedores', { signal }).catch(() => [] as Devedor[]),
    apiRequest<MetodoPagamentoConfig[]>('/api/metodos-pagamento?apenasAtivos=true', { signal }).catch(
      () => [] as MetodoPagamentoConfig[],
    ),
    apiRequest<DefinicoesLoja>('/api/definicoes', { signal }).catch(
      () => ({ controloCaixa: false, nomeLoja: 'BStore' } as DefinicoesLoja),
    ),
    apiRequest<SessaoCaixaAtual>('/api/caixa/atual', { signal }).catch(
      () => ({ aberta: false } as SessaoCaixaAtual),
    ),
  ]);
  return { vendas, produtos, categorias, devedores, metodos, definicoes, sessaoCaixa };
};

export default function VendasPage() {
  const { data, loading, error, reload } = useResource(loadSalesData);
  const [search, setSearch] = useState('');
  const [filterEstado, setFilterEstado] = useState('todos');
  const [filterMetodo, setFilterMetodo] = useState('todos');

  // Modais principais
  const [showModal, setShowModal] = useState(false);
  const [showFechoModal, setShowFechoModal] = useState(false);
  const [showAbrirCaixaModal, setShowAbrirCaixaModal] = useState(false);
  const [valorAberturaCaixa, setValorAberturaCaixa] = useState('0');
  const [vendaDetalhes, setVendaDetalhes] = useState<VendaDocumento | null>(null);
  const [vendaParaAnular, setVendaParaAnular] = useState<VendaDocumento | null>(null);
  const [motivoAnulacao, setMotivoAnulacao] = useState('');
  const [reciboSucesso, setReciboSucesso] = useState<ReciboDados | null>(null);

  // Devoluções simples
  const [vendaParaDevolver, setVendaParaDevolver] = useState<VendaDocumento | null>(null);
  const [itensDevolucao, setItensDevolucao] = useState<{ itemVendaId: number; quantidade: string }[]>([]);
  const [motivoDevolucao, setMotivoDevolucao] = useState('Engano na venda');
  const [devolvendo, setDevolvendo] = useState(false);

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

  // Desconto por linha (Modal)
  const [itemParaDesconto, setItemParaDesconto] = useState<ItemCarrinho | null>(null);
  const [modalDescTipo, setModalDescTipo] = useState<'percentual' | 'valor'>('percentual');
  const [modalDescValor, setModalDescValor] = useState('');
  const [modalDescNota, setModalDescNota] = useState('');

  // Pagamentos
  const [pagamentos, setPagamentos] = useState<LinhaPagamento[]>([
    { id: '1', metodo: 'DINHEIRO', valor: '' },
  ]);
  const [clienteId, setClienteId] = useState<string>('');
  const [novoClienteNome, setNovoClienteNome] = useState('');
  const [criandoNovoCliente, setCriandoNovoCliente] = useState(false);
  const [observacao, setObservacao] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => crypto.randomUUID());

  // Offline / Resiliência
  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine);
  const [vendasPendentes, setVendasPendentes] = useState<VendaPendente[]>([]);
  const [sincronizando, setSincronizando] = useState(false);

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
  const metodosDisponiveis = useMemo(() => {
    if (data?.metodos && data.metodos.length > 0) return data.metodos;
    return [
      { id: 1, nome: 'Dinheiro', tipo: 'DINHEIRO', ativo: true, ordem: 1 },
      { id: 2, nome: 'M-Pesa', tipo: 'DIGITAL', ativo: true, ordem: 2 },
      { id: 3, nome: 'e-Mola', tipo: 'DIGITAL', ativo: true, ordem: 3 },
      { id: 4, nome: 'A fiado', tipo: 'FIADO', ativo: true, ordem: 4 },
    ];
  }, [data?.metodos]);

  const definicoes = useMemo<DefinicoesLoja>(
    () => data?.definicoes || { controloCaixa: false, nomeLoja: 'BStore' },
    [data?.definicoes],
  );
  const sessaoCaixa = useMemo<SessaoCaixaAtual>(
    () => data?.sessaoCaixa || { aberta: false },
    [data?.sessaoCaixa],
  );

  // Monitorização de rede e fila offline
  const carregarPendentes = useCallback(async () => {
    const p = await listarVendasPendentes();
    setVendasPendentes(p);
  }, []);

  const sincronizarFilaOffline = useCallback(async () => {
    if (sincronizando) return;
    setSincronizando(true);
    try {
      const res = await sincronizarVendasPendentes(async (payload, uuidCliente) => {
        return await apiRequest('/api/vendas/lote', {
          method: 'POST',
          headers: { 'Idempotency-Key': uuidCliente },
          body: JSON.stringify(payload),
        });
      });
      if (res.enviadas > 0) {
        toast(`${res.enviadas} venda(s) enviada(s) para o servidor.`);
        void reload();
      }
      await carregarPendentes();
    } catch {
      // Ignora erro de rede temporário
    } finally {
      setSincronizando(false);
    }
  }, [sincronizando, toast, reload, carregarPendentes]);

  useEffect(() => {
    void carregarPendentes();
    const handleOnline = () => {
      setIsOnline(true);
      void sincronizarFilaOffline();
    };
    const handleOffline = () => setIsOnline(false);
    const handlePendentesAtualizadas = () => void carregarPendentes();

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('bstore:pendentes-atualizadas', handlePendentesAtualizadas);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('bstore:pendentes-atualizadas', handlePendentesAtualizadas);
    };
  }, [carregarPendentes, sincronizarFilaOffline]);

  // Cálculo individual de linha de carrinho (com desconto)
  const calcularLinha = useCallback((item: ItemCarrinho) => {
    const precoUnit = decimalSeguro(item.produto.preco, 0);
    const descPerc = decimalSeguro(item.descontoPercentual, 0);
    const descVal = decimalSeguro(item.descontoValor, 0);
    let precoFinal = precoUnit;

    if (descPerc.gt(0)) {
      precoFinal = precoUnit.minus(precoUnit.times(descPerc).dividedBy(100));
    } else if (descVal.gt(0)) {
      precoFinal = precoUnit.minus(descVal);
    }
    if (precoFinal.lt(0)) precoFinal = decimal(0);

    const subtotal = precoFinal.times(item.quantidade);
    return { precoUnit, precoFinal, subtotal, descPerc, descVal };
  }, []);

  const total = useMemo(() => {
    return carrinho.reduce((sum, item) => sum.plus(calcularLinha(item).subtotal), decimal(0));
  }, [carrinho, calcularLinha]);

  const totalPago = pagamentos.reduce((sum, p) => {
    const val = parseDecimalInput(p.valor) || (pagamentos.length === 1 && !p.valor ? total : null);
    return val && val.gt(0) ? sum.plus(val) : sum;
  }, decimal(0));

  const temDinheiro = pagamentos.some(
    (p) => p.metodo.toUpperCase() === 'DINHEIRO' || p.metodo.toUpperCase().includes('DINHEIRO'),
  );
  const temFiado = pagamentos.some(
    (p) => p.metodo.toUpperCase() === 'FIADO' || p.metodo.toUpperCase().includes('FIADO'),
  );
  const trocoCalculado = temDinheiro && totalPago.gt(total) ? totalPago.minus(total) : decimal(0);
  const faltaPagar = total.gt(totalPago) ? total.minus(totalPago) : decimal(0);

  // Broadcast para tela do cliente (segundo monitor)
  const emitirBroadcastCliente = useCallback(
    (tipo: 'cart_update' | 'sale_success' | 'idle', extra?: Record<string, any>) => {
      if (typeof BroadcastChannel === 'undefined') return;
      try {
        const canal = new BroadcastChannel('bstore_cliente_display');
        if (tipo === 'cart_update') {
          canal.postMessage({
            type: 'cart_update',
            items: carrinho.map((c) => {
              const calc = calcularLinha(c);
              return {
                nome: c.produto.nome,
                quantidade: `${c.quantidade.toFixed(3)} ${c.produto.unidade || 'un'}`,
                precoUnitario: `${formatMoney(calc.precoFinal)}`,
                total: `${formatMoney(calc.subtotal)}`,
              };
            }),
            total: total.toFixed(2),
            nomeLoja: definicoes.nomeLoja || 'BStore',
          });
        } else if (tipo === 'sale_success') {
          canal.postMessage({
            type: 'sale_success',
            total: extra?.total || total.toFixed(2),
            pago: extra?.pago || totalPago.toFixed(2),
            troco: extra?.troco || '0.00',
          });
        } else {
          canal.postMessage({ type: 'idle' });
        }
        canal.close();
      } catch {
        // Broadcast silencioso se indisponível
      }
    },
    [carrinho, calcularLinha, total, totalPago, definicoes.nomeLoja],
  );

  useEffect(() => {
    if (showModal && carrinho.length > 0) {
      emitirBroadcastCliente('cart_update');
    }
  }, [carrinho, showModal, emitirBroadcastCliente]);

  const abrirTelaCliente = () => {
    if (typeof window !== 'undefined') {
      window.open('/cliente', '_blank', 'width=1024,height=768');
    }
  };

  const filteredSales = vendas.filter((v) => {
    const termo = search.trim().toLocaleLowerCase();
    const matchSearch =
      !termo ||
      `${v.numero} ${v.produto || ''} ${v.data} ${v.observacao || ''}`
        .toLocaleLowerCase()
        .includes(termo);
    const matchEstado = filterEstado === 'todos' || v.estado === filterEstado;
    const matchMetodo =
      filterMetodo === 'todos' ||
      (v.pagamentos &&
        v.pagamentos.some((p) => p.metodo.toUpperCase() === filterMetodo.toUpperCase()));
    return matchSearch && matchEstado && matchMetodo;
  });

  const suggestions = produtos
    .filter((p) => p.nome.toLocaleLowerCase().includes(busca.trim().toLocaleLowerCase()))
    .slice(0, 30);

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

  const addProduct = useCallback(
    (product: Produto, qty: Decimal) => {
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
    },
    [carrinho],
  );

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
    emitirBroadcastCliente('idle');
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
        setScanMessage(`${product.nome} adicionado ao carrinho.`);
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
      toast(`Produto ${novoProduto.nome} registado!`);
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

  // Desconto por linha
  const abrirDescontoModal = (item: ItemCarrinho) => {
    setItemParaDesconto(item);
    if (item.descontoValor && Number(item.descontoValor) > 0) {
      setModalDescTipo('valor');
      setModalDescValor(item.descontoValor);
    } else {
      setModalDescTipo('percentual');
      setModalDescValor(item.descontoPercentual || '');
    }
    setModalDescNota(item.notaDesconto || '');
  };

  const salvarDescontoLinha = (e: React.FormEvent) => {
    e.preventDefault();
    if (!itemParaDesconto) return;

    setCarrinho((prev) =>
      prev.map((it) => {
        if (it.produto.id !== itemParaDesconto.produto.id) return it;
        return {
          ...it,
          descontoPercentual: modalDescTipo === 'percentual' ? modalDescValor || undefined : undefined,
          descontoValor: modalDescTipo === 'valor' ? modalDescValor || undefined : undefined,
          notaDesconto: modalDescNota.trim() || undefined,
        };
      }),
    );
    setItemParaDesconto(null);
  };

  const removerDescontoLinha = () => {
    if (!itemParaDesconto) return;
    setCarrinho((prev) =>
      prev.map((it) => {
        if (it.produto.id !== itemParaDesconto.produto.id) return it;
        const copy = { ...it };
        delete copy.descontoPercentual;
        delete copy.descontoValor;
        delete copy.notaDesconto;
        return copy;
      }),
    );
    setItemParaDesconto(null);
  };

  // Manipulação de pagamentos
  const selecionarMetodoPrincipal = (metodo: MetodoPagamentoConfig) => {
    setPagamentos((prev) => {
      const primeiro = prev[0] || { id: '1', metodo: 'DINHEIRO', valor: '' };
      return [
        {
          ...primeiro,
          metodoId: metodo.id,
          metodo: metodo.tipo === 'FIADO' ? 'FIADO' : metodo.nome.toUpperCase(),
        },
        ...prev.slice(1),
      ];
    });
  };

  const addLinhaPagamento = () => {
    const metodoPadrao = metodosDisponiveis.find((m) => m.tipo === 'DIGITAL') || metodosDisponiveis[0];
    setPagamentos((prev) => [
      ...prev,
      {
        id: crypto.randomUUID(),
        metodoId: metodoPadrao?.id,
        metodo: metodoPadrao?.tipo === 'FIADO' ? 'FIADO' : (metodoPadrao?.nome.toUpperCase() || 'MPESA'),
        valor: faltaPagar.gt(0) ? faltaPagar.toFixed(2) : '',
      },
    ]);
  };

  const updateLinhaPagamento = (
    id: string,
    field: 'metodo' | 'valor' | 'referencia',
    value: string,
  ) => {
    setPagamentos((prev) =>
      prev.map((p) => {
        if (p.id !== id) return p;
        if (field === 'metodo') {
          const config = metodosDisponiveis.find(
            (m) => m.nome.toUpperCase() === value.toUpperCase() || m.tipo === value,
          );
          return {
            ...p,
            metodo: value,
            metodoId: config?.id,
          };
        }
        return { ...p, [field]: value };
      }),
    );
  };

  const removeLinhaPagamento = (id: string) => {
    if (pagamentos.length <= 1) return;
    setPagamentos((prev) => prev.filter((p) => p.id !== id));
  };

  // Abrir caixa diário
  const handleAbrirCaixa = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await apiRequest('/api/caixa/abrir', {
        method: 'POST',
        body: JSON.stringify({
          valorInicial: valorAberturaCaixa || '0',
          notaAbertura: 'Abertura rápida no POS',
        }),
      });
      setShowAbrirCaixaModal(false);
      toast('Caixa aberto com sucesso.');
      void reload();
    } catch (err: unknown) {
      toast(err instanceof Error ? err.message : 'Falha ao abrir o caixa.');
    }
  };

  // Submissão da venda (com suporte online + fila offline)
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
        uuidCliente: idempotencyKey,
        itens: carrinho.map((item) => {
          const calc = calcularLinha(item);
          return {
            produtoId: item.produto.id,
            quantidade: item.quantidade.toFixed(3),
            descontoPercentual: calc.descPerc.gt(0) ? calc.descPerc.toFixed(2) : undefined,
            descontoValor: calc.descVal.gt(0) ? calc.descVal.toFixed(2) : undefined,
            nota: item.notaDesconto,
          };
        }),
        pagamentos: pagamentos.map((p) => ({
          metodoId: p.metodoId,
          metodo: p.metodo,
          valor: p.valor || (pagamentos.length === 1 ? total.toFixed(2) : '0.00'),
          referencia: p.referencia?.trim() || undefined,
        })),
        clienteId: clienteId ? Number(clienteId) : undefined,
        observacao: observacao.trim() || undefined,
      };

      const clienteObj = devedores.find((d) => String(d.id) === String(clienteId));
      const dadosRecibo: ReciboDados = {
        id: 0,
        numero: 0,
        data: new Date().toLocaleDateString('pt-MZ'),
        hora: new Date().toLocaleTimeString('pt-MZ', { hour: '2-digit', minute: '2-digit' }),
        total: total.toFixed(2),
        troco: trocoCalculado.toFixed(2),
        observacao: observacao.trim() || undefined,
        clienteNome: clienteObj?.nome,
        itens: carrinho.map((item) => {
          const c = calcularLinha(item);
          return {
            produto: item.produto.nome,
            quantidade: item.quantidade.toFixed(3),
            unidade: item.produto.unidade,
            precoUnitario: c.precoFinal.toFixed(2),
            total: c.subtotal.toFixed(2),
          };
        }),
        pagamentos: pagamentos.map((p) => ({
          metodo: p.metodo,
          valor: p.valor || total.toFixed(2),
          troco: trocoCalculado.toFixed(2),
        })),
      };

      // MODO OFFLINE DETETADO ANTES OU DURANTE O PEDIDO
      if (!navigator.onLine) {
        await salvarVendaPendente({
          uuidCliente: idempotencyKey,
          dataCriacao: new Date().toISOString(),
          payload,
        });
        emitirBroadcastCliente('sale_success', {
          total: total.toFixed(2),
          pago: totalPago.toFixed(2),
          troco: trocoCalculado.toFixed(2),
        });
        setShowModal(false);
        resetCart();
        setReciboSucesso(dadosRecibo);
        toast('Venda guardada no aparelho (sem internet). Será enviada quando a rede voltar.');
        await carregarPendentes();
        return;
      }

      try {
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

        dadosRecibo.id = result.id;
        dadosRecibo.numero = result.numero;
        dadosRecibo.total = result.total;
        dadosRecibo.troco = result.troco;

        emitirBroadcastCliente('sale_success', {
          total: result.total,
          pago: totalPago.toFixed(2),
          troco: result.troco,
        });

        setShowModal(false);
        resetCart();
        setReciboSucesso(dadosRecibo);
        toast(`Venda registada: ${formatMoney(result.total)}.`);
        void reload();
        void notificarVendaRegistada(`${result.itens} produtos`, result.total).catch(() => {});
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : '';
        if (
          !navigator.onLine ||
          msg.includes('fetch') ||
          msg.includes('Network') ||
          msg.includes('Failed to')
        ) {
          await salvarVendaPendente({
            uuidCliente: idempotencyKey,
            dataCriacao: new Date().toISOString(),
            payload,
          });
          emitirBroadcastCliente('sale_success', {
            total: total.toFixed(2),
            pago: totalPago.toFixed(2),
            troco: trocoCalculado.toFixed(2),
          });
          setShowModal(false);
          resetCart();
          setReciboSucesso(dadosRecibo);
          toast('Falha de rede: venda guardada no aparelho para envio posterior.');
          await carregarPendentes();
        } else {
          throw err;
        }
      }
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

  // Processar Devolução
  const iniciarDevolucao = (venda: VendaDocumento) => {
    setVendaParaDevolver(venda);
    setMotivoDevolucao('Engano na venda');
    if (venda.itens && venda.itens.length > 0) {
      setItensDevolucao(
        venda.itens.map((it) => ({
          itemVendaId: it.id,
          quantidade: '',
        })),
      );
    } else {
      setItensDevolucao([]);
    }
  };

  const handleConfirmarDevolucao = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!vendaParaDevolver) return;

    const itensParaEnviar = itensDevolucao
      .filter((it) => parseDecimalInput(it.quantidade)?.gt(0))
      .map((it) => ({
        itemVendaId: it.itemVendaId,
        quantidade: it.quantidade,
      }));

    if (itensParaEnviar.length === 0) {
      toast('Indique a quantidade a devolver em pelo menos um item.');
      return;
    }

    setDevolvendo(true);
    try {
      await apiRequest(`/api/vendas/${vendaParaDevolver.id}/devolver`, {
        method: 'POST',
        body: JSON.stringify({
          motivo: motivoDevolucao,
          uuidCliente: crypto.randomUUID(),
          itens: itensParaEnviar,
        }),
      });
      toast('Devolução registada com sucesso e stock reposto.');
      setVendaParaDevolver(null);
      if (vendaDetalhes?.id === vendaParaDevolver.id) {
        setVendaDetalhes(null);
      }
      void reload();
    } catch (err: unknown) {
      toast(err instanceof Error ? err.message : 'Falha ao processar devolução.');
    } finally {
      setDevolvendo(false);
    }
  };

  const abrirNovaVenda = () => {
    if (definicoes.controloCaixa && !sessaoCaixa.aberta) {
      setShowAbrirCaixaModal(true);
      return;
    }
    setShowModal(true);
  };

  return (
    <div>
      <PageHeading
        title="Vendas"
        description="Ponto de venda simples, pagamentos múltiplos, descontos e tela do cliente."
      >
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          {typeof BroadcastChannel !== 'undefined' && (
            <button
              type="button"
              className="btn-secondary"
              onClick={abrirTelaCliente}
              title="Abrir tela do cliente num segundo monitor"
            >
              <Monitor size={16} strokeWidth={2} aria-hidden="true" /> Tela do cliente
            </button>
          )}

          <button
            type="button"
            className="btn-secondary"
            onClick={() => setShowFechoModal(true)}
          >
            <BarChart3 size={16} strokeWidth={2} aria-hidden="true" /> Fecho de Caixa
          </button>

          <button
            type="button"
            className="btn-primary"
            disabled={!data}
            onClick={abrirNovaVenda}
          >
            <Plus size={16} strokeWidth={2.2} aria-hidden="true" /> Registar venda
          </button>
        </div>
      </PageHeading>

      {/* BARRA DE ESTADO DE CAIXA SE CONTROLO ESTIVER ACTIVO */}
      {definicoes.controloCaixa && (
        <div className="caixa-status-bar">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span
              style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                backgroundColor: sessaoCaixa.aberta ? '#16a34a' : '#ea580c',
              }}
            />
            <span style={{ fontSize: 13, fontWeight: 600 }}>
              {sessaoCaixa.aberta ? 'Caixa aberta' : 'Caixa fechada'}
            </span>
            {sessaoCaixa.aberta && (
              <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                Fundo: {formatMoney(sessaoCaixa.valorInicial || '0')} | Esperado em dinheiro:{' '}
                {formatMoney(sessaoCaixa.valorEsperado || '0')}
              </span>
            )}
          </div>

          <div>
            {sessaoCaixa.aberta ? (
              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: 12, padding: '4px 10px' }}
                onClick={() => setShowFechoModal(true)}
              >
                Fechar caixa
              </button>
            ) : (
              <button
                type="button"
                className="btn-primary"
                style={{ fontSize: 12, padding: '4px 10px' }}
                onClick={() => setShowAbrirCaixaModal(true)}
              >
                Abrir caixa
              </button>
            )}
          </div>
        </div>
      )}

      {/* AVISO OFFLINE E VENDAS PENDENTES */}
      {(!isOnline || vendasPendentes.length > 0) && (
        <div className="offline-warning-bar">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <WifiOff size={16} />
            <span>
              {!isOnline
                ? 'Sem ligação à internet. As vendas continuam a ser guardadas no aparelho.'
                : `${vendasPendentes.length} venda(s) guardadas no aparelho à espera de sincronização.`}
            </span>
          </div>
          {isOnline && vendasPendentes.length > 0 && (
            <button
              type="button"
              className="btn-secondary"
              style={{ fontSize: 12, padding: '3px 8px' }}
              disabled={sincronizando}
              onClick={() => void sincronizarFilaOffline()}
            >
              {sincronizando ? <Spinner size="small" /> : 'Sincronizar agora'}
            </button>
          )}
        </div>
      )}

      <div className="toolbar" style={{ flexWrap: 'wrap', gap: 12 }}>
        <SearchField value={search} onChange={setSearch} label="Pesquisar venda" />

        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            aria-label="Filtrar por estado"
            value={filterEstado}
            onChange={(e) => setFilterEstado(e.target.value)}
            style={{
              padding: '7px 12px',
              borderRadius: 6,
              border: '1px solid var(--border-color, #ccc)',
            }}
          >
            <option value="todos">Todos os estados</option>
            <option value="CONCLUIDA">Concluídas</option>
            <option value="PARCIALMENTE_DEVOLVIDA">Parcialmente Devolvidas</option>
            <option value="DEVOLVIDA">Devolvidas</option>
            <option value="ANULADA">Anuladas</option>
          </select>

          <select
            aria-label="Filtrar por método de pagamento"
            value={filterMetodo}
            onChange={(e) => setFilterMetodo(e.target.value)}
            style={{
              padding: '7px 12px',
              borderRadius: 6,
              border: '1px solid var(--border-color, #ccc)',
            }}
          >
            <option value="todos">Todos os métodos</option>
            {metodosDisponiveis.map((m) => (
              <option key={m.id} value={m.tipo === 'FIADO' ? 'FIADO' : m.nome.toUpperCase()}>
                {m.nome}
              </option>
            ))}
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
                icon={<ShoppingCart size={36} strokeWidth={1.8} aria-hidden="true" />}
              >
                <button className="btn-secondary" onClick={abrirNovaVenda}>
                  <Plus size={16} strokeWidth={2} aria-hidden="true" /> Registar venda
                </button>
              </EmptyState>
            ) : filteredSales.length === 0 ? (
              <EmptyState
                title="Nenhuma venda encontrada"
                description="Experimente alterar os filtros de pesquisa."
                icon={<Search size={36} strokeWidth={1.8} aria-hidden="true" />}
              >
                <button
                  className="btn-secondary"
                  onClick={() => {
                    setSearch('');
                    setFilterEstado('todos');
                    setFilterMetodo('todos');
                  }}
                >
                  <RefreshCw size={14} strokeWidth={2} aria-hidden="true" /> Limpar filtros
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
                      <th scope="col" className="numeric">
                        Total
                      </th>
                      <th scope="col">Estado</th>
                      <th scope="col">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSales.map((v) => {
                      const metodosStr =
                        v.pagamentos && v.pagamentos.length > 0
                          ? v.pagamentos
                              .map((p) => formatarMetodoPagamento(p.metodo))
                              .join(' + ')
                          : 'Dinheiro';
                      const isAnulada = v.estado === 'ANULADA';
                      const isDevolvida =
                        v.estado === 'DEVOLVIDA' || v.estado === 'PARCIALMENTE_DEVOLVIDA';

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
                            <strong
                              style={{
                                textDecoration: isAnulada ? 'line-through' : 'none',
                              }}
                            >
                              {formatMoney(v.total)}
                            </strong>
                          </td>
                          <td data-label="Estado">
                            {isAnulada ? (
                              <span
                                className="badge"
                                style={{
                                  background: '#fee2e2',
                                  color: '#991b1b',
                                  border: '1px solid #f87171',
                                }}
                                title={
                                  v.motivoAnulacao
                                    ? `Motivo: ${v.motivoAnulacao}`
                                    : 'Venda anulada'
                                }
                              >
                                <XCircle size={12} strokeWidth={2} aria-hidden="true" /> Anulada
                              </span>
                            ) : isDevolvida ? (
                              <span
                                className="badge"
                                style={{
                                  background: '#fef3c7',
                                  color: '#92400e',
                                  border: '1px solid #fde68a',
                                }}
                              >
                                <RotateCcw size={12} strokeWidth={2} aria-hidden="true" />{' '}
                                {v.estado === 'DEVOLVIDA' ? 'Devolvida' : 'Devolução parcial'}
                              </span>
                            ) : (
                              <span
                                className="badge"
                                style={{
                                  background: '#dcfce7',
                                  color: '#166534',
                                  border: '1px solid #86efac',
                                }}
                              >
                                <CheckCircle2 size={12} strokeWidth={2} aria-hidden="true" />{' '}
                                Concluída
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
                                <Eye size={13} strokeWidth={2} aria-hidden="true" /> Detalhes
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
                                    itens:
                                      v.itens && v.itens.length > 0
                                        ? v.itens.map((it) => ({
                                            produto: it.produto,
                                            quantidade: it.quantidade,
                                            precoUnitario: it.precoUnitario,
                                            total: it.total,
                                          }))
                                        : [
                                            {
                                              produto: v.produto || `Venda #${v.numero}`,
                                              quantidade: v.quantidade || '1',
                                              precoUnitario: v.total,
                                              total: v.total,
                                            },
                                          ],
                                    pagamentos: v.pagamentos?.map((p) => ({
                                      metodo: p.metodo,
                                      valor: p.valor,
                                      troco: p.troco,
                                    })),
                                  };
                                  imprimirReciboTexto(dados);
                                }}
                              >
                                <Printer size={13} strokeWidth={2} aria-hidden="true" /> Recibo
                              </button>
                              {!isAnulada && (
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{
                                    padding: '3px 8px',
                                    fontSize: 12,
                                    color: 'var(--color-danger)',
                                  }}
                                  onClick={() => {
                                    setMotivoAnulacao('');
                                    setVendaParaAnular(v);
                                  }}
                                >
                                  <Ban size={13} strokeWidth={2} aria-hidden="true" /> Anular
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

      {/* MODAL DE REGISTO DE VENDA (FLUXO 3 TOQUES) */}
      {showModal && (
        <Modal
          title="Registar Venda"
          onClose={closeModal}
          busy={save.pending || scan.pending}
          wide
        >
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
                          setActiveOption((index) =>
                            Math.min(index + 1, suggestions.length - 1),
                          );
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
                            onClick={() => selectProduct(p)}
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
                    onClick={() => void handleScan()}
                  >
                    {scan.pending ? (
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
                      onChange={(e) => setContinuousScan(e.target.checked)}
                    />
                    Scanner contínuo (reabre após leitura)
                  </label>
                </div>
              </div>

              {/* LISTA DO CARRINHO */}
              <div className="cart-heading">
                <h4>
                  <ShoppingCart size={17} strokeWidth={2} aria-hidden="true" /> Carrinho
                </h4>
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
                    const step = decimal(
                      ['kg', 'L', 'g', 'ml', 'm'].includes(unit) ? '0.5' : '1',
                    );
                    const calc = calcularLinha(item);
                    const temDesconto = calc.descPerc.gt(0) || calc.descVal.gt(0);

                    return (
                      <div className="cart-item" key={item.produto.id}>
                        <div className="cart-product">
                          <strong>{item.produto.nome}</strong>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            {temDesconto ? (
                              <>
                                <span style={{ textDecoration: 'line-through', color: 'var(--text-muted)' }}>
                                  {formatMoney(calc.precoUnit)}
                                </span>
                                <span style={{ fontWeight: 600, color: 'var(--color-brand)' }}>
                                  {formatMoney(calc.precoFinal)} / {unit}
                                </span>
                                <span className="cart-discount-badge">
                                  {calc.descPerc.gt(0)
                                    ? `-${calc.descPerc}%`
                                    : `-${formatMoney(calc.descVal)}`}
                                </span>
                              </>
                            ) : (
                              <span>
                                {formatMoney(calc.precoUnit)} / {unit}
                              </span>
                            )}
                            <button
                              type="button"
                              className="cart-discount-btn"
                              onClick={() => abrirDescontoModal(item)}
                              title="Adicionar ou alterar desconto neste produto"
                            >
                              <Percent size={11} /> {temDesconto ? 'Editar desc.' : 'Desconto'}
                            </button>
                          </div>
                        </div>

                        <div className="quantity-control">
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label="Diminuir quantidade"
                            onClick={() =>
                              changeQuantity(item.produto.id, item.quantidade.minus(step))
                            }
                          >
                            <Minus size={14} strokeWidth={2.5} aria-hidden="true" />
                          </button>
                          <span>{formatQuantity(item.quantidade, unit)}</span>
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label="Aumentar quantidade"
                            onClick={() =>
                              changeQuantity(item.produto.id, item.quantidade.plus(step))
                            }
                          >
                            <Plus size={14} strokeWidth={2.5} aria-hidden="true" />
                          </button>
                        </div>

                        <strong className="cart-subtotal numeric">
                          {formatMoney(calc.subtotal)}
                        </strong>

                        <button
                          type="button"
                          className="icon-btn delete cart-remove"
                          aria-label="Remover do carrinho"
                          onClick={() =>
                            setCarrinho((prev) =>
                              prev.filter((e) => e.produto.id !== item.produto.id),
                            )
                          }
                        >
                          <Trash2 size={15} strokeWidth={2} aria-hidden="true" />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* SEÇÃO DE PAGAMENTO (RECEBER EM 3 TOQUES) */}
              <div
                style={{
                  marginTop: 24,
                  borderTop: '2px solid var(--border-color)',
                  paddingTop: 16,
                }}
              >
                {/* Total em destaque grande */}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'baseline',
                    marginBottom: 16,
                    padding: '12px 16px',
                    borderRadius: 8,
                    background: 'var(--bg-subtle)',
                  }}
                >
                  <span style={{ fontSize: 15, fontWeight: 600 }}>Total a pagar:</span>
                  <strong
                    style={{
                      fontSize: 32,
                      color: 'var(--color-brand)',
                      letterSpacing: '-0.5px',
                    }}
                  >
                    {formatMoney(total)}
                  </strong>
                </div>

                {/* BOTÕES TOUCH DE MÉTODOS ACTIVOS */}
                <span
                  style={{
                    display: 'block',
                    fontSize: 13,
                    fontWeight: 600,
                    marginBottom: 8,
                  }}
                >
                  Forma de pagamento rápida:
                </span>
                <div className="payment-methods-grid">
                  {metodosDisponiveis.map((m) => {
                    const isSelected =
                      pagamentos[0]?.metodo.toUpperCase() === m.nome.toUpperCase() ||
                      (m.tipo === 'FIADO' && pagamentos[0]?.metodo.toUpperCase() === 'FIADO');
                    return (
                      <button
                        key={m.id}
                        type="button"
                        className={`payment-method-tile ${isSelected ? 'selected' : ''}`}
                        onClick={() => selecionarMetodoPrincipal(m)}
                      >
                        <CreditCard size={18} />
                        <span>{m.nome}</span>
                      </button>
                    );
                  })}
                </div>

                {/* ATALHOS DE NOTAS PARA DINHEIRO */}
                {temDinheiro && (
                  <div>
                    <span
                      style={{
                        display: 'block',
                        fontSize: 12,
                        color: 'var(--text-secondary)',
                        marginBottom: 4,
                      }}
                    >
                      Notas recebidas (atalho):
                    </span>
                    <div className="quick-cash-row">
                      <button
                        type="button"
                        className="quick-cash-btn"
                        onClick={() => updateLinhaPagamento(pagamentos[0].id, 'valor', total.toFixed(2))}
                      >
                        Exato ({formatMoney(total)})
                      </button>
                      {[50, 100, 200, 500, 1000, 2000].map((valorNota) => (
                        <button
                          key={valorNota}
                          type="button"
                          className="quick-cash-btn"
                          onClick={() =>
                            updateLinhaPagamento(pagamentos[0].id, 'valor', valorNota.toString())
                          }
                        >
                          {valorNota} MT
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* LINHAS DE PAGAMENTO DETALHADAS (DIVIDIR PAGAMENTO) */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '14px 0 8px' }}>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>Divisão do pagamento:</span>
                  <button
                    type="button"
                    className="btn-secondary"
                    style={{ fontSize: 12, padding: '3px 8px' }}
                    onClick={addLinhaPagamento}
                  >
                    <Plus size={13} /> Dividir
                  </button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {pagamentos.map((p, idx) => (
                    <div key={p.id} className="sale-payment-split-row">
                      <Field id={`metodo-${p.id}`} label={`Forma ${idx + 1}`}>
                        <select
                          id={`metodo-${p.id}`}
                          value={p.metodo}
                          onChange={(e) => updateLinhaPagamento(p.id, 'metodo', e.target.value)}
                        >
                          {metodosDisponiveis.map((m) => (
                            <option
                              key={m.id}
                              value={m.tipo === 'FIADO' ? 'FIADO' : m.nome.toUpperCase()}
                            >
                              {m.nome}
                            </option>
                          ))}
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
                        aria-label="Remover linha"
                      >
                        <Trash2 size={15} strokeWidth={2} aria-hidden="true" />
                      </button>

                      {/* Campo opcional de referência para pagamentos digitais */}
                      {p.metodo !== 'DINHEIRO' && p.metodo !== 'FIADO' && (
                        <div style={{ gridColumn: '1 / -1', marginTop: -4 }}>
                          <input
                            type="text"
                            placeholder="Código / referência da transação (opcional)"
                            value={p.referencia || ''}
                            onChange={(e) =>
                              updateLinhaPagamento(p.id, 'referencia', e.target.value)
                            }
                            style={{
                              width: '100%',
                              padding: '6px 8px',
                              fontSize: 12,
                              borderRadius: 6,
                              border: '1px solid var(--border-color)',
                            }}
                          />
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                {/* CAMPO DE CLIENTE CASO TENHA FIADO */}
                {temFiado && (
                  <div
                    style={{
                      marginTop: 16,
                      padding: 12,
                      background: 'var(--bg-subtle)',
                      borderRadius: 8,
                      border: '1px solid var(--border-color)',
                    }}
                  >
                    <label
                      htmlFor="cliente-fiado"
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                        fontWeight: 600,
                        marginBottom: 6,
                      }}
                    >
                      <User size={15} strokeWidth={2} aria-hidden="true" /> Cliente Devedor
                      (Obrigatório para Fiado):
                    </label>
                    <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                      <select
                        id="cliente-fiado"
                        value={clienteId}
                        onChange={(e) => setClienteId(e.target.value)}
                        style={{
                          flex: 1,
                          padding: 8,
                          borderRadius: 6,
                          border: '1px solid var(--input-border)',
                        }}
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
                        {criandoNovoCliente ? (
                          'Cancelar'
                        ) : (
                          <>
                            <Plus size={14} strokeWidth={2} aria-hidden="true" /> Novo
                          </>
                        )}
                      </button>
                    </div>

                    {criandoNovoCliente && (
                      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                        <input
                          type="text"
                          placeholder="Nome do novo cliente"
                          value={novoClienteNome}
                          onChange={(e) => setNovoClienteNome(e.target.value)}
                          style={{
                            flex: 1,
                            padding: 8,
                            borderRadius: 6,
                            border: '1px solid var(--input-border)',
                          }}
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

                {/* RESUMO DE VALORES E TROCO EM TEMPO REAL */}
                <div style={{ marginTop: 16 }}>
                  {trocoCalculado.gt(0) && (
                    <Notice kind="success">
                      Troco a devolver: <strong>{formatMoney(trocoCalculado)}</strong>
                    </Notice>
                  )}
                  {faltaPagar.gt(0) && !temFiado && (
                    <Notice kind="error">
                      Falta receber: <strong>{formatMoney(faltaPagar)}</strong>
                    </Notice>
                  )}
                </div>

                <div style={{ marginTop: 16 }}>
                  <label
                    htmlFor="sale-obs"
                    style={{
                      display: 'block',
                      fontSize: 13,
                      fontWeight: 600,
                      marginBottom: 4,
                    }}
                  >
                    Observações (Opcional):
                  </label>
                  <input
                    id="sale-obs"
                    type="text"
                    value={observacao}
                    onChange={(e) => setObservacao(e.target.value)}
                    placeholder="Ex: Entrega ao domicílio, cliente regular..."
                    style={{
                      width: '100%',
                      padding: 8,
                      borderRadius: 6,
                      border: '1px solid var(--border-color, #ccc)',
                    }}
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
                  disabled={
                    carrinho.length === 0 ||
                    save.pending ||
                    (!temFiado && faltaPagar.gt(0))
                  }
                  style={{ minHeight: 48, fontSize: 16 }}
                >
                  {save.pending ? (
                    <Spinner size="small" />
                  ) : (
                    <Check size={18} strokeWidth={2.2} aria-hidden="true" />
                  )}
                  {save.pending ? ' A processar…' : ' Confirmar venda'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}

      {/* MODAL DE DESCONTO POR LINHA */}
      {itemParaDesconto && (
        <Modal
          title={`Desconto — ${itemParaDesconto.produto.nome}`}
          onClose={() => setItemParaDesconto(null)}
        >
          <form onSubmit={salvarDescontoLinha}>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 16 }}>
              Preço normal: {formatMoney(itemParaDesconto.produto.preco)} /{' '}
              {itemParaDesconto.produto.unidade || 'un'}
            </p>

            <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
              <button
                type="button"
                className={modalDescTipo === 'percentual' ? 'btn-primary' : 'btn-secondary'}
                onClick={() => setModalDescTipo('percentual')}
                style={{ flex: 1 }}
              >
                Porcentagem (%)
              </button>
              <button
                type="button"
                className={modalDescTipo === 'valor' ? 'btn-primary' : 'btn-secondary'}
                onClick={() => setModalDescTipo('valor')}
                style={{ flex: 1 }}
              >
                Valor fixo (MT)
              </button>
            </div>

            {modalDescTipo === 'percentual' ? (
              <div>
                <Field id="desc-perc" label="Desconto percentual (%)">
                  <input
                    id="desc-perc"
                    type="number"
                    min="1"
                    max="100"
                    step="1"
                    placeholder="Ex: 5, 10, 20"
                    value={modalDescValor}
                    onChange={(e) => setModalDescValor(e.target.value)}
                    required
                    autoFocus
                  />
                </Field>
                <div style={{ display: 'flex', gap: 8, marginTop: 8, marginBottom: 16 }}>
                  {[5, 10, 15, 20].map((p) => (
                    <button
                      key={p}
                      type="button"
                      className="btn-secondary"
                      style={{ fontSize: 12, padding: '4px 8px' }}
                      onClick={() => setModalDescValor(p.toString())}
                    >
                      {p}%
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <Field id="desc-val" label="Abatimento em Meticais (MT)">
                <input
                  id="desc-val"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="Ex: 10.00"
                  value={modalDescValor}
                  onChange={(e) => setModalDescValor(e.target.value)}
                  required
                  autoFocus
                />
              </Field>
            )}

            <Field id="desc-nota" label="Nota do desconto (opcional)">
              <input
                id="desc-nota"
                type="text"
                placeholder="Ex: Cliente assíduo, produto com data próxima..."
                value={modalDescNota}
                onChange={(e) => setModalDescNota(e.target.value)}
              />
            </Field>

            <div className="modal-actions" style={{ marginTop: 20 }}>
              <button
                type="button"
                className="btn-secondary"
                style={{ color: '#dc2626' }}
                onClick={removerDescontoLinha}
              >
                Remover desconto
              </button>
              <button type="submit" className="btn-primary">
                Aplicar desconto
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL PARA ABRIR CAIXA DIÁRIO */}
      {showAbrirCaixaModal && (
        <Modal
          title="Abrir Caixa do Dia"
          onClose={() => setShowAbrirCaixaModal(false)}
        >
          <form onSubmit={handleAbrirCaixa}>
            <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginBottom: 16 }}>
              O controlo de caixa está ativo. Indique o montante em dinheiro na gaveta para iniciar
              o dia.
            </p>
            <Field id="caixa-abertura" label="Fundo de trocos inicial (MT) *">
              <input
                id="caixa-abertura"
                type="number"
                min="0"
                step="0.01"
                required
                autoFocus
                value={valorAberturaCaixa}
                onChange={(e) => setValorAberturaCaixa(e.target.value)}
                style={{ fontSize: 20, fontWeight: 600, padding: 10 }}
              />
            </Field>

            <div className="quick-cash-row">
              <button
                type="button"
                className="quick-cash-btn"
                onClick={() => setValorAberturaCaixa('0')}
              >
                0 MT (Sem fundo)
              </button>
              {[100, 200, 500, 1000, 2000].map((v) => (
                <button
                  key={v}
                  type="button"
                  className="quick-cash-btn"
                  onClick={() => setValorAberturaCaixa(v.toString())}
                >
                  {v} MT
                </button>
              ))}
            </div>

            <div className="modal-actions" style={{ marginTop: 20 }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowAbrirCaixaModal(false)}
              >
                Cancelar
              </button>
              <button type="submit" className="btn-primary">
                Abrir caixa
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL DE CADASTRO RÁPIDO AO LER CÓDIGO INEXISTENTE */}
      {quickCreateBarcode && (
        <Modal
          title={`Novo Produto: Código ${quickCreateBarcode}`}
          onClose={() => setQuickCreateBarcode(null)}
        >
          <form onSubmit={handleSalvarProdutoRapido}>
            <p
              style={{
                margin: '0 0 16px 0',
                fontSize: 14,
                color: 'var(--text-muted, #666)',
              }}
            >
              Este código de barras não existe no catálogo. Preencha os dados para registar e
              adicionar diretamente ao carrinho:
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
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setQuickCreateBarcode(null)}
              >
                Cancelar
              </button>
              <button type="submit" className="btn-primary">
                <Save size={15} strokeWidth={2} aria-hidden="true" /> Guardar e Adicionar
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL DE RECIBO / PÓS-VENDA CONCLUÍDA */}
      {reciboSucesso && (
        <Modal
          title={`Venda #${reciboSucesso.numero} Concluída!`}
          onClose={() => setReciboSucesso(null)}
        >
          <div
            style={{
              padding: 12,
              background: 'var(--bg-subtle)',
              borderRadius: 8,
              marginBottom: 16,
            }}
          >
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

          <div
            style={{
              display: 'flex',
              gap: 8,
              flexWrap: 'wrap',
              justifyContent: 'center',
            }}
          >
            <button
              type="button"
              className="btn-primary"
              onClick={() => imprimirReciboTexto(reciboSucesso)}
            >
              <Printer size={15} strokeWidth={2} aria-hidden="true" /> Imprimir Recibo
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => partilharReciboWhatsApp(reciboSucesso)}
            >
              <Share2 size={15} strokeWidth={2} aria-hidden="true" /> Enviar por WhatsApp
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => {
                void navigator.clipboard.writeText(gerarTextoRecibo(reciboSucesso));
                toast('Recibo copiado para a área de transferência.');
              }}
            >
              <Copy size={15} strokeWidth={2} aria-hidden="true" /> Copiar Texto
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
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: 12,
              marginBottom: 16,
            }}
          >
            <div>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                Data e Hora:
              </span>
              <strong style={{ display: 'block' }}>
                {vendaDetalhes.data} {vendaDetalhes.hora}
              </strong>
            </div>
            <div>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Estado:</span>
              <div>
                {vendaDetalhes.estado === 'ANULADA' ? (
                  <span className="badge" style={{ background: '#fee2e2', color: '#991b1b' }}>
                    <XCircle size={13} strokeWidth={2} aria-hidden="true" /> ANULADA
                  </span>
                ) : vendaDetalhes.estado === 'DEVOLVIDA' ||
                  vendaDetalhes.estado === 'PARCIALMENTE_DEVOLVIDA' ? (
                  <span className="badge" style={{ background: '#fef3c7', color: '#92400e' }}>
                    <RotateCcw size={13} strokeWidth={2} aria-hidden="true" />{' '}
                    {vendaDetalhes.estado}
                  </span>
                ) : (
                  <span className="badge" style={{ background: '#dcfce7', color: '#166534' }}>
                    <CheckCircle2 size={13} strokeWidth={2} aria-hidden="true" /> CONCLUÍDA
                  </span>
                )}
              </div>
            </div>
            <div>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                Total da Venda:
              </span>
              <strong
                style={{
                  display: 'block',
                  fontSize: 18,
                  color: 'var(--primary, #1e40af)',
                }}
              >
                {formatMoney(vendaDetalhes.total)}
              </strong>
            </div>
            {vendaDetalhes.lucro && (
              <div>
                <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                  Lucro Bruto:
                </span>
                <strong style={{ display: 'block', color: '#16a34a' }}>
                  {formatMoney(vendaDetalhes.lucro)}
                </strong>
              </div>
            )}
          </div>

          {vendaDetalhes.motivoAnulacao && (
            <div
              style={{
                padding: 12,
                background: '#fee2e2',
                borderRadius: 8,
                marginBottom: 16,
                color: '#991b1b',
              }}
            >
              <strong>Motivo da Anulação:</strong> {vendaDetalhes.motivoAnulacao}
              {vendaDetalhes.anuladaEm && (
                <div style={{ fontSize: 12, marginTop: 4 }}>
                  Em: {vendaDetalhes.anuladaEm.replace('T', ' ').slice(0, 16)}
                </div>
              )}
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
                    <td>
                      <strong>{it.produto}</strong>
                      {it.notaDesconto && (
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                          Desc: {it.notaDesconto}
                        </div>
                      )}
                    </td>
                    <td className="numeric font-mono">
                      {formatQuantity(it.quantidade, '')}
                    </td>
                    <td className="numeric font-mono">
                      {formatMoney(it.precoUnitario)}
                    </td>
                    <td className="numeric font-mono" style={{ fontWeight: 600 }}>
                      {formatMoney(it.total)}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td>{vendaDetalhes.produto || `Venda #${vendaDetalhes.numero}`}</td>
                  <td className="numeric font-mono">
                    {formatQuantity(vendaDetalhes.quantidade || '1', '')}
                  </td>
                  <td className="numeric font-mono">{formatMoney(vendaDetalhes.total)}</td>
                  <td className="numeric font-mono" style={{ fontWeight: 600 }}>
                    {formatMoney(vendaDetalhes.total)}
                  </td>
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
                    <th>Referência</th>
                  </tr>
                </thead>
                <tbody>
                  {vendaDetalhes.pagamentos.map((p) => (
                    <tr key={p.id}>
                      <td>
                        <strong>{formatarMetodoPagamento(p.metodo)}</strong>
                      </td>
                      <td className="numeric font-mono">{formatMoney(p.valor)}</td>
                      <td className="numeric font-mono">{formatMoney(p.troco || '0.00')}</td>
                      <td style={{ fontSize: 12 }}>{p.referencia || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          <div className="modal-actions" style={{ marginTop: 20 }}>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setVendaDetalhes(null)}
            >
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
                  itens:
                    vendaDetalhes.itens && vendaDetalhes.itens.length > 0
                      ? vendaDetalhes.itens.map((it) => ({
                          produto: it.produto,
                          quantidade: it.quantidade,
                          precoUnitario: it.precoUnitario,
                          total: it.total,
                        }))
                      : [
                          {
                            produto: vendaDetalhes.produto || `Venda #${vendaDetalhes.numero}`,
                            quantidade: vendaDetalhes.quantidade || '1',
                            precoUnitario: vendaDetalhes.total,
                            total: vendaDetalhes.total,
                          },
                        ],
                  pagamentos: vendaDetalhes.pagamentos?.map((p) => ({
                    metodo: p.metodo,
                    valor: p.valor,
                    troco: p.troco,
                  })),
                };
                imprimirReciboTexto(dados);
              }}
            >
              <Printer size={15} strokeWidth={2} aria-hidden="true" /> Imprimir Recibo
            </button>

            {/* BOTÃO DEVOLVER PRODUTOS */}
            {vendaDetalhes.estado !== 'ANULADA' &&
              vendaDetalhes.estado !== 'DEVOLVIDA' && (
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => iniciarDevolucao(vendaDetalhes)}
                >
                  <RotateCcw size={15} strokeWidth={2} aria-hidden="true" /> Devolver produtos
                </button>
              )}

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
                <Ban size={15} strokeWidth={2} aria-hidden="true" /> Anular
              </button>
            )}
          </div>
        </Modal>
      )}

      {/* MODAL DE DEVOLUÇÃO SIMPLES */}
      {vendaParaDevolver && (
        <Modal
          title={`Devolver Produtos — Venda #${vendaParaDevolver.numero}`}
          onClose={() => setVendaParaDevolver(null)}
          busy={devolvendo}
          wide
        >
          <form onSubmit={handleConfirmarDevolucao}>
            <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginBottom: 16 }}>
              Indique quais produtos e quantidades deseja devolver ao stock. O dinheiro será
              reembolsado ou abatido na conta do cliente.
            </p>

            <Field id="dev-motivo" label="Motivo da devolução *">
              <select
                id="dev-motivo"
                value={motivoDevolucao}
                onChange={(e) => setMotivoDevolucao(e.target.value)}
                required
              >
                <option value="Engano na venda">Engano na venda</option>
                <option value="Produto estragado">Produto estragado</option>
                <option value="Cliente desistiu">Cliente desistiu</option>
                <option value="Troca">Troca</option>
              </select>
            </Field>

            <div style={{ marginTop: 16, marginBottom: 16 }}>
              <span style={{ fontSize: 13, fontWeight: 600 }}>Quantidades a devolver:</span>
              <table className="responsive-table" style={{ marginTop: 8 }}>
                <thead>
                  <tr>
                    <th>Produto</th>
                    <th className="numeric">Qtd Vendida</th>
                    <th className="numeric">Devolver agora</th>
                  </tr>
                </thead>
                <tbody>
                  {vendaParaDevolver.itens?.map((it) => {
                    const atual =
                      itensDevolucao.find((d) => d.itemVendaId === it.id)?.quantidade || '';
                    return (
                      <tr key={it.id}>
                        <td>
                          <strong>{it.produto}</strong>
                        </td>
                        <td className="numeric font-mono">{formatQuantity(it.quantidade, '')}</td>
                        <td className="numeric">
                          <input
                            type="number"
                            min="0"
                            max={it.quantidade}
                            step="0.001"
                            placeholder="0"
                            value={atual}
                            onChange={(e) =>
                              setItensDevolucao((prev) =>
                                prev.map((d) =>
                                  d.itemVendaId === it.id
                                    ? { ...d, quantidade: e.target.value }
                                    : d,
                                ),
                              )
                            }
                            style={{
                              width: 100,
                              textAlign: 'right',
                              padding: '4px 8px',
                              borderRadius: 4,
                              border: '1px solid var(--border-color)',
                            }}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="modal-actions" style={{ marginTop: 20 }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setVendaParaDevolver(null)}
                disabled={devolvendo}
              >
                Cancelar
              </button>
              <button type="submit" className="btn-primary" disabled={devolvendo}>
                {devolvendo ? <Spinner size="small" /> : 'Confirmar devolução'}
              </button>
            </div>
          </form>
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
              Ao anular a venda, as quantidades serão devolvidas ao stock e a dívida (se houver)
              será cancelada.
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
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setVendaParaAnular(null)}
              >
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
      {showFechoModal && (
        <FechoCaixaModal
          onClose={() => setShowFechoModal(false)}
          onSessaoFechada={() => void reload()}
        />
      )}
    </div>
  );
}
