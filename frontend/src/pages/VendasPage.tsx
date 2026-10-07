import { useToast } from '../utils/toast';
import { useEffect, useRef, useState } from 'react';
import { apiFetch, apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { abrirScanner } from '../utils/scanner';
import { notificarVendaRegistada } from '../utils/notificacoes';
import { decimal, formatMoney, formatQuantity, parseDecimalInput } from '../utils/decimal';
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
}
interface Venda {
  id: number;
  produto: string;
  quantidade: string;
  total: string;
  data: string;
}
interface ItemCarrinho {
  produto: Produto;
  quantidade: Decimal;
}
const loadSales = async (signal: AbortSignal) => {
  const [vendas, produtos] = await Promise.all([
    apiRequest<Venda[]>('/api/vendas', { signal }),
    apiRequest<Produto[]>('/api/produtos', { signal }),
  ]);
  return { vendas, produtos };
};

export default function VendasPage() {
  const { data, loading, error, reload } = useResource(loadSales);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [carrinho, setCarrinho] = useState<ItemCarrinho[]>([]);
  const [busca, setBusca] = useState('');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [activeOption, setActiveOption] = useState(-1);
  const [selectedProd, setSelectedProd] = useState<Produto | null>(null);
  const [quantity, setQuantity] = useState('1');
  const [amount, setAmount] = useState('');
  const [cartError, setCartError] = useState<string | null>(null);
  const [scanMessage, setScanMessage] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const suggestionsRef = useRef<HTMLUListElement>(null);
  const save = useMutation();
  const scan = useMutation();
  const toast = useToast();
  const vendas = data?.vendas || [];
  const produtos = data?.produtos || [];
  const filteredSales = vendas.filter((v) =>
    `${v.produto} ${v.data}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  );
  const suggestions = produtos
    .filter((p) => p.nome.toLocaleLowerCase().includes(busca.trim().toLocaleLowerCase()))
    .slice(0, 30);
  const total = carrinho.reduce(
    (sum, item) => sum.plus(decimal(item.produto.preco).times(item.quantidade)),
    decimal(0),
  );
  const paid = parseDecimalInput(amount);
  const change = paid && paid.gt(0) ? paid.minus(total) : null;
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
  const addProduct = (product: Produto, qty: Decimal) => {
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
    setCarrinho((previous) =>
      existing
        ? previous.map((item) =>
            item.produto.id === product.id
              ? { ...item, quantidade: item.quantidade.plus(qty) }
              : item,
          )
        : [...previous, { produto: product, quantidade: qty }],
    );
    setCartError(null);
    return true;
  };
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
    setAmount('');
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
    setAmount('');
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
  const handleScan = () =>
    void scan.run(async () => {
      setScanMessage(null);
      setCartError(null);
      const code = await abrirScanner();
      if (!code) return;
      const response = await apiFetch(`/api/produtos/barcode/${encodeURIComponent(code)}`);
      if (response.status === 404)
        throw new Error(
          `O código ${code} não está associado a um produto. Adicione-o na página Produtos.`,
        );
      if (!response.ok) throw new Error('Não foi possível consultar este código. Tente novamente.');
      const product: Produto = await response.json();
      const existing = carrinho.some((item) => item.produto.id === product.id);
      const step = decimal(
        existing && ['kg', 'g', 'L', 'ml'].includes(product.unidade) ? '0.5' : '1',
      );
      if (addProduct(product, step)) setScanMessage(`${product.nome} adicionado ao carrinho.`);
    });
  const finishSale = (event: React.FormEvent) => {
    event.preventDefault();
    if (carrinho.length === 0) return;
    void save.run(async () => {
      const result = await apiRequest<{ itens: number; total: string }>('/api/vendas/lote', {
        method: 'POST',
        body: JSON.stringify(
          carrinho.map((item) => ({
            produtoId: item.produto.id,
            quantidade: item.quantidade.toFixed(3),
          })),
        ),
      });
      setShowModal(false);
      resetCart();
      toast(`Venda registada: ${formatMoney(result.total)}.`);
      void reload();
      // Uma notificação indisponível não pode transformar uma venda concluída numa falha.
      void notificarVendaRegistada(`${result.itens} produtos`, result.total).catch(() => {});
    });
  };

  return (
    <div>
      <PageHeading
        title="Vendas"
        description="Do carrinho ao troco. Registe cada venda com clareza."
      >
        <button className="btn-primary" disabled={!data} onClick={() => setShowModal(true)}>
          <span aria-hidden="true">➕</span> Registar venda
        </button>
      </PageHeading>
      <div className="toolbar">
        <SearchField value={search} onChange={setSearch} label="Pesquisar por produto ou data" />
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
                description="Pesquise por outro produto ou data."
                icon="🔍"
              >
                <button className="btn-secondary" onClick={() => setSearch('')}>
                  🔄 Limpar pesquisa
                </button>
              </EmptyState>
            ) : (
              <div className="table-wrapper">
                <table className="responsive-table">
                  <caption className="sr-only">Histórico de vendas</caption>
                  <thead>
                    <tr>
                      <th scope="col">Produto</th>
                      <th scope="col" className="numeric">
                        Quantidade
                      </th>
                      <th scope="col" className="numeric">
                        Total
                      </th>
                      <th scope="col">Data</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSales.map((v) => (
                      <tr key={v.id}>
                        <td data-label="Produto" className="cell-name">
                          {v.produto}
                        </td>
                        <td data-label="Quantidade" className="numeric">
                          {formatQuantity(v.quantidade, '')}
                        </td>
                        <td data-label="Total" className="numeric">
                          {formatMoney(v.total)}
                        </td>
                        <td data-label="Data" className="cell-secondary">
                          {v.data}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )
      )}
      {showModal && (
        <Modal title="Registar venda" onClose={closeModal} busy={save.pending || scan.pending} wide>
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
                          event.stopPropagation();
                          setShowSuggestions(false);
                        }
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          if (showSuggestions && activeOption >= 0 && suggestions[activeOption])
                            selectProduct(suggestions[activeOption]);
                          else if (selectedProd) addSelected();
                        }
                      }}
                    />
                  </div>
                  {showSuggestions && (
                    <ul
                      id="sale-suggestions"
                      ref={suggestionsRef}
                      role="listbox"
                      aria-label="Produtos disponíveis"
                      className="product-suggestions"
                    >
                      {suggestions.length === 0 ? (
                        <li
                          className="suggestion-empty"
                          role="option"
                          aria-disabled="true"
                          aria-selected="false"
                        >
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
                  label={
                    selectedProd ? `Quantidade (${selectedProd.unidade || 'un'})` : 'Quantidade'
                  }
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
                <button type="button" className="btn-secondary sale-scan" onClick={handleScan}>
                  {scan.pending ? <Spinner size="small" /> : <span aria-hidden="true">📷</span>} Ler código
                </button>
              </div>
              <div className="cart-heading">
                <h4>🛒 Carrinho</h4>
                <span>
                  {carrinho.length} produto{carrinho.length === 1 ? '' : 's'}
                </span>
              </div>
              {carrinho.length === 0 ? (
                <div className="cart-empty">
                  <span className="cart-empty-emoji" aria-hidden="true">
                    🛒
                  </span>
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
                          <span>
                            {formatMoney(item.produto.preco)} / {unit}
                          </span>
                        </div>
                        <div className="quantity-control">
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label={`Diminuir quantidade de ${item.produto.nome}`}
                            onClick={() =>
                              changeQuantity(item.produto.id, item.quantidade.minus(step))
                            }
                          >
                            ➖
                          </button>
                          <span>{formatQuantity(item.quantidade, unit)}</span>
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label={`Aumentar quantidade de ${item.produto.nome}`}
                            onClick={() =>
                              changeQuantity(item.produto.id, item.quantidade.plus(step))
                            }
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
                          aria-label={`Remover ${item.produto.nome} do carrinho`}
                          onClick={() =>
                            setCarrinho((previous) =>
                              previous.filter((entry) => entry.produto.id !== item.produto.id),
                            )
                          }
                        >
                          🗑️
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
              <div className="sale-payment">
                <div className="sale-total">
                  <span>Total a pagar</span>
                  <strong>{formatMoney(total)}</strong>
                </div>
                {carrinho.length > 0 && (
                  <>
                    <Field
                      id="sale-paid"
                      label="Valor entregue pelo cliente (MT)"
                      hint="Opcional. Serve apenas para calcular o troco."
                    >
                      <input
                        id="sale-paid"
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        placeholder="0,00"
                        aria-describedby="sale-paid-hint"
                      />
                    </Field>
                    {change !== null && (
                      <Notice kind={change.gte(0) ? 'success' : 'error'}>
                        {change.gte(0)
                          ? `Troco: ${formatMoney(change)}`
                          : `Falta receber ${formatMoney(change.abs())}`}
                      </Notice>
                    )}
                  </>
                )}
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={closeModal}>
                  Cancelar
                </button>
                <button className="btn-primary" type="submit" disabled={carrinho.length === 0}>
                  {save.pending ? <Spinner size="small" /> : <span aria-hidden="true">✅</span>}
                  {save.pending ? ' A registar…' : ' Confirmar venda'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}
    </div>
  );
}
