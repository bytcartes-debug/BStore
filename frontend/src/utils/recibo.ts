import { formatMoney, formatQuantity } from './decimal';

export interface ItemRecibo {
  produto: string;
  quantidade: string;
  unidade?: string;
  precoUnitario: string;
  total: string;
}

export interface PagamentoRecibo {
  metodo: string;
  valor: string;
  troco?: string;
}

export interface ReciboDados {
  id?: number;
  numero: number | string;
  data: string;
  hora?: string;
  total: string;
  troco?: string;
  observacao?: string;
  clienteNome?: string;
  itens: ItemRecibo[];
  pagamentos?: PagamentoRecibo[];
}

export function formatarMetodoPagamento(metodo: string): string {
  switch (metodo?.toUpperCase()) {
    case 'DINHEIRO': return 'Dinheiro';
    case 'MPESA': return 'M-Pesa';
    case 'EMOLA': return 'e-Mola';
    case 'MKESH': return 'mKesh';
    case 'CARTAO': return 'Cartão';
    case 'FIADO': return 'A Fiado';
    default: return metodo || 'Outro';
  }
}

export function gerarTextoRecibo(recibo: ReciboDados, nomeLoja = 'Flex Stock'): string {
  const linha = '----------------------------------------';
  const dataHora = recibo.hora ? `${recibo.data} ${recibo.hora}` : recibo.data;

  const itensTexto = recibo.itens
    .map((item) => {
      const qtd = formatQuantity(item.quantidade, item.unidade || '');
      const preco = formatMoney(item.precoUnitario);
      const total = formatMoney(item.total);
      return `${item.produto}\n  ${qtd} x ${preco} = ${total}`;
    })
    .join('\n');

  let pagamentosTexto = '';
  if (recibo.pagamentos && recibo.pagamentos.length > 0) {
    pagamentosTexto = '\nFormas de Pagamento:\n' +
      recibo.pagamentos
        .map((p) => {
          const m = formatarMetodoPagamento(p.metodo);
          return `  ${m}: ${formatMoney(p.valor)}`;
        })
        .join('\n');
  }

  const trocoTexto = recibo.troco && Number(recibo.troco) > 0
    ? `\nTroco: ${formatMoney(recibo.troco)}`
    : '';

  const clienteTexto = recibo.clienteNome
    ? `\nCliente: ${recibo.clienteNome}`
    : '';

  const obsTexto = recibo.observacao
    ? `\nObs: ${recibo.observacao}`
    : '';

  return [
    `          ${nomeLoja.toUpperCase()}          `,
    linha,
    `Venda: #${recibo.numero}`,
    `Data: ${dataHora}${clienteTexto}`,
    linha,
    itensTexto,
    linha,
    `TOTAL: ${formatMoney(recibo.total)}${pagamentosTexto}${trocoTexto}${obsTexto}`,
    linha,
    '       Obrigado pela preferência!       ',
    '             Volte sempre!              ',
  ].join('\n');
}

export function formatarNumeroWhatsApp(telefone?: string): string {
  if (!telefone) return '';
  const limpo = telefone.replace(/\D/g, '');
  if (!limpo) return '';
  if (limpo.length === 9) {
    return `258${limpo}`;
  }
  return limpo;
}

export function partilharReciboWhatsApp(
  recibo: ReciboDados,
  nomeLoja = 'BStore',
  telefone?: string,
) {
  const texto = gerarTextoRecibo(recibo, nomeLoja);
  const numFormatado = formatarNumeroWhatsApp(telefone);
  const url = numFormatado
    ? `https://wa.me/${numFormatado}?text=${encodeURIComponent(texto)}`
    : `https://wa.me/?text=${encodeURIComponent(texto)}`;
  window.open(url, '_blank', 'noopener,noreferrer');
}

export async function partilharRecibo(
  recibo: ReciboDados,
  nomeLoja = 'BStore',
): Promise<'compartilhado' | 'copiado' | 'cancelado'> {
  const texto = gerarTextoRecibo(recibo, nomeLoja);
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({
        title: `Recibo #${recibo.numero} — ${nomeLoja}`,
        text: texto,
      });
      return 'compartilhado';
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        return 'cancelado';
      }
    }
  }

  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(texto);
      return 'copiado';
    } catch {}
  }

  return 'cancelado';
}

export function imprimirReciboTexto(
  recibo: ReciboDados,
  nomeLoja = 'BStore',
  largura: '58mm' | '80mm' = '58mm',
) {
  const texto = gerarTextoRecibo(recibo, nomeLoja);
  const printWidth = largura === '80mm' ? '72mm' : '48mm';

  // Montar elemento no DOM da página para suporte nativo a window.print() sem popup blocker
  let printArea = document.getElementById('bstore-recibo-print-area');
  if (!printArea) {
    printArea = document.createElement('div');
    printArea.id = 'bstore-recibo-print-area';
    document.body.appendChild(printArea);
  }
  printArea.className = largura === '80mm' ? 'recibo-termico-80' : 'recibo-termico-58';
  printArea.innerHTML = `<pre class="bstore-recibo-pre">${texto}</pre>`;

  try {
    window.print();
    return;
  } catch {
    // Caso window.print falhe, fallback para abertura de popup
  }

  const win = window.open('', '_blank', 'width=420,height=600,noopener,noreferrer');
  if (!win) return;
  win.document.write(`
    <!DOCTYPE html>
    <html>
      <head>
        <title>Recibo #${recibo.numero}</title>
        <style>
          @page {
            margin: 0;
            size: ${largura} auto;
          }
          body {
            font-family: 'Courier New', Courier, monospace;
            font-size: 12px;
            white-space: pre-wrap;
            padding: 8px 4px;
            margin: 0 auto;
            width: ${printWidth};
            color: #000;
          }
        </style>
      </head>
      <body>${texto}</body>
    </html>
  `);
  win.document.close();
  win.focus();
  setTimeout(() => {
    try {
      win.print();
      win.close();
    } catch {}
  }, 250);
}
