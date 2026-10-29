import { useState, useEffect, useCallback, useRef } from 'react';
import { RefreshCw, CreditCard, Printer, CheckCircle2, History, Download, Send, Lock, Eye, EyeOff } from 'lucide-react';
import { apiRequest, descarregarFicheiro } from '../utils/api';
import { formatarDataHora } from '../utils/dataHora';
import { decimalSeguro, formatMoney, parseDecimalInput } from '../utils/decimal';
import { Modal, Spinner, Notice, Field } from './UI';
import { useToast } from '../utils/toast';
import { Confetti, type ConfettiHandle } from './ui/Confetti';

interface FechoCaixaData {
  data: string;
  totalVendido: string;
  totalCusto: string;
  lucroEstimado: string;
  numeroVendas: number;
  vendasAnuladas: number;
  totalFiado: string;
  totalDespesas?: string;
  totaisPorMetodo: Record<string, string>;
}

interface SessaoCaixaAtual {
  aberta: boolean;
  id?: number;
  abertaEm?: string;
  valorInicial?: string;
  vendasDinheiro?: string;
  devolucoesDinheiro?: string;
  despesasDinheiro?: string;
  valorEsperado?: string;
  totalVendas?: number;
}

interface SessaoHistorico {
  id: number;
  abertaEm: string;
  fechadaEm?: string;
  valorInicial: string;
  valorEsperado?: string;
  valorContado?: string;
  diferenca?: string;
  notaFecho?: string;
  estado: string;
}

interface FechoCaixaModalProps {
  onClose: () => void;
  onSessaoFechada?: () => void;
}

export function FechoCaixaModal({ onClose, onSessaoFechada }: FechoCaixaModalProps) {
  const toast = useToast();
  const [abaAtiva, setAbaAtiva] = useState<'sessao' | 'diario' | 'historico'>('sessao');
  const [dataSelecionada, setDataSelecionada] = useState(new Date().toISOString().slice(0, 10));

  // Sessão atual
  const [sessao, setSessao] = useState<SessaoCaixaAtual | null>(null);
  const [, setCarregandoSessao] = useState(false);
  const [valorContado, setValorContado] = useState('');
  const [notaFecho, setNotaFecho] = useState('');
  const [fechando, setFechando] = useState(false);
  const [fechoSucesso, setFechoSucesso] = useState<any | null>(null);
  const [modoCego, setModoCego] = useState(true);
  const confettiRef = useRef<ConfettiHandle>(null);

  // Relatório diário
  const [dados, setDados] = useState<FechoCaixaData | null>(null);
  const [carregandoDiario, setCarregandoDiario] = useState(false);
  const [enviandoResumoWhatsapp, setEnviandoResumoWhatsapp] = useState(false);

  // Histórico
  const [historico, setHistorico] = useState<SessaoHistorico[]>([]);
  const [carregandoHistorico, setCarregandoHistorico] = useState(false);
  const [exportandoHistorico, setExportandoHistorico] = useState(false);
  const [histInicio, setHistInicio] = useState('');
  const [histFim, setHistFim] = useState('');

  const [erro, setErro] = useState<string | null>(null);

  const carregarSessao = useCallback(async () => {
    setCarregandoSessao(true);
    try {
      const res = await apiRequest<SessaoCaixaAtual>('/api/caixa/atual');
      setSessao(res);
      if (res && res.aberta) {
        setValorContado(''); // Fecho de caixa cego: o operador digita o que realmente contou
      } else {
        setAbaAtiva('diario');
      }
    } catch {
      setAbaAtiva('diario');
    } finally {
      setCarregandoSessao(false);
    }
  }, []);

  const carregarDiario = useCallback(async (data: string) => {
    setCarregandoDiario(true);
    setErro(null);
    try {
      const res = await apiRequest<FechoCaixaData>(`/api/caixa/fecho?data=${data}`);
      setDados(res);
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Falha ao carregar fecho diário.');
    } finally {
      setCarregandoDiario(false);
    }
  }, []);

  const carregarHistorico = useCallback(async (inicio = histInicio, fim = histFim) => {
    setCarregandoHistorico(true);
    try {
      const params = new URLSearchParams();
      if (inicio) params.set('inicio', inicio);
      if (fim) params.set('fim', fim);
      const query = params.toString() ? `?${params.toString()}` : '';
      const res = await apiRequest<SessaoHistorico[]>(`/api/caixa/historico${query}`);
      setHistorico(res || []);
    } catch {
      setHistorico([]);
    } finally {
      setCarregandoHistorico(false);
    }
  }, [histInicio, histFim]);

  useEffect(() => {
    void carregarSessao();
  }, [carregarSessao]);

  useEffect(() => {
    if (abaAtiva === 'diario') {
      void carregarDiario(dataSelecionada);
    } else if (abaAtiva === 'historico') {
      void carregarHistorico();
    }
  }, [abaAtiva, dataSelecionada, carregarDiario, carregarHistorico]);

  const valorEsperadoDec = decimalSeguro(sessao?.valorEsperado, 0);
  const valorContadoDec = parseDecimalInput(valorContado) || decimalSeguro(0);
  const diferencaDec = valorContadoDec.minus(valorEsperadoDec);

  const handleExportarHistorico = async () => {
    try {
      setExportandoHistorico(true);
      const params = new URLSearchParams({ limite: '200' });
      if (histInicio) params.set('inicio', histInicio);
      if (histFim) params.set('fim', histFim);
      await descarregarFicheiro(
        `/api/caixa/exportar?${params.toString()}`,
        `fechos_caixa_${new Date().toISOString().slice(0, 10)}.csv`,
      );
      toast('Histórico de fechos exportado com sucesso.');
    } catch {
      toast('Não foi possível exportar os fechos de caixa.');
    } finally {
      setExportandoHistorico(false);
    }
  };

  const handleFecharSessao = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sessao || !sessao.aberta) return;
    setFechando(true);
    setErro(null);
    try {
      const res = await apiRequest('/api/caixa/fechar', {
        method: 'POST',
        body: JSON.stringify({
          sessaoId: sessao.id,
          valorContado: valorContadoDec.toFixed(2),
          notaFecho: notaFecho.trim() || undefined,
        }),
      });
      setFechoSucesso(res);
      confettiRef.current?.fire();
      toast('Caixa fechado com sucesso.');
      if (onSessaoFechada) onSessaoFechada();
    } catch (err: unknown) {
      setErro(err instanceof Error ? err.message : 'Falha ao fechar o caixa.');
    } finally {
      setFechando(false);
    }
  };

  const enviarResumoWhatsapp = async () => {
    setEnviandoResumoWhatsapp(true);
    try {
      const res = await apiRequest<{ sucesso: boolean; mensagem: string; erro?: string }>(
        `/api/caixa/resumo-diario/whatsapp?data=${dataSelecionada}`,
        { method: 'POST' }
      );
      if (res.sucesso) {
        toast('✅ ' + (res.mensagem || 'Resumo do dia enviado para o WhatsApp do proprietário!'));
      } else {
        toast('❌ ' + (res.erro || 'Falha ao enviar resumo.'));
      }
    } catch (err: unknown) {
      toast('❌ ' + (err instanceof Error ? err.message : 'Erro ao comunicar com WhatsApp.'));
    } finally {
      setEnviandoResumoWhatsapp(false);
    }
  };

  const imprimirFecho = () => {
    if (!dados) return;
    const win = window.open('', '_blank', 'width=450,height=600');
    if (!win) return;
    const metodosHtml = Object.entries(dados.totaisPorMetodo || {})
      .map(
        ([m, val]) =>
          `<tr><td style="padding:4px 0">${m}:</td><td style="text-align:right;font-weight:bold">${formatMoney(val)}</td></tr>`,
      )
      .join('');

    win.document.write(`
      <!DOCTYPE html>
      <html>
        <head>
          <title>Fecho de Caixa - ${dados.data}</title>
          <style>
            body { font-family: monospace; font-size: 13px; padding: 20px; line-height: 1.5; color: #111; }
            h2, h3 { margin: 0 0 8px 0; text-align: center; }
            hr { border: none; border-top: 1px dashed #666; margin: 12px 0; }
            table { width: 100%; border-collapse: collapse; }
            .total-row { font-size: 15px; font-weight: bold; border-top: 1px solid #000; }
          </style>
        </head>
        <body>
          <h2>BSTORE</h2>
          <h3>FECHO DIÁRIO DE CAIXA</h3>
          <p style="text-align:center">Data: ${dados.data}</p>
          <hr />
          <table>
            <tr><td>Total Vendido:</td><td style="text-align:right;font-weight:bold">${formatMoney(dados.totalVendido)}</td></tr>
            <tr><td>Total Custo:</td><td style="text-align:right">${formatMoney(dados.totalCusto)}</td></tr>
            <tr><td>Lucro Estimado:</td><td style="text-align:right;color:#0a7a3b;font-weight:bold">${formatMoney(dados.lucroEstimado)}</td></tr>
            <tr><td>Vendas Concluídas:</td><td style="text-align:right">${dados.numeroVendas}</td></tr>
            <tr><td>Vendas Anuladas:</td><td style="text-align:right;color:#c00">${dados.vendasAnuladas}</td></tr>
            <tr><td>Vendas a Fiado:</td><td style="text-align:right">${formatMoney(dados.totalFiado)}</td></tr>
            <tr><td>Saídas / Despesas:</td><td style="text-align:right;color:#ea580c">-${formatMoney(dados.totalDespesas || '0')}</td></tr>
          </table>
          <hr />
          <h4 style="margin:8px 0">RECEBIMENTOS POR MÉTODO</h4>
          <table>
            ${metodosHtml}
          </table>
          <hr />
          <p style="text-align:center;font-size:11px">Emitido em ${new Date().toLocaleString('pt-MZ')}</p>
        </body>
      </html>
    `);
    win.document.close();
    win.focus();
    setTimeout(() => {
      win.print();
      win.close();
    }, 300);
  };

  return (
    <Modal title="Controlo e Fecho de Caixa" onClose={onClose} wide>
      {/* Abas */}
      <div
        style={{
          display: 'flex',
          gap: 8,
          marginBottom: 16,
          borderBottom: '1px solid var(--border-color)',
          paddingBottom: 8,
        }}
      >
        {sessao && sessao.aberta && (
          <button
            type="button"
            className={abaAtiva === 'sessao' ? 'btn-primary' : 'btn-secondary'}
            onClick={() => setAbaAtiva('sessao')}
            style={{ fontSize: 13 }}
          >
            Fechar caixa agora
          </button>
        )}
        <button
          type="button"
          className={abaAtiva === 'diario' ? 'btn-primary' : 'btn-secondary'}
          onClick={() => setAbaAtiva('diario')}
          style={{ fontSize: 13 }}
        >
          Resumo diário
        </button>
        <button
          type="button"
          className={abaAtiva === 'historico' ? 'btn-primary' : 'btn-secondary'}
          onClick={() => setAbaAtiva('historico')}
          style={{ fontSize: 13 }}
        >
          <History size={14} style={{ marginRight: 4 }} /> Histórico de fechos
        </button>
      </div>

      {erro && <Notice>{erro}</Notice>}

      {/* ABA 1: FECHAR SESSÃO ATIVA */}
      {abaAtiva === 'sessao' && sessao && (
        <div>
          {fechoSucesso ? (
            <div style={{ textAlign: 'center', padding: '24px 16px' }}>
              <CheckCircle2 size={52} color="#16a34a" style={{ margin: '0 auto 12px' }} />
              <h3 style={{ fontSize: 20, marginBottom: 8 }}>Caixa fechado com sucesso!</h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: 14, marginBottom: 12 }}>
                Turno concluído. O relatório de auditoria e conferência foi enviado directamente para o WhatsApp do proprietário.
              </p>
              
              <div
                style={{
                  background: 'var(--bg-subtle, #f9fafb)',
                  border: '1px solid var(--border-color, #e5e7eb)',
                  borderRadius: 8,
                  padding: '12px 16px',
                  maxWidth: 360,
                  margin: '0 auto 20px',
                  textAlign: 'left',
                  fontSize: 13,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ color: 'var(--text-muted)' }}>Total contado:</span>
                  <strong style={{ fontFamily: 'monospace', fontSize: 14 }}>{formatMoney(fechoSucesso.valorContado)}</strong>
                </div>
                {!modoCego && (
                  <>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                      <span style={{ color: 'var(--text-muted)' }}>Esperado em gaveta:</span>
                      <strong style={{ fontFamily: 'monospace' }}>{formatMoney(fechoSucesso.valorEsperado)}</strong>
                    </div>
                    {fechoSucesso.diferenca !== '0.00' && (
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          color: Number(fechoSucesso.diferenca) < 0 ? '#dc2626' : '#2563eb',
                          fontWeight: 600,
                        }}
                      >
                        <span>Diferença:</span>
                        <span>{Number(fechoSucesso.diferenca) > 0 ? '+' : ''}{formatMoney(fechoSucesso.diferenca)}</span>
                      </div>
                    )}
                  </>
                )}
              </div>

              <div>
                <button type="button" className="btn-primary" onClick={onClose}>
                  Concluir
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleFecharSessao}>
              {/* Barra de Modo Cego / Auditoria */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: modoCego ? 'rgba(37, 99, 235, 0.08)' : 'rgba(234, 88, 12, 0.08)',
                  border: `1px solid ${modoCego ? 'rgba(37, 99, 235, 0.2)' : 'rgba(234, 88, 12, 0.2)'}`,
                  borderRadius: 8,
                  padding: '10px 14px',
                  marginBottom: 16,
                  gap: 12,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Lock size={16} color={modoCego ? '#2563eb' : '#ea580c'} />
                  <div>
                    <strong style={{ fontSize: 13, display: 'block' }}>
                      {modoCego ? 'Conferência Cega de Caixa' : 'Modo Auditoria (Gerente)'}
                    </strong>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {modoCego
                        ? 'Contagem física independente. O operador não vê os totais teóricos do sistema.'
                        : 'Totais teóricos e desvios visíveis em tempo real.'}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ fontSize: 11, padding: '5px 9px', display: 'flex', alignItems: 'center', gap: 5, whiteSpace: 'nowrap' }}
                  onClick={() => setModoCego(!modoCego)}
                >
                  {modoCego ? <Eye size={13} /> : <EyeOff size={13} />}
                  {modoCego ? 'Ver Valores' : 'Activar Modo Cego'}
                </button>
              </div>

              {modoCego ? (
                /* Cards Modo Cego */
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                    gap: 10,
                    marginBottom: 16,
                  }}
                >
                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Fundo Inicial</span>
                    <strong style={{ display: 'block', fontSize: 17, marginTop: 4 }}>
                      {formatMoney(sessao.valorInicial || '0')}
                    </strong>
                  </div>
                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Vendas Registadas</span>
                    <strong style={{ display: 'block', fontSize: 17, color: '#16a34a', marginTop: 4 }}>
                      {sessao.totalVendas || 0}
                    </strong>
                  </div>
                  <div className="card" style={{ padding: 12, textAlign: 'center', background: 'var(--bg-subtle)' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Destino da Auditoria</span>
                    <strong style={{ display: 'block', fontSize: 13, color: '#25D366', marginTop: 6 }}>
                      WhatsApp do Dono
                    </strong>
                  </div>
                </div>
              ) : (
                /* Cards Modo Auditoria / Gerente */
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
                    gap: 10,
                    marginBottom: 16,
                  }}
                >
                  <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Fundo Inicial</span>
                    <strong style={{ display: 'block', fontSize: 15 }}>
                      {formatMoney(sessao.valorInicial || '0')}
                    </strong>
                  </div>
                  <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Vendas Dinheiro</span>
                    <strong style={{ display: 'block', fontSize: 15, color: '#16a34a' }}>
                      +{formatMoney(sessao.vendasDinheiro || '0')}
                    </strong>
                  </div>
                  <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Devoluções</span>
                    <strong style={{ display: 'block', fontSize: 15, color: '#dc2626' }}>
                      -{formatMoney(sessao.devolucoesDinheiro || '0')}
                    </strong>
                  </div>
                  <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Despesas</span>
                    <strong style={{ display: 'block', fontSize: 15, color: '#ea580c' }}>
                      -{formatMoney(sessao.despesasDinheiro || '0')}
                    </strong>
                  </div>
                  <div
                    className="card"
                    style={{ padding: 10, textAlign: 'center', background: 'var(--bg-subtle)' }}
                  >
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Valor Esperado</span>
                    <strong style={{ display: 'block', fontSize: 16, color: 'var(--color-brand)' }}>
                      {formatMoney(valorEsperadoDec)}
                    </strong>
                  </div>
                </div>
              )}

              <Field id="fecho-contado" label="Dinheiro físico contado na gaveta (MT) *">
                <input
                  id="fecho-contado"
                  type="number"
                  step="0.01"
                  min="0"
                  inputMode="decimal"
                  placeholder="0.00"
                  required
                  value={valorContado}
                  onChange={(e) => setValorContado(e.target.value)}
                  style={{ fontSize: 18, fontWeight: 600, padding: 10 }}
                />
              </Field>

              <div className="pos-keypad-container" style={{ marginTop: 8, marginBottom: 12 }}>
                <div className="pos-keypad">
                  {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
                    <button
                      key={digit}
                      type="button"
                      className="pos-keypad-btn"
                      onClick={() => setValorContado((prev) => (prev === '0' ? digit : prev + digit))}
                    >
                      {digit}
                    </button>
                  ))}
                  <button
                    type="button"
                    className="pos-keypad-btn"
                    onClick={() => setValorContado((prev) => (prev.includes('.') ? prev : (prev || '0') + '.'))}
                  >
                    .
                  </button>
                  <button
                    type="button"
                    className="pos-keypad-btn"
                    onClick={() => setValorContado((prev) => (prev === '0' ? '0' : prev + '0'))}
                  >
                    0
                  </button>
                  <button
                    type="button"
                    className="pos-keypad-btn backspace"
                    onClick={() => setValorContado((prev) => prev.slice(0, -1))}
                    aria-label="Apagar dígito"
                  >
                    ⌫
                  </button>
                </div>
              </div>

              {!modoCego && (
                <>
                  <div style={{ display: 'flex', gap: 8, marginTop: 8, marginBottom: 16 }}>
                    <button
                      type="button"
                      className="btn-secondary"
                      style={{ fontSize: 12, padding: '4px 8px' }}
                      onClick={() => setValorContado(valorEsperadoDec.toFixed(2))}
                    >
                      Usar valor esperado ({formatMoney(valorEsperadoDec)})
                    </button>
                  </div>

                  <div style={{ marginBottom: 16 }}>
                    {diferencaDec.isZero() ? (
                      <Notice kind="success">
                        A contagem confere exactamente com o valor esperado.
                      </Notice>
                    ) : diferencaDec.gt(0) ? (
                      <Notice kind="info">
                        Sobra de dinheiro na gaveta: +{formatMoney(diferencaDec)}
                      </Notice>
                    ) : (
                      <Notice kind="error">
                        Falta de dinheiro na gaveta: {formatMoney(diferencaDec)}
                      </Notice>
                    )}
                  </div>
                </>
              )}

              <Field id="fecho-nota" label="Observações de fecho (opcional)">
                <input
                  id="fecho-nota"
                  type="text"
                  placeholder="Ex: Turno da manhã, justificação de quebra de caixa..."
                  value={notaFecho}
                  onChange={(e) => setNotaFecho(e.target.value)}
                />
              </Field>

              <div className="modal-actions" style={{ marginTop: 20 }}>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={onClose}
                  disabled={fechando}
                >
                  Cancelar
                </button>
                <button type="submit" className="btn-primary" disabled={fechando}>
                  {fechando ? <Spinner size="small" /> : 'Confirmar e fechar caixa'}
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* ABA 2: RESUMO DIÁRIO */}
      {abaAtiva === 'diario' && (
        <div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 16, flexWrap: 'wrap' }}>
            <label htmlFor="fecho-data" style={{ fontWeight: 600 }}>
              Data:
            </label>
            <input
              id="fecho-data"
              type="date"
              value={dataSelecionada}
              onChange={(e) => setDataSelecionada(e.target.value)}
              style={{
                padding: '6px 10px',
                borderRadius: 6,
                border: '1px solid var(--border-color, #ccc)',
              }}
            />
            <div style={{ display: 'inline-flex', gap: 6 }}>
              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: 12, padding: '5px 9px' }}
                onClick={() => setDataSelecionada(new Date().toISOString().slice(0, 10))}
              >
                Hoje
              </button>
              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: 12, padding: '5px 9px' }}
                onClick={() => {
                  const d = new Date();
                  d.setDate(d.getDate() - 1);
                  setDataSelecionada(d.toISOString().slice(0, 10));
                }}
              >
                Ontem
              </button>
            </div>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void carregarDiario(dataSelecionada)}
              disabled={carregandoDiario}
            >
              {carregandoDiario ? (
                <Spinner size="small" />
              ) : (
                <>
                  <RefreshCw size={15} /> <span>Atualizar</span>
                </>
              )}
            </button>
          </div>

          {carregandoDiario && !dados ? (
            <div style={{ padding: 32, textAlign: 'center' }}>
              <Spinner />
              <p style={{ marginTop: 8 }}>A carregar dados do dia…</p>
            </div>
          ) : dados ? (
            <div>
              <div className="fecho-pc-layout">
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                    gap: 12,
                  }}
                >
                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                      Total Vendido
                    </span>
                    <strong
                      style={{ display: 'block', fontSize: 18, color: 'var(--primary, #1e40af)' }}
                    >
                      {formatMoney(dados.totalVendido)}
                    </strong>
                  </div>

                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                      Custo Mercadorias
                    </span>
                    <strong style={{ display: 'block', fontSize: 18 }}>
                      {formatMoney(dados.totalCusto)}
                    </strong>
                  </div>

                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                      Lucro Estimado
                    </span>
                    <strong style={{ display: 'block', fontSize: 18, color: '#16a34a' }}>
                      {formatMoney(dados.lucroEstimado)}
                    </strong>
                    <span
                      style={{
                        fontSize: 10,
                        color: 'var(--text-muted, #888)',
                        display: 'block',
                        marginTop: 2,
                      }}
                    >
                      Vendas antigas sem custo não entram no lucro
                    </span>
                  </div>

                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                      Vendas / Anuladas
                    </span>
                    <strong style={{ display: 'block', fontSize: 18 }}>
                      {dados.numeroVendas} /{' '}
                      <span style={{ color: '#dc2626' }}>{dados.vendasAnuladas}</span>
                    </strong>
                  </div>

                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                      Vendido a Fiado
                    </span>
                    <strong style={{ display: 'block', fontSize: 18, color: '#d97706' }}>
                      {formatMoney(dados.totalFiado)}
                    </strong>
                  </div>

                  <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                      Saídas / Despesas
                    </span>
                    <strong style={{ display: 'block', fontSize: 18, color: '#ea580c' }}>
                      -{formatMoney(dados.totalDespesas || '0')}
                    </strong>
                  </div>
                </div>

                <div className="card" style={{ padding: 16 }}>
                  <h4 style={{ margin: '0 0 12px 0', display: 'flex', alignItems: 'center', gap: 8 }}>
                    <CreditCard size={18} /> Totais por Forma de Pagamento
                  </h4>
                  <table className="responsive-table">
                    <thead>
                      <tr>
                        <th>Método</th>
                        <th className="numeric">Total Recebido</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.entries(dados.totaisPorMetodo || {}).map(([metodo, valor]) => (
                        <tr key={metodo}>
                          <td data-label="Método">
                            <strong>{metodo}</strong>
                          </td>
                          <td data-label="Total Recebido" className="numeric font-mono" style={{ fontWeight: 600 }}>
                            {formatMoney(valor)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="modal-actions" style={{ marginTop: 20, display: 'flex', gap: 8, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                <button type="button" className="btn-secondary" onClick={onClose}>
                  Fechar
                </button>
                <button
                  type="button"
                  className="btn-primary"
                  style={{ background: '#16a34a', borderColor: '#16a34a', color: '#fff', display: 'flex', alignItems: 'center', gap: 6 }}
                  onClick={enviarResumoWhatsapp}
                  disabled={enviandoResumoWhatsapp || !dados}
                >
                  {enviandoResumoWhatsapp ? <Spinner size="small" /> : <Send size={16} />}
                  Enviar Resumo ao Dono (WhatsApp)
                </button>
                <button type="button" className="btn-primary" onClick={imprimirFecho}>
                  <Printer size={16} /> Imprimir Fecho
                </button>
              </div>
            </div>
          ) : null}
        </div>
      )}

      {/* ABA 3: HISTÓRICO DE SESSÕES */}
      {abaAtiva === 'historico' && (
        <div>
          <div
            style={{
              display: 'flex',
              gap: 10,
              alignItems: 'center',
              marginBottom: 16,
              flexWrap: 'wrap',
            }}
          >
            <label
              style={{
                fontSize: 13,
                color: 'var(--text-secondary)',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              De:
              <input
                type="date"
                aria-label="Data inicial das sessões"
                value={histInicio}
                max={histFim || undefined}
                onChange={(e) => setHistInicio(e.target.value)}
                style={{
                  padding: '6px 8px',
                  borderRadius: 6,
                  border: '1px solid var(--border-color, #ccc)',
                }}
              />
            </label>
            <label
              style={{
                fontSize: 13,
                color: 'var(--text-secondary)',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              Até:
              <input
                type="date"
                aria-label="Data final das sessões"
                value={histFim}
                min={histInicio || undefined}
                onChange={(e) => setHistFim(e.target.value)}
                style={{
                  padding: '6px 8px',
                  borderRadius: 6,
                  border: '1px solid var(--border-color, #ccc)',
                }}
              />
            </label>
            <button
              type="button"
              className="btn-secondary"
              style={{ fontSize: 13, padding: '6px 12px' }}
              onClick={() => void carregarHistorico(histInicio, histFim)}
              disabled={carregandoHistorico}
            >
              {carregandoHistorico ? <Spinner size="small" /> : 'Filtrar'}
            </button>
            {(histInicio || histFim) && (
              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: 13, padding: '6px 12px' }}
                onClick={() => {
                  setHistInicio('');
                  setHistFim('');
                  void carregarHistorico('', '');
                }}
                disabled={carregandoHistorico}
              >
                Limpar
              </button>
            )}
          </div>

          {carregandoHistorico ? (
            <div style={{ textAlign: 'center', padding: 24 }}>
              <Spinner />
            </div>
          ) : historico.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: 24 }}>
              {histInicio || histFim
                ? 'Nenhum fecho de caixa encontrado no período selecionado.'
                : 'Nenhum fecho de caixa registado até ao momento.'}
            </p>
          ) : (
            <div className="table-wrapper">
              <table className="responsive-table">
                <thead>
                  <tr>
                    <th>Data Abertura</th>
                    <th>Fechada Em</th>
                    <th className="numeric">Inicial</th>
                    <th className="numeric">Esperado</th>
                    <th className="numeric">Contado</th>
                    <th className="numeric">Diferença</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {historico.map((h) => {
                    const difNum = Number(h.diferenca || '0');
                    return (
                      <tr key={h.id}>
                        <td data-label="Data Abertura">{formatarDataHora(h.abertaEm)}</td>
                        <td data-label="Fechada Em">
                          {h.fechadaEm ? formatarDataHora(h.fechadaEm) : 'Aberta'}
                        </td>
                        <td data-label="Inicial" className="numeric font-mono">{formatMoney(h.valorInicial)}</td>
                        <td data-label="Esperado" className="numeric font-mono">
                          {h.valorEsperado ? formatMoney(h.valorEsperado) : '-'}
                        </td>
                        <td data-label="Contado" className="numeric font-mono">
                          {h.valorContado ? formatMoney(h.valorContado) : '-'}
                        </td>
                        <td
                          data-label="Diferença"
                          className="numeric font-mono"
                          style={{
                            color: difNum < 0 ? '#dc2626' : difNum > 0 ? '#2563eb' : '#16a34a',
                            fontWeight: 600,
                          }}
                        >
                          {h.diferenca
                            ? `${difNum > 0 ? '+' : ''}${formatMoney(h.diferenca)}`
                            : '-'}
                        </td>
                        <td data-label="Estado">
                          <span
                            className={`badge ${h.estado === 'ABERTA' ? 'badge-success' : 'badge-neutral'}`}
                          >
                            {h.estado === 'ABERTA' ? 'Aberta' : 'Fechada'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          <div className="modal-actions" style={{ marginTop: 20 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              Fechar
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void handleExportarHistorico()}
              disabled={exportandoHistorico || historico.length === 0}
              title="Exportar histórico de fechos para CSV / Excel"
            >
              {exportandoHistorico ? <Spinner size="small" /> : <Download size={16} />} Exportar CSV
            </button>
          </div>
        </div>
      )}
      <Confetti ref={confettiRef} count={90} />
    </Modal>
  );
}
