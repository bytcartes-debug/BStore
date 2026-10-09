import { expect, type Page } from '@playwright/test';

export const account = {
  id: 10,
  nome: 'Conta de teste',
  email: 'teste@example.test',
  role: 'superuser',
  diasRestantes: -1,
};
export const categories = [
  {
    id: 1,
    nome: 'Mercearia',
    descricao: 'Produtos essenciais para o dia a dia',
    icone: '🛍️',
    totalProdutos: 2,
  },
  {
    id: 2,
    nome: 'Bebidas',
    descricao: 'Água, sumos e refrigerantes',
    icone: 'drink',
    totalProdutos: 1,
  },
];
export const products = [
  {
    id: 1,
    nome: 'Arroz agulha de qualidade superior',
    preco: '80.00',
    custo: '50.00',
    margem: '37.50',
    stock: '25.500',
    stockMinimo: '5.000',
    unidade: 'kg',
    categoriaId: 1,
    categoriaNome: 'Mercearia',
    codigoBarras: '5601234567890',
    ativo: true,
  },
  {
    id: 2,
    nome: 'Açúcar branco',
    preco: '95.50',
    custo: '70.00',
    margem: '26.70',
    stock: '2.000',
    stockMinimo: '5.000',
    unidade: 'kg',
    categoriaId: 1,
    categoriaNome: 'Mercearia',
    ativo: true,
  },
  {
    id: 3,
    nome: 'Água mineral 1,5 L',
    preco: '35.00',
    custo: '20.00',
    margem: '42.86',
    stock: '40.000',
    stockMinimo: '10.000',
    unidade: 'un',
    categoriaId: 2,
    categoriaNome: 'Bebidas',
    ativo: true,
  },
];
const sales = [
  { id: 1, produto: products[0].nome, quantidade: '1.500', total: '120.00', data: '29/09/2026' },
  { id: 2, produto: products[2].nome, quantidade: '2.000', total: '70.00', data: '29/09/2026' },
];
export const dashboard = {
  totalVendasHoje: '190.00',
  lucroHoje: '65.00',
  lucroUltimos7Dias: '450.00',
  valorTotalStockCusto: '2215.00',
  totalProdutos: 3,
  totalCategorias: 2,
  totalDevedores: 1,
  alertasStock: [products[1]],
  vendasRecentes: sales,
  vendasPorDia: [0, 80, 120, 240, 95.5, 380, 190].map((total, i) => ({
    dia: `${23 + i}/09`,
    total: total.toFixed(2),
  })),
};

export async function mockApi(page: Page, role = 'superuser') {
  // Nenhum pedido desta suite chega ao backend, incluindo pedidos de escrita.
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() !== 'GET') {
      await route.fulfill({
        status: 500,
        json: { erro: 'Pedido de escrita não simulado neste teste.' },
      });
      return;
    }
    const responses: Record<string, unknown> = {
      '/api/auth/me': { ...account, role },
      '/api/dashboard': dashboard,
      '/api/produtos': products,
      '/api/categorias': categories,
      '/api/categorias/1/produtos': products.filter((p) => p.categoriaId === 1),
      '/api/categorias/2/produtos': products.filter((p) => p.categoriaId === 2),
      '/api/devedores': [
        {
          id: 1,
          nome: 'Cliente de teste',
          divida: '250.50',
          descricao: 'Compras de mercearia',
          data: '28/09/2026',
        },
      ],
      '/api/vendas': sales,
      '/api/usuarios': [
        { ...account, diasAcesso: 0, dataExpiracao: null, expirado: false },
        {
          id: 11,
          nome: 'Operador de teste',
          email: 'operador@example.test',
          role: 'operator',
          diasAcesso: 30,
          dataExpiracao: '04/10/2026',
          diasRestantes: 5,
          expirado: false,
        },
      ],
      '/api/definicoes': { controloCaixa: false, nomeLoja: 'BStore' },
      '/api/caixa/atual': { aberta: false },
      '/api/metodos-pagamento': [
        { id: 1, nome: 'Dinheiro', tipo: 'DINHEIRO', ativo: true, ordem: 1 },
        { id: 2, nome: 'M-Pesa', tipo: 'DIGITAL', ativo: true, ordem: 2 },
        { id: 3, nome: 'e-Mola', tipo: 'DIGITAL', ativo: true, ordem: 3 },
        { id: 4, nome: 'A fiado', tipo: 'FIADO', ativo: true, ordem: 4 },
      ],
      '/api/stock/falta-repor': [
        {
          id: 2,
          nome: 'Açúcar branco',
          stockAtual: '2.000',
          stockMinimo: '5.000',
          stockMaximo: '10.000',
          quantidadeSugerida: '8.000',
          unidade: 'kg',
          preco: '95.50',
          custo: '70.00',
        },
      ],
    };
    await route.fulfill({
      status: path in responses ? 200 : 404,
      json: responses[path] ?? { erro: 'Recurso não encontrado.' },
    });
  });
}

export async function navigate(page: Page, name: string) {
  const mobile = (page.viewportSize()?.width || 1440) < 768;
  if (mobile) await page.getByRole('button', { name: 'Abrir menu', exact: true }).click();
  await page
    .getByRole('navigation', { name: 'Navegação principal' })
    .getByRole('button', { name: new RegExp(`^${name}`, 'i') })
    .click();
  if (mobile) await expect(page.getByRole('dialog')).toHaveCount(0);
}

export async function noOverflow(page: Page) {
  const overflow = await page.evaluate(() => ({
    page: document.documentElement.scrollWidth - window.innerWidth,
    dialogs: [...document.querySelectorAll('dialog[open]')].map(
      (el) => el.scrollWidth - el.clientWidth,
    ),
  }));
  expect(overflow.page).toBeLessThanOrEqual(1);
  expect(overflow.dialogs.every((value) => value <= 1)).toBe(true);
}
