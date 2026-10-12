import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockApi, navigate, account } from './fixtures';

async function assertNoHorizontalScroll(page: Page, step = '') {
  const scrollData = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(
    scrollData.scrollWidth,
    `Scroll horizontal detetado em ${step || 'página'}: scrollWidth=${scrollData.scrollWidth} > innerWidth=${scrollData.innerWidth}`
  ).toBeLessThanOrEqual(scrollData.innerWidth + 1);
}

async function checkA11y(page: Page, contextName: string) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const violations = result.violations.filter(
    (v) => v.impact === 'critical' || v.impact === 'serious'
  );
  expect(
    violations,
    `Acessibilidade violada (${contextName}): ${JSON.stringify(violations.map((v) => ({ id: v.id, impact: v.impact, desc: v.description, nodes: v.nodes.length })))}`
  ).toEqual([]);
}

test.describe('Testes de fluxo crítico por dispositivo', () => {
  test('1. login com validação de scroll e acessibilidade nos temas claro e escuro', async ({ page }) => {
    let loggedIn = false;
    await mockApi(page);
    await page.route('**/api/auth/me', (route) => {
      if (!loggedIn) {
        return route.fulfill({ status: 401, json: { erro: 'Não autenticado' } });
      }
      return route.fulfill({ status: 200, json: account });
    });
    await page.route('**/api/auth/login', (route) => {
      loggedIn = true;
      return route.fulfill({ status: 200, json: account });
    });

    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Aceder ao Sistema' })).toBeVisible();
    await assertNoHorizontalScroll(page, 'Login');
    await checkA11y(page, 'Login (tema claro)');

    // Alternar para tema escuro no login
    await page.evaluate(() => document.body.classList.add('dark-theme'));
    await page.waitForTimeout(300);
    await checkA11y(page, 'Login (tema escuro)');
    await page.evaluate(() => document.body.classList.remove('dark-theme'));
    await page.waitForTimeout(300);

    // Preencher credenciais e autenticar
    await page.locator('#login-email').fill('teste@example.test');
    await page.locator('#login-password').fill('Senha123!');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
    await assertNoHorizontalScroll(page, 'Painel após Login');
  });

  test('2. venda em dinheiro com troco (3 toques no telemóvel)', async ({ page }) => {
    await mockApi(page);
    let vendaRecebida: any = null;
    await page.route('**/api/vendas/lote', (route) => {
      vendaRecebida = route.request().postDataJSON();
      return route.fulfill({
        status: 200,
        json: { id: 201, numero: 50, total: '80.00', troco: '20.00', itens: 1 },
      });
    });

    await page.goto('/');
    await navigate(page, 'Vendas');
    await assertNoHorizontalScroll(page, 'Vendas inicial');

    // Toque 1: Abrir modal de venda
    await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await assertNoHorizontalScroll(page, 'Modal Nova Venda');

    // Toque 2: Adicionar produto pelo catálogo
    const prodCard = page.getByRole('button', { name: /Arroz agulha/i }).first();
    if (await prodCard.isVisible()) {
      await prodCard.click();
    } else {
      await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Arroz');
      await page.keyboard.press('ArrowDown');
      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Adicionar', exact: true }).click();
    }

    // Toque 3: Selecionar nota de 100 MT e confirmar venda
    await page.getByRole('button', { name: '100 MT', exact: true }).click();
    await assertNoHorizontalScroll(page, 'Venda com Troco Calculado');
    await page.getByRole('button', { name: /Confirmar venda/i }).click();

    // Verificação do recibo de sucesso pós-venda
    await expect(page.getByRole('dialog')).toContainText(/Venda #50 Concluída|Venda registada/i);
    await assertNoHorizontalScroll(page, 'Recibo pós-venda');
    await page.getByRole('button', { name: 'Fechar', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(vendaRecebida).not.toBeNull();
  });

  test('3. venda dividida em múltiplos métodos com cálculo em tempo real e sem scroll', async ({ page }) => {
    await mockApi(page);
    let vendaDivididaPayload: any = null;
    await page.route('**/api/vendas/lote', (route) => {
      vendaDivididaPayload = route.request().postDataJSON();
      return route.fulfill({
        status: 200,
        json: { id: 202, numero: 51, total: '80.00', troco: '0.00', itens: 1 },
      });
    });

    await page.goto('/');
    await navigate(page, 'Vendas');

    await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    // Adicionar produto
    await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Arroz');
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: 'Adicionar', exact: true }).click();

    // Dividir pagamento
    await page.getByRole('button', { name: 'Dividir', exact: true }).click();

    // Linha 1: 50 MT Dinheiro
    const inputValor1 = page.locator('input[id^="valor-"]').first();
    await inputValor1.fill('50.00');

    // Linha 2: 30 MT M-Pesa
    const selectMetodo2 = page.locator('select[id^="metodo-"]').nth(1);
    await selectMetodo2.selectOption('M-Pesa');
    const inputValor2 = page.locator('input[id^="valor-"]').nth(1);
    await inputValor2.fill('30.00');

    await assertNoHorizontalScroll(page, 'Venda Dividida');
    await page.getByRole('button', { name: /Confirmar venda/i }).click();

    await expect(page.getByRole('dialog')).toContainText(/Venda #51 Concluída/i);
    await page.getByRole('button', { name: 'Fechar', exact: true }).click();
    expect(vendaDivididaPayload.pagamentos.length).toBe(2);
  });

  test('4. devolução parcial de produtos repostos em stock e sem scroll', async ({ page }) => {
    await mockApi(page);
    let devolucaoEnviada: any = null;

    const vendaComItens = {
      id: 1,
      numero: 1,
      data: '12/10/2026',
      hora: '10:00',
      total: '160.00',
      troco: '0.00',
      estado: 'CONCLUIDA',
      itens: [
        {
          id: 10,
          vendaId: 1,
          produtoId: 1,
          produto: 'Arroz agulha de qualidade superior',
          quantidade: '2.000',
          precoUnitario: '80.00',
          total: '160.00',
        },
      ],
      pagamentos: [{ id: 1, metodo: 'DINHEIRO', valor: '160.00' }],
    };

    await page.route('**/api/vendas', (route) => {
      if (devolucaoEnviada) {
        return route.fulfill({
          status: 200,
          json: [{ ...vendaComItens, estado: 'PARCIALMENTE_DEVOLVIDA' }],
        });
      }
      return route.fulfill({ status: 200, json: [vendaComItens] });
    });

    await page.route('**/api/vendas/1/devolver', (route) => {
      devolucaoEnviada = route.request().postDataJSON();
      return route.fulfill({
        status: 200,
        json: { ...vendaComItens, estado: 'PARCIALMENTE_DEVOLVIDA' },
      });
    });

    await page.goto('/');
    await navigate(page, 'Vendas');
    await assertNoHorizontalScroll(page, 'Lista de Vendas');

    // Clicar em Detalhes
    await page.getByRole('button', { name: 'Detalhes', exact: true }).first().click();
    await expect(page.getByRole('dialog', { name: /Detalhes do Documento/i })).toBeVisible();

    // Iniciar devolução
    await page.getByRole('button', { name: /Devolver produtos|Devolver/i }).click();
    const modalDevolucao = page.getByRole('dialog', { name: /Devolver Produtos/i });
    await expect(modalDevolucao).toBeVisible();

    // Selecionar 1 unidade para devolver
    const inputDevolver = modalDevolucao.locator('input[placeholder="0"]').first();
    await inputDevolver.fill('1.000');

    await assertNoHorizontalScroll(page, 'Modal Devolução Parcial');
    await modalDevolucao.getByRole('button', { name: 'Confirmar devolução' }).click();

    await expect(page.getByText('Devolução registada com sucesso')).toBeVisible();
    expect(devolucaoEnviada).not.toBeNull();
  });

  test('5. abrir e fechar caixa com conferência cega e sem scroll', async ({ page }) => {
    let caixaAberta = false;
    await mockApi(page);
    await page.route('**/api/definicoes', (route) =>
      route.fulfill({ status: 200, json: { controloCaixa: true, nomeLoja: 'BStore' } }),
    );
    await page.route('**/api/caixa/atual', (route) =>
      route.fulfill({
        status: 200,
        json: caixaAberta
          ? {
              aberta: true,
              id: 99,
              valorInicial: '500.00',
              vendasDinheiro: '200.00',
              valorEsperado: '700.00',
            }
          : { aberta: false },
      }),
    );
    await page.route('**/api/caixa/abrir', (route) => {
      caixaAberta = true;
      return route.fulfill({ status: 200, json: { sucesso: true } });
    });
    await page.route('**/api/caixa/fechar', (route) => {
      caixaAberta = false;
      return route.fulfill({
        status: 200,
        json: {
          sessao: { id: 99, valorInicial: '500.00', valorEsperado: '700.00', valorContado: '700.00', diferenca: '0.00' },
          relatorio: { totalVendido: '200.00', lucroEstimado: '80.00', numeroVendas: 2, totalFiado: '0.00' },
        },
      });
    });

    await page.goto('/');
    await navigate(page, 'Vendas');
    await assertNoHorizontalScroll(page, 'Vendas com Caixa Fechada');

    // Abrir Caixa
    await page.getByRole('button', { name: 'Abrir caixa', exact: true }).first().click();
    await expect(page.getByRole('dialog', { name: 'Abrir Caixa do Dia' })).toBeVisible();
    await page.locator('#caixa-abertura').fill('500.00');
    await page.getByRole('button', { name: 'Abrir caixa', exact: true }).last().click();

    // Agora caixa está aberta
    await expect(page.getByText('Caixa aberta')).toBeVisible();
    await assertNoHorizontalScroll(page, 'Vendas com Caixa Aberta');

    // Fechar Caixa
    await page.getByRole('button', { name: 'Fechar caixa', exact: true }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.locator('#fecho-contado').fill('700.00');
    await assertNoHorizontalScroll(page, 'Modal Fecho de Caixa');
    await page.getByRole('button', { name: /Confirmar e fechar caixa/i }).click();
    await expect(
      page.getByRole('heading', { name: 'Caixa fechado com sucesso!' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Concluir' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });

  test('6. catálogo e navegação pelos 5 ecrãs principais sem scroll horizontal e acessibilidade', async ({ page }, testInfo) => {
    test.setTimeout(240000);
    await mockApi(page);
    await page.goto('/');

    const telas = [
      { nome: 'Visão Geral', header: 'A sua loja, hoje' },
      { nome: 'Vendas', header: 'Vendas' },
      { nome: 'Produtos', header: 'Produtos' },
      { nome: 'Devedores', header: 'Devedores' },
    ];

    // 1. Painel (Visão Geral)
    await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
    await assertNoHorizontalScroll(page, 'Painel');
    await checkA11y(page, 'Painel (claro)');

    // 2. Outras telas
    for (const tela of telas.slice(1)) {
      await navigate(page, tela.nome);
      await expect(page.getByRole('heading', { name: new RegExp(tela.header, 'i'), level: 2 })).toBeVisible();
      await assertNoHorizontalScroll(page, tela.nome);
      await checkA11y(page, `${tela.nome} (claro)`);
    }

    // 5º ecrã: Caixa (FechoCaixaModal)
    await navigate(page, 'Vendas');
    await page.getByRole('button', { name: 'Fecho de Caixa' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await assertNoHorizontalScroll(page, 'Caixa Modal');
    await checkA11y(page, 'Caixa Modal (claro)');
    await page.getByRole('button', { name: 'Fechar janela' }).click();

    // Capturas de ecrã para os 5 ecrãs no projeto Desktop e Mobile
    const isDesktop = (page.viewportSize()?.width || 0) >= 1000;
    const isMobile = (page.viewportSize()?.width || 0) < 600;

    if (isDesktop || isMobile) {
      const sufixo = isDesktop ? 'desktop' : 'mobile';
      // Captura Painel
      await navigate(page, 'Visão Geral');
      await page.screenshot({ path: testInfo.outputPath(`ecra-painel-${sufixo}.png`), fullPage: true });

      // Captura Vendas
      await navigate(page, 'Vendas');
      await page.screenshot({ path: testInfo.outputPath(`ecra-vendas-${sufixo}.png`), fullPage: true });

      // Captura Produtos
      await navigate(page, 'Produtos');
      await page.screenshot({ path: testInfo.outputPath(`ecra-produtos-${sufixo}.png`), fullPage: true });

      // Captura Devedores
      await navigate(page, 'Devedores');
      await page.screenshot({ path: testInfo.outputPath(`ecra-devedores-${sufixo}.png`), fullPage: true });

      // Captura Caixa
      await navigate(page, 'Vendas');
      await page.getByRole('button', { name: 'Fecho de Caixa' }).click();
      await page.screenshot({ path: testInfo.outputPath(`ecra-caixa-${sufixo}.png`), fullPage: false });
      await page.getByRole('button', { name: 'Fechar janela' }).click();
    }
  });
});
