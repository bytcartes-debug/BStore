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

export function partilharReciboWhatsApp(recibo: ReciboDados, nomeLoja = 'Flex Stock') {
  const texto = gerarTextoRecibo(recibo, nomeLoja);
  const url = `https://wa.me/?text=${encodeURIComponent(texto)}`;
  window.open(url, '_blank');
}

export function imprimirReciboTexto(recibo: ReciboDados, nomeLoja = 'Flex Stock') {
  const texto = gerarTextoRecibo(recibo, nomeLoja);
  const win = window.open('', '_blank', 'width=400,height=600');
  if (!win) return;
  win.document.write(`
    <!DOCTYPE html>
    <html>
      <head>
        <title>Recibo #${recibo.numero}</title>
        <style>
          body {
            font-family: 'Courier New', Courier, monospace;
            font-size: 13px;
            white-space: pre-wrap;
            padding: 16px;
            margin: 0;
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
    win.print();
    win.close();
  }, 250);
}
