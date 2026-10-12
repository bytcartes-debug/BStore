import { test, expect } from '@playwright/test';
import { mockApi, navigate, account, products } from './fixtures';

test.describe('Vendas em espera por utilizador e revalidação de preço', () => {
  test('pôr venda em espera fica associada ao utilizador e não aparece para outro utilizador após logout', async ({ page }) => {
    let currentUser = { ...account, id: 10, userId: 10 };

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/auth/me') {
        return route.fulfill({ json: currentUser });
      }
      if (url.pathname === '/api/auth/logout') {
        return route.fulfill({ status: 204 });
      }
      if (url.pathname === '/api/produtos') {
        return route.fulfill({ json: products });
      }
      if (url.pathname === '/api/categorias') {
        return route.fulfill({ json: [] });
      }
      if (url.pathname === '/api/devedores') {
        return route.fulfill({ json: [] });
      }
      if (url.pathname === '/api/vendas') {
        return route.fulfill({ json: [] });
      }
      if (url.pathname === '/api/definicoes') {
        return route.fulfill({ json: { controloCaixa: false, nomeLoja: 'BStore' } });
      }
      if (url.pathname === '/api/caixa/atual') {
        return route.fulfill({ json: { aberta: false } });
      }
      if (url.pathname === '/api/metodos-pagamento') {
        return route.fulfill({ json: [{ id: 1, nome: 'Dinheiro', tipo: 'DINHEIRO', ativo: true, ordem: 1 }] });
      }
      return route.fulfill({ json: {} });
    });

    await page.goto('/');
    await navigate(page, 'Vendas');

    // Abre modal de registar venda
    await page.getByRole('button', { name: 'Registar venda' }).first().click();
    await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Água');
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: 'Adicionar', exact: true }).click();

    // Põe o carrinho em espera
    page.on('dialog', async (dialog) => {
      await dialog.accept('Cliente Teste');
    });

    await page.getByRole('button', { name: /Pôr em Espera/i }).click();

    // Carrinho deve aparecer no banner de espera do utilizador 10
    await expect(page.getByText(/1 Carrinho\(s\) em Espera no Balcão/i)).toBeVisible();

    // Verifica que a chave guardada é específica do utilizador 10
    const chaveExiste = await page.evaluate(() => {
      return localStorage.getItem('bstore_carrinhos_espera_10') !== null;
    });
    expect(chaveExiste).toBe(true);

    // Fecha modal
    await page.getByRole('button', { name: 'Cancelar' }).click();

    // Faz logout
    await page.getByRole('button', { name: /Terminar sessão|Sair/i }).click();

    // Muda utilizador actual para id 11
    currentUser = { ...account, id: 11, userId: 11, email: 'operador@teste.com' };

    // Recarrega como novo utilizador
    await page.goto('/');
    await navigate(page, 'Vendas');

    // O utilizador 11 não deve ver o carrinho em espera do utilizador 10
    await expect(page.getByText(/Carrinho\(s\) em Espera no Balcão/i)).toHaveCount(0);
  });

  test('retomar carrinho com preço alterado mostra aviso de actualização', async ({ page }) => {
    await mockApi(page);

    // Injeta sessão de utilizador e carrinho em espera para o utilizador atual (id 10) com preço antigo (25.00 em vez de 35.00)
    await page.addInitScript(() => {
      localStorage.setItem('currentUser', JSON.stringify({ userId: 10, id: 10, email: 'teste@example.test', role: 'superuser' }));
      const held = [
        {
          id: 'test-cart-1',
          criadoEm: '10:00',
          criadoEmMs: Date.now(),
          identificador: 'Cliente Manhã',
          itens: [
            {
              produto: {
                id: 3,
                nome: 'Água mineral 1,5 L',
                preco: '25.00', // Preço antigo guardado
                stock: '40.000',
                unidade: 'un',
                ativo: true,
              },
              quantidade: '2',
            },
          ],
          total: '50.00',
          totalItens: 2,
        },
      ];
      localStorage.setItem('bstore_carrinhos_espera_10', JSON.stringify(held));
    });

    await page.goto('/');
    await navigate(page, 'Vendas');

    // O banner de carrinhos em espera deve estar visível
    await expect(page.getByText(/1 Carrinho\(s\) em Espera no Balcão/i)).toBeVisible();

    // Retoma o carrinho através do botão com o identificador / título
    await page.getByRole('button', { name: /Cliente Manhã/i }).click();

    // Deve apresentar o aviso de que o preço mudou para o valor actual do catálogo (35.00)
    await expect(page.getByText(/O preço de 1 produto\(s\) mudou/i)).toBeVisible();
  });
});
