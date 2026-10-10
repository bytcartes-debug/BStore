export interface Categoria {
  id: number;
  nome: string;
}

export interface Produto {
  id: number;
  nome: string;
  preco: string;
  custo?: string;
  margem?: string;
  stock: string;
  stockMinimo: string;
  unidade: string;
  categoriaId: number;
  categoriaNome?: string;
  codigoBarras?: string;
  ativo?: boolean;
}

export interface MovimentoStock {
  id: number;
  produtoId: number;
  produtoNome: string;
  tipo: string;
  quantidade: string;
  custoUnitario: string;
  motivo?: string;
  referenciaTipo?: string;
  referenciaId?: number;
  criadoEm: string;
}

export interface ItemEntrada {
  produtoId: number;
  produtoNome: string;
  quantidade: string;
  custoUnitario: string;
  motivo: string;
}

export interface ProdutoFormData {
  nome: string;
  preco: string;
  custo: string;
  stock: string;
  stockMinimo: string;
  unidade: string;
  categoriaId: string;
  codigoBarras: string;
}

export const UNIDADES = ['un', 'kg', 'L', 'g', 'ml'];
