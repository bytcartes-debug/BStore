import { apiFetch } from '../utils/api';
import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X, ScanLine, Loader2 } from 'lucide-react';
import { abrirScanner } from '../utils/scanner';
import { buscarNaOpenFoodFacts } from '../utils/openFoodFacts';

const UNIDADES = ['un', 'kg', 'L', 'g', 'ml'];

interface Categoria { id: number; nome: string; }
interface Produto   { id: number; nome: string; preco: number; stock: number; stockMinimo: number; unidade: string; categoriaId: number; categoriaNome?: string; codigoBarras?: string; }

const fmt = (n: number, u: string) =>
  Number.isInteger(n) ? `${n} ${u}` : `${n.toFixed(3).replace(/\.?0+$/, '')} ${u}`;

const FORM_VAZIO = { nome: '', preco: '', stock: '', stockMinimo: '5', unidade: 'un', categoriaId: '', codigoBarras: '' };

const ProdutosPage: React.FC = () => {
  const [produtos, setProdutos]     = useState<Produto[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [search, setSearch]         = useState('');
  const [showModal, setShowModal]   = useState(false);
  const [editing, setEditing]       = useState<Produto | null>(null);
  const [form, setForm]             = useState(FORM_VAZIO);
  const [scanning, setScanning]     = useState(false);
  const [offMsg, setOffMsg]         = useState<string | null>(null); // mensagem Open Food Facts
  const [erro, setErro]             = useState<string | null>(null);

  const load = () => {
    apiFetch('/api/produtos').then(r => r.json()).then(setProdutos).catch(() => {});
    apiFetch('/api/categorias').then(r => r.json()).then(setCategorias).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const filtered = produtos.filter(p => p.nome.toLowerCase().includes(search.toLowerCase()));

  const openNew = () => {
    setEditing(null);
    setForm({ ...FORM_VAZIO, categoriaId: categorias[0]?.id.toString() || '' });
    setOffMsg(null); setErro(null);
    setShowModal(true);
  };

  const openEdit = (p: Produto) => {
    setEditing(p);
    setForm({
      nome: p.nome, preco: p.preco.toString(), stock: p.stock.toString(),
      stockMinimo: p.stockMinimo.toString(), unidade: p.unidade || 'un',
      categoriaId: p.categoriaId.toString(), codigoBarras: p.codigoBarras || '',
    });
    setOffMsg(null); setErro(null);
    setShowModal(true);
  };

  /** Scan de código de barras no formulário de produto */
  const handleScanProduto = async () => {
    setScanning(true); setOffMsg(null); setErro(null);
    try {
      const codigo = await abrirScanner();
      if (!codigo) return;

      setForm(f => ({ ...f, codigoBarras: codigo }));

      // 1. Verificar se já existe na BD local
      const r = await apiFetch(`/api/produtos/barcode/${encodeURIComponent(codigo)}`);
      if (r.ok) {
        const p = await r.json();
        setErro(`⚠️ Este código já está registado no produto "${p.nome}".`);
        return;
      }

      // 2. Consultar Open Food Facts
      setOffMsg('🔍 A procurar produto online...');
      const dados = await buscarNaOpenFoodFacts(codigo);
      if (dados) {
        setForm(f => ({
          ...f,
          nome:    dados.nome + (dados.marca && !dados.nome.toLowerCase().includes(dados.marca.toLowerCase()) ? ` ${dados.marca}` : ''),
          unidade: dados.unidade || f.unidade,
        }));
        setOffMsg(`✅ Produto encontrado: "${dados.nome}" ${dados.marca ? `(${dados.marca})` : ''}`);
      } else {
        setOffMsg('ℹ️ Produto não encontrado online. Preencha o nome manualmente.');
      }
    } finally {
      setScanning(false);
    }
  };

  const handleSave = async () => {
    if (!form.nome.trim() || !form.preco || !form.categoriaId) { setErro('Preencha nome, preço e categoria.'); return; }
    setErro(null);
    const body: any = {
      nome:        form.nome.trim(),
      preco:       parseFloat(form.preco),
      stock:       parseFloat(form.stock) || 0,
      stockMinimo: parseFloat(form.stockMinimo) || 0,
      unidade:     form.unidade || 'un',
      categoriaId: parseInt(form.categoriaId),
    };
    if (form.codigoBarras.trim()) body.codigoBarras = form.codigoBarras.trim();

    try {
      if (editing) {
        await apiFetch(`/api/produtos/${editing.id}`, { method: 'PUT', body: JSON.stringify(body) });
      } else {
        await apiFetch('/api/produtos', { method: 'POST', body: JSON.stringify(body) });
      }
      setShowModal(false);
      load();
    } catch (e: any) {
      setErro('Erro ao guardar. Tente novamente.');
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Apagar este produto?')) return;
    await apiFetch(`/api/produtos/${id}`, { method: 'DELETE' });
    load();
  };

  const f = form;
  const setF = (patch: Partial<typeof form>) => setForm(prev => ({ ...prev, ...patch }));

  return (
    <div>
      {/* Header */}
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:20, flexWrap:'wrap', gap:12 }}>
        <div>
          <h2 style={{ fontSize:22, fontWeight:700, color:'var(--text-primary)' }}>Produtos</h2>
          <p style={{ fontSize:14, color:'var(--text-secondary)', marginTop:4 }}>{produtos.length} produto(s) cadastrado(s)</p>
        </div>
        <button className="btn-primary" onClick={openNew}><Plus size={18} /> Novo Produto</button>
      </div>

      {/* Search */}
      <div style={{ marginBottom:16 }}>
        <input
          style={{ width:'100%', maxWidth:360 }}
          placeholder="🔍 Pesquisar produto..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {/* Tabela */}
      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr><th>Nome</th><th>Preço</th><th>Stock</th><th>Categoria</th><th>Código</th><th>Ações</th></tr>
            </thead>
            <tbody>
              {filtered.length === 0
                ? <tr><td colSpan={6} style={{ textAlign:'center', color:'var(--text-secondary)', padding:32 }}>Nenhum produto encontrado.</td></tr>
                : filtered.map(p => (
                  <tr key={p.id}>
                    <td style={{ fontWeight:600 }}>{p.nome}</td>
                    <td>MT {p.preco.toFixed(2)}</td>
                    <td style={{ color: p.stock <= p.stockMinimo ? 'var(--color-danger)' : 'inherit' }}>
                      {fmt(p.stock, p.unidade)} {p.stock <= p.stockMinimo && '⚠️'}
                    </td>
                    <td style={{ color:'var(--text-secondary)' }}>{p.categoriaNome || '—'}</td>
                    <td style={{ fontSize:11, color:'var(--text-muted)', fontFamily:'monospace' }}>{p.codigoBarras || '—'}</td>
                    <td>
                      <button className="icon-btn" onClick={() => openEdit(p)}><Pencil size={14} /></button>
                      <button className="icon-btn delete" onClick={() => handleDelete(p.id)}><Trash2 size={14} /></button>
                    </td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>{editing ? 'Editar Produto' : 'Novo Produto'}</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>

            {/* Banner Open Food Facts */}
            {offMsg && (
              <div style={{ fontSize:12, padding:'8px 12px', borderRadius:6, marginBottom:10,
                background: offMsg.startsWith('✅') ? 'rgba(16,185,129,0.15)' : 'rgba(100,100,255,0.1)',
                color: offMsg.startsWith('✅') ? 'var(--color-brand)' : 'var(--text-secondary)' }}>
                {offMsg}
              </div>
            )}
            {erro && <p style={{ fontSize:13, color:'var(--color-danger)', marginBottom:8 }}>{erro}</p>}

            {/* Código de Barras (topo — primeiro a preencher via scan) */}
            <div className="form-group">
              <label>📷 Código de Barras</label>
              <div style={{ display:'flex', gap:8 }}>
                <input
                  value={f.codigoBarras}
                  onChange={e => setF({ codigoBarras: e.target.value })}
                  placeholder="Ex: 5601234567890 (opcional)"
                  style={{ flex:1 }}
                />
                <button
                  className="btn-secondary"
                  onClick={handleScanProduto}
                  disabled={scanning}
                  style={{ whiteSpace:'nowrap', display:'flex', alignItems:'center', gap:6, minWidth:90 }}
                >
                  {scanning ? <Loader2 size={16} className="spin" /> : <ScanLine size={16} />}
                  {scanning ? 'A ler...' : 'Scan'}
                </button>
              </div>
            </div>

            <div className="form-group">
              <label>Nome *</label>
              <input value={f.nome} onChange={e => setF({ nome: e.target.value })} placeholder="Nome do produto" />
            </div>
            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:12 }}>
              <div className="form-group">
                <label>Preço (MT) *</label>
                <input type="number" min="0" step="0.01" value={f.preco} onChange={e => setF({ preco: e.target.value })} placeholder="0.00" />
              </div>
              <div className="form-group">
                <label>Stock Actual</label>
                <input type="number" min="0" step="0.001" value={f.stock} onChange={e => setF({ stock: e.target.value })} placeholder="0" />
              </div>
            </div>
            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:12 }}>
              <div className="form-group">
                <label>Stock Mínimo</label>
                <input type="number" min="0" step="0.001" value={f.stockMinimo} onChange={e => setF({ stockMinimo: e.target.value })} placeholder="5" />
              </div>
              <div className="form-group">
                <label>Unidade</label>
                <div style={{ display:'flex', gap:4, flexWrap:'wrap' }}>
                  {UNIDADES.map(u => (
                    <button key={u} type="button" onClick={() => setF({ unidade: u })}
                      style={{ padding:'6px 10px', borderRadius:6, border:'2px solid',
                        borderColor: f.unidade === u ? 'var(--color-primary)' : 'var(--border)',
                        background: f.unidade === u ? 'var(--color-primary)' : 'transparent',
                        color: f.unidade === u ? '#fff' : 'var(--text-primary)',
                        fontWeight: f.unidade === u ? 700 : 400, cursor:'pointer', fontSize:12 }}>
                      {u}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="form-group">
              <label>Categoria *</label>
              <select value={f.categoriaId} onChange={e => setF({ categoriaId: e.target.value })}>
                <option value="">— Selecionar —</option>
                {categorias.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>

            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave}>{editing ? 'Guardar' : 'Criar Produto'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProdutosPage;
