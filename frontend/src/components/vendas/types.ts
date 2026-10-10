import type Decimal from 'decimal.js';

export interface Produto {
  id: number;
  nome: string;
  preco: string;
  stock: string;
  unidade: string;
  codigoBarras?: string;
  custo?: string;
}

export interface Categoria {
  id: number;
  nome: string;
}

export interface Devedor {
  id: number;
  nome: string;
  saldo?: string;
}

export interface ItemVendaDetalhe {
  id: number;
  produto: string;
  quantidade: string;
  precoUnitario: string;
  total: string;
  descontoPercentual?: string;
  descontoValor?: string;
  notaDesconto?: string;
}

export interface PagamentoVendaDetalhe {
  id: number;
  metodo: string;
  valor: string;
  troco: string;
  referencia?: string;
}

export interface VendaDocumento {
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

export interface MetodoPagamentoConfig {
  id: number;
  nome: string;
  tipo: string;
  ativo: boolean;
  ordem: number;
}

export interface DefinicoesLoja {
  controloCaixa: boolean;
  nomeLoja: string;
}

export interface SessaoCaixaAtual {
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

export interface DespesaCaixa {
  id: number;
  sessaoId?: number;
  valor: string;
  categoria: string;
  descricao: string;
  criadaEm: string;
  criadoPor?: number;
}

export interface CarrinhoEmEspera {
  id: string;
  criadoEm: string;
  identificador?: string;
  clienteId?: string;
  observacao?: string;
  itens: {
    produto: Produto;
    quantidade: string;
    descontoPercentual?: string;
    descontoValor?: string;
    notaDesconto?: string;
  }[];
  total: string;
  totalItens: number;
}

export interface ItemCarrinho {
  produto: Produto;
  quantidade: Decimal;
  descontoPercentual?: string;
  descontoValor?: string;
  notaDesconto?: string;
}

export interface LinhaPagamento {
  id: string;
  metodoId?: number;
  metodo: string;
  valor: string;
  referencia?: string;
}
