import { useEffect, useState } from 'react';
import { apiRequest } from '../utils/api';
import { useToast } from '../utils/toast';
import { PageHeading, LoadError, EmptyState, SkeletonRow } from '../components/UI';
import { ArrowLeft, Copy, RefreshCw, Share2 } from 'lucide-react';
import type { PageId } from '../App';
import './FaltaReporPage.css';

interface ItemReposicao {
  id: number;
  nome: string;
  stockAtual: string;
  stockMinimo: string;
  stockMaximo: string | null;
  quantidadeSugerida: string;
  unidade: string;
  preco: string;
  custo: string;
}

interface FaltaReporPageProps {
  navigate: (page: PageId) => void;
}

export default function FaltaReporPage({ navigate }: FaltaReporPageProps) {
  const [itens, setItens] = useState<ItemReposicao[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const toast = useToast();

  const carregarDados = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiRequest<ItemReposicao[]>('/api/stock/falta-repor');
      setItens(data || []);
    } catch (err: any) {
      setError(err?.message || 'Não foi possível carregar a lista de reposição.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    carregarDados();
  }, []);

  const itensFiltrados = itens.filter((i) =>
    i.nome.toLowerCase().includes(search.trim().toLowerCase())
  );

  const gerarTextoLista = () => {
    const agora = new Date().toLocaleDateString('pt-MZ');
    let texto = `*Lista de Compras / Reposição - BStore (${agora})*\n\n`;

    itens.forEach((item, index) => {
      texto += `${index + 1}. *${item.nome}*: Comprar ${item.quantidadeSugerida} ${item.unidade} (Stock actual: ${item.stockAtual})\n`;
    });

    texto += `\nTotal de itens para repor: ${itens.length}`;
    return texto;
  };

  const copiarLista = async () => {
    if (itens.length === 0) return;
    const texto = gerarTextoLista();
    if (navigator.clipboard) {
      try {
        await navigator.clipboard.writeText(texto);
        toast('Lista copiada! Pode colar no WhatsApp ou notas.');
      } catch {
        toast('Não foi possível copiar a lista.');
      }
    } else {
      toast('Área de transferência indisponível.');
    }
  };

  const partilharLista = async () => {
    if (itens.length === 0) return;
    const texto = gerarTextoLista();
    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: 'Lista de Reposição - BStore',
          text: texto,
        });
        toast('Lista partilhada com sucesso.');
        return;
      } catch (err: unknown) {
        if ((err as Error)?.name === 'AbortError') return;
      }
    }
    await copiarLista();
  };

  const podePartilhar = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  return (
    <div className="falta-repor-page">
      <div className="falta-repor-header-bar">
        <button
          type="button"
          className="btn-voltar"
          onClick={() => navigate('dashboard')}
          aria-label="Voltar ao Painel"
        >
          <ArrowLeft size={20} />
          <span>Voltar</span>
        </button>
        <PageHeading
          title="Falta Repor"
          description="Produtos com stock no limite ou esgotados com sugestão de compra"
        />
        <div className="falta-repor-acoes">
          <button
            type="button"
            className="btn-acao-repor"
            onClick={carregarDados}
            disabled={loading}
            title="Atualizar lista"
          >
            <RefreshCw size={18} className={loading ? 'spin' : ''} />
            <span>Atualizar</span>
          </button>
          <button
            type="button"
            className="btn-acao-repor"
            onClick={copiarLista}
            disabled={itens.length === 0}
            title="Copiar lista de compras para a área de transferência"
          >
            <Copy size={18} />
            <span>Copiar lista</span>
          </button>
          {podePartilhar && (
            <button
              type="button"
              className="btn-acao-repor btn-whatsapp"
              onClick={partilharLista}
              disabled={itens.length === 0}
              title="Partilhar lista via Web Share API"
            >
              <Share2 size={18} />
              <span>Partilhar</span>
            </button>
          )}
        </div>
      </div>

      <div className="falta-repor-filtro">
        <input
          type="text"
          placeholder="Filtrar produto..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="repor-busca-input"
        />
      </div>

      {loading && (
        <div className="card" aria-busy="true">
          <div className="table-wrapper">
            <table className="responsive-table repor-tabela">
              <tbody>
                {Array.from({ length: 6 }).map((_, i) => (
                  <SkeletonRow key={i} cols={6} />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {error && <LoadError message={error} retry={carregarDados} />}

      {!loading && !error && itens.length === 0 && (
        <EmptyState
          title="Stock em ordem!"
          description="Todos os produtos estão acima do stock mínimo estipulado."
        />
      )}

      {!loading && !error && itens.length > 0 && (
        <div className="card">
          <div className="table-wrapper">
            <table className="responsive-table repor-tabela">
              <caption className="sr-only">Produtos em falta para repor no inventário</caption>
              <thead>
                <tr>
                  <th scope="col">Produto</th>
                  <th scope="col" className="numeric">Stock Atual</th>
                  <th scope="col" className="numeric">Mínimo</th>
                  <th scope="col" className="numeric">Máximo</th>
                  <th scope="col" className="numeric destaque">Sugestão de Compra</th>
                  <th scope="col" className="numeric">Custo Estimado</th>
                </tr>
              </thead>
              <tbody>
                {itensFiltrados.map((item) => (
                  <tr key={item.id}>
                    <td data-label="Produto" className="cell-name prod-col">
                      <strong className="prod-nome">{item.nome}</strong>
                    </td>
                    <td data-label="Stock Atual" className="numeric">
                      <span className="badge-atual">
                        {item.stockAtual} {item.unidade}
                      </span>
                    </td>
                    <td data-label="Mínimo" className="numeric cell-secondary">
                      {item.stockMinimo} {item.unidade}
                    </td>
                    <td data-label="Máximo" className="numeric cell-secondary">
                      {item.stockMaximo ? `${item.stockMaximo} ${item.unidade}` : '-'}
                    </td>
                    <td data-label="Sugestão de Compra" className="numeric destaque">
                      <span className="badge-sugestao">
                        +{item.quantidadeSugerida} {item.unidade}
                      </span>
                    </td>
                    <td data-label="Custo Estimado" className="numeric font-mono">
                      {item.custo !== '0.00' ? `${item.custo} MT` : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
