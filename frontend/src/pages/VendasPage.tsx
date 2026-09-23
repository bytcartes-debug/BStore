import { apiFetch } from '../utils/api';
import React, { useEffect, useState, useRef } from 'react';
import { Plus, X, Search, Trash2, ShoppingCart, ScanLine, Loader2 } from 'lucide-react';
import { abrirScanner } from '../utils/scanner';

interface Produto { id: number; nome: string; preco: number; stock: number; unidade: string; }
interface Venda   { id: number; produto: string; quantidade: number; total: number; data: string; }
interface ItemCarrinho { produto: Produto; quantidade: number; }

const VendasPage: React.FC = () => {
  const [vendas, setVendas]         = useState<Venda[]>([]);
  const [produtos, setProdutos]     = useState<Produto[]>([]);
  const [showModal, setShowModal]   = useState(false);

  // Carrinho
  const [carrinho, setCarrinho]     = useState<ItemCarrinho[]>([]);
  const [busca, setBusca]           = useState('');
  const [showSugestoes, setShowSugestoes] = useState(false);
  const [selectedProd, setSelectedProd]   = useState<Produto | null>(null);
  const [qtdAtual, setQtdAtual]     = useState('1');
  const buscaRef = useRef<HTMLDivElement>(null);

  // Troco
  const [valorEntregue, setValorEntregue] = useState('');

  // Estado do botão
  const [loading, setLoading]       = useState(false);
  const [erro, setErro]             = useState<string | null>(null);
  const [scanning, setScanning]     = useState(false);
  const [scanMsg, setScanMsg]       = useState<string | null>(null);

  const load = () => {
    apiFetch('/api/vendas').then(r => r.json()).then(setVendas).catch(() => {});
    apiFetch('/api/produtos').then(r => r.json()).then(setProdutos).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  // Fechar sugestões ao clicar fora
  useEffect(() => {
    const handle = (e: MouseEvent) => {
      if (buscaRef.current && !buscaRef.current.contains(e.target as Node))
        setShowSugestoes(false);
    };
    document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, []);

  const totalCarrinho = carrinho.reduce((s, i) => s + i.produto.preco * i.quantidade, 0);

  // Troco calculado automaticamente
  const trocoCalculado = (() => {
    const v = parseFloat(valorEntregue);
    if (isNaN(v) || valorEntregue === '' || v <= 0) return null;
    return v - totalCarrinho;
  })();

  const handleSelectProduto = (p: Produto) => {
    setSelectedProd(p);
    setBusca(p.nome);
    setShowSugestoes(false);
    setQtdAtual('1');
  };

  const handleAdicionarAoCarrinho = () => {
    if (!selectedProd) return;
    const qtd = parseFloat(qtdAtual) || 1;
    if (qtd <= 0) return;

    // Se já está no carrinho, soma a quantidade
    setCarrinho(prev => {
      const existente = prev.find(i => i.produto.id === selectedProd.id);
      if (existente) {
        return prev.map(i => i.produto.id === selectedProd.id
          ? { ...i, quantidade: i.quantidade + qtd }
          : i);
      }
      return [...prev, { produto: selectedProd, quantidade: qtd }];
    });

    // Limpar pesquisa
    setBusca('');
    setSelectedProd(null);
    setQtdAtual('1');
    setValorEntregue('');
  };

  const handleRemoverItem = (produtoId: number) => {
    setCarrinho(prev => prev.filter(i => i.produto.id !== produtoId));
  };

  const handleAlterarQtd = (produtoId: number, novaQtd: number) => {
    if (novaQtd <= 0) { handleRemoverItem(produtoId); return; }
    setCarrinho(prev => prev.map(i => i.produto.id === produtoId ? { ...i, quantidade: novaQtd } : i));
  };

  const handleFinalizarVenda = async () => {
    if (carrinho.length === 0) return;
    setLoading(true);
    setErro(null);
    try {
      const itens = carrinho.map(i => ({ produtoId: i.produto.id, quantidade: i.quantidade }));
      const r = await apiFetch('/api/vendas/lote', {
        method: 'POST',
        body: JSON.stringify(itens),
      });
      if (!r.ok) {
        const d = await r.json().catch(() => ({}));
        throw new Error(d.erro || `Erro ${r.status}`);
      }
      const resultado = await r.json();

      // Notificação
      const { notificarVendaRegistada } = await import('../utils/notificacoes');
      await notificarVendaRegistada(`${resultado.itens} produtos`, resultado.total);

      setShowModal(false);
      setCarrinho([]);
      setBusca('');
      setSelectedProd(null);
      setQtdAtual('1');
      setValorEntregue('');
      load();
    } catch (e: any) {
      setErro(e.message || 'Erro ao registar venda.');
    } finally {
      setLoading(false);
    }
  };

  const handleFecharModal = () => {
    setShowModal(false);
    setCarrinho([]);
    setBusca('');
    setSelectedProd(null);
    setQtdAtual('1');
    setValorEntregue('');
    setErro(null);
    setScanMsg(null);
  };

  /** Scan na venda: lê código → procura na BD → adiciona ao carrinho */
  const handleScanVenda = async () => {
    setScanning(true); setScanMsg(null);
    try {
      const codigo = await abrirScanner();
      if (!codigo) return;

      const r = await apiFetch(`/api/produtos/barcode/${encodeURIComponent(codigo)}`);
      if (!r.ok) {
        setScanMsg(`⚠️ Produto com código "${codigo}" não está cadastrado.`);
        setTimeout(() => setScanMsg(null), 4000);
        return;
      }
      const p: Produto = await r.json();

      // Adicionar ao carrinho (ou incrementar se já existe)
      setCarrinho(prev => {
        const existente = prev.find(i => i.produto.id === p.id);
        if (existente) {
          const step = ['kg','g','L','ml'].includes(p.unidade) ? 0.5 : 1;
          return prev.map(i => i.produto.id === p.id ? { ...i, quantidade: i.quantidade + step } : i);
        }
        return [...prev, { produto: p, quantidade: 1 }];
      });

      setScanMsg(`✅ "${p.nome}" adicionado ao carrinho.`);
      setTimeout(() => setScanMsg(null), 2500);
    } finally {
      setScanning(false);
    }
  };

  const produtosFiltrados = produtos.filter(p =>
    p.nome.toLowerCase().includes(busca.toLowerCase())
  );

  return (
    <div>
      {/* Header */}
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:20, flexWrap:'wrap', gap:12 }}>
        <div>
          <h2 style={{ fontSize:22, fontWeight:700, color:'var(--text-primary)' }}>Vendas</h2>
          <p style={{ fontSize:14, color:'var(--text-secondary)', marginTop:4 }}>{vendas.length} venda(s) registada(s)</p>
        </div>
        <button className="btn-primary" onClick={() => setShowModal(true)}>
          <ShoppingCart size={18} /> Nova Venda
        </button>
      </div>

      {/* Tabela de vendas */}
      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr><th>Produto</th><th>Qtd</th><th>Total (MT)</th><th>Data</th></tr>
            </thead>
            <tbody>
              {vendas.length === 0
                ? <tr><td colSpan={4} style={{ textAlign:'center', color:'var(--text-secondary)', padding:32 }}>Nenhuma venda registada.</td></tr>
                : vendas.map(v => (
                  <tr key={v.id}>
                    <td style={{ fontWeight:600 }}>{v.produto}</td>
                    <td>{v.quantidade}</td>
                    <td>MT {v.total.toFixed(2)}</td>
                    <td style={{ color:'var(--text-secondary)' }}>{v.data}</td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal de Venda com Carrinho */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container" style={{ maxWidth: 540 }}>
            <div className="modal-header">
              <h3>🛒 Nova Venda</h3>
              <button className="modal-close-btn" onClick={handleFecharModal}><X size={20} /></button>
            </div>

            {erro && (
              <p style={{ color:'var(--color-danger)', fontSize:13, background:'rgba(239,68,68,0.1)', borderRadius:6, padding:'8px 10px', marginBottom:8 }}>
                ⚠️ {erro}
              </p>
            )}

            {/* Mensagem de scan */}
            {scanMsg && (
              <div style={{ fontSize:13, padding:'8px 12px', borderRadius:6, marginBottom:8,
                background: scanMsg.startsWith('✅') ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.1)',
                color: scanMsg.startsWith('✅') ? 'var(--color-brand)' : 'var(--color-danger)' }}>
                {scanMsg}
              </div>
            )}

            {/* Pesquisa de produto */}
            <div className="form-group" ref={buscaRef} style={{ position:'relative' }}>
              <label>Adicionar Produto</label>
              <div style={{ display:'flex', gap:8 }}>
                <div style={{ position:'relative', flex:1 }}>
                  <Search size={16} style={{ position:'absolute', left:10, top:'50%', transform:'translateY(-50%)', color:'var(--text-muted)' }} />
                  <input
                    value={busca}
                    onChange={e => { setBusca(e.target.value); setShowSugestoes(true); setSelectedProd(null); }}
                    onFocus={() => setShowSugestoes(true)}
                    placeholder="🔍 Pesquisar produto..."
                    style={{ paddingLeft:34, width:'100%' }}
                  />
                </div>
                {/* Botão Scan */}
                <button
                  className="btn-secondary"
                  onClick={handleScanVenda}
                  disabled={scanning}
                  title="Scan de código de barras"
                  style={{ display:'flex', alignItems:'center', gap:6, whiteSpace:'nowrap', padding:'0 12px' }}
                >
                  {scanning ? <Loader2 size={16} className="spin" /> : <ScanLine size={16} />}
                  {scanning ? '' : 'Scan'}
                </button>
                <div style={{ display:'flex', alignItems:'center', gap:4 }}>
                  <input
                    type="number" min="0.001" step="0.001" value={qtdAtual}
                    onChange={e => setQtdAtual(e.target.value)}
                    style={{ width:75, textAlign:'center' }}
                    placeholder="Qtd"
                  />
                  {selectedProd && (
                    <span style={{ fontSize:12, color:'var(--text-secondary)', whiteSpace:'nowrap' }}>
                      {selectedProd.unidade || 'un'}
                    </span>
                  )}
                </div>
                <button
                  className="btn-primary"
                  onClick={handleAdicionarAoCarrinho}
                  disabled={!selectedProd}
                  style={{ whiteSpace:'nowrap', padding:'0 12px' }}
                  title="Adicionar ao carrinho"
                >
                  <Plus size={18} />
                </button>
              </div>

              {/* Sugestões */}
              {showSugestoes && busca.length > 0 && (
                <div style={{
                  position:'absolute', top:'100%', left:0, right:0, zIndex:100,
                  background:'var(--bg-card)', border:'1px solid var(--border-color)',
                  borderRadius:8, boxShadow:'0 8px 24px rgba(0,0,0,0.3)',
                  maxHeight:200, overflowY:'auto',
                }}>
                  {produtosFiltrados.length === 0
                    ? <div style={{ padding:'12px 16px', color:'var(--text-muted)', fontSize:13 }}>Nenhum produto encontrado</div>
                    : produtosFiltrados.map(p => (
                      <div
                        key={p.id}
                        onClick={() => handleSelectProduto(p)}
                        style={{
                          padding:'10px 16px', cursor:'pointer',
                          borderBottom:'1px solid var(--border-color)',
                          display:'flex', justifyContent:'space-between', alignItems:'center',
                        }}
                        onMouseEnter={e => (e.currentTarget.style.background = 'var(--color-brand-light)')}
                        onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                      >
                        <span style={{ fontWeight:600, color:'var(--text-primary)' }}>{p.nome}</span>
                        <span style={{ fontSize:12, color:'var(--text-secondary)' }}>
                          MT {p.preco.toFixed(2)} · {p.stock} {p.unidade || 'un'}
                        </span>
                      </div>
                    ))
                  }
                </div>
              )}
            </div>

            {/* Carrinho */}
            {carrinho.length > 0 && (
              <div style={{ marginBottom:16 }}>
                <p style={{ fontSize:13, fontWeight:600, color:'var(--text-secondary)', marginBottom:8 }}>
                  🛒 Carrinho ({carrinho.length} produto{carrinho.length !== 1 ? 's' : ''})
                </p>
                <div style={{ border:'1px solid var(--border-color)', borderRadius:8, overflow:'hidden' }}>
                  {carrinho.map((item, idx) => {
                    const u = item.produto.unidade || 'un';
                    const step = ['kg','L','g','ml','m'].includes(u) ? 0.5 : 1;
                    const fmtQtd = Number.isInteger(item.quantidade) ? `${item.quantidade}` : `${item.quantidade}`;
                    return (
                      <div key={item.produto.id} style={{
                        display:'flex', alignItems:'center', gap:8,
                        padding:'10px 12px',
                        borderBottom: idx < carrinho.length - 1 ? '1px solid var(--border-color)' : 'none',
                        background: 'var(--bg-card)',
                      }}>
                        <span style={{ flex:1, fontWeight:600, fontSize:14, color:'var(--text-primary)' }}>
                          {item.produto.nome}
                        </span>
                        {/* Ajustar quantidade */}
                        <button
                          onClick={() => handleAlterarQtd(item.produto.id, Math.max(0, +(item.quantidade - step).toFixed(3)))}
                          style={{ width:28, height:28, borderRadius:6, border:'1px solid var(--border-color)', background:'transparent', cursor:'pointer', color:'var(--text-primary)', fontSize:16 }}
                        >−</button>
                        <span style={{ minWidth:52, textAlign:'center', fontWeight:700, fontSize:13 }}>
                          {fmtQtd} {u}
                        </span>
                        <button
                          onClick={() => handleAlterarQtd(item.produto.id, +(item.quantidade + step).toFixed(3))}
                          style={{ width:28, height:28, borderRadius:6, border:'1px solid var(--border-color)', background:'transparent', cursor:'pointer', color:'var(--text-primary)', fontSize:16 }}
                        >+</button>
                        <span style={{ minWidth:84, textAlign:'right', color:'var(--color-brand)', fontWeight:700, fontSize:13 }}>
                          MT {(item.produto.preco * item.quantidade).toFixed(2)}
                        </span>
                        <button className="icon-btn delete" onClick={() => handleRemoverItem(item.produto.id)} style={{ marginLeft:4 }}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    );
                  })}
                </div>

                {/* Total */}
                <div style={{
                  display:'flex', justifyContent:'space-between', alignItems:'center',
                  padding:'12px 14px', background:'var(--color-brand-light)',
                  border:'1px solid var(--color-brand)', borderRadius:8, marginTop:8,
                }}>
                  <span style={{ fontWeight:700, color:'var(--color-brand)' }}>Total a pagar</span>
                  <span style={{ fontWeight:800, fontSize:18, color:'var(--color-brand)' }}>MT {totalCarrinho.toFixed(2)}</span>
                </div>

                {/* Troco automático */}
                <div className="form-group" style={{ marginTop:12, marginBottom:0 }}>
                  <label>💰 Valor entregue pelo cliente (MT)</label>
                  <input
                    type="number" min="0" step="0.01"
                    value={valorEntregue}
                    onChange={e => setValorEntregue(e.target.value)}
                    placeholder="0.00"
                  />
                  {trocoCalculado !== null && (
                    <div style={{
                      marginTop:8, padding:'10px 12px',
                      background: trocoCalculado >= 0 ? 'var(--color-brand-light)' : 'var(--color-danger-light)',
                      borderRadius:8, fontSize:14, fontWeight:700,
                      color: trocoCalculado >= 0 ? 'var(--color-brand)' : 'var(--color-danger)',
                    }}>
                      {trocoCalculado >= 0
                        ? `✅ Troco: MT ${trocoCalculado.toFixed(2)}`
                        : `❌ Falta MT ${Math.abs(trocoCalculado).toFixed(2)}`}
                    </div>
                  )}
                </div>
              </div>
            )}

            {carrinho.length === 0 && (
              <div style={{ textAlign:'center', padding:'24px 0', color:'var(--text-muted)', fontSize:14 }}>
                🛒 Carrinho vazio — pesquise e adicione produtos acima
              </div>
            )}

            <div className="modal-actions">
              <button className="btn-secondary" onClick={handleFecharModal}>Cancelar</button>
              <button
                className="btn-primary"
                onClick={handleFinalizarVenda}
                disabled={loading || carrinho.length === 0}
              >
                {loading ? 'A registar...' : `✅ Finalizar Venda${carrinho.length > 0 ? ` (${carrinho.length} item${carrinho.length !== 1 ? 's' : ''})` : ''}`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default VendasPage;
