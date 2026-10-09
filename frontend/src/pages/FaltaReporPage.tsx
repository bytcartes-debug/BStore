import { useEffect, useState } from 'react';
import { apiRequest } from '../utils/api';
import { useToast } from '../utils/toast';
import { PageHeading, Loading, LoadError, EmptyState } from '../components/UI';
import { ArrowLeft, RefreshCw, Share2 } from 'lucide-react';
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

  const copiarListaWhatsApp = () => {
    if (itens.length === 0) return;
    const agora = new Date().toLocaleDateString('pt-MZ');
    let texto = `*Lista de Compras / Reposição - BStore (${agora})*\n\n`;

    itens.forEach((item, index) => {
      texto += `${index + 1}. *${item.nome}*: Comprar ${item.quantidadeSugerida} ${item.unidade} (Stock actual: ${item.stockAtual})\n`;
    });

    texto += `\nTotal de itens para repor: ${itens.length}`;

    if (navigator.clipboard) {
      navigator.clipboard.writeText(texto).then(() => {
        toast('Lista copiada! Pode colar no WhatsApp.');
      });
    } else {
      toast('Área de transferência indisponível.');
    }
  };

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
            className="btn-acao-repor btn-whatsapp"
            onClick={copiarListaWhatsApp}
            disabled={itens.length === 0}
          >
            <Share2 size={18} />
            <span>Copiar para WhatsApp</span>
          </button>
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

      {loading && <Loading label="A verificar níveis de stock..." />}
      {error && <LoadError message={error} retry={carregarDados} />}

      {!loading && !error && itens.length === 0 && (
        <EmptyState
          title="Stock em ordem!"
          description="Todos os produtos estão acima do stock mínimo estipulado."
        />
      )}

      {!loading && !error && itens.length > 0 && (
        <div className="repor-tabela-container">
          <table className="repor-tabela">
            <thead>
              <tr>
                <th>Produto</th>
                <th className="num">Stock Atual</th>
                <th className="num">Mínimo</th>
                <th className="num">Máximo</th>
                <th className="num destaque">Sugestão de Compra</th>
                <th className="num">Custo Estimado</th>
              </tr>
            </thead>
            <tbody>
              {itensFiltrados.map((item) => (
                <tr key={item.id}>
                  <td className="prod-col">
                    <span className="prod-nome">{item.nome}</span>
                  </td>
                  <td className="num">
                    <span className="badge-atual">
                      {item.stockAtual} {item.unidade}
                    </span>
                  </td>
                  <td className="num">{item.stockMinimo} {item.unidade}</td>
                  <td className="num">{item.stockMaximo ? `${item.stockMaximo} ${item.unidade}` : '-'}</td>
                  <td className="num destaque">
                    <span className="badge-sugestao">
                      +{item.quantidadeSugerida} {item.unidade}
                    </span>
                  </td>
                  <td className="num">
                    {item.custo !== '0.00' ? `${item.custo} MT` : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
