export interface DadosProdutoOFF {
  nome: string;
  unidade: string | null; // ex: "kg", "L", "g", "ml", "un"
  marca: string | null;
}


/** Tenta extrair a unidade de uma string de quantidade como "5 kg", "500 ml", "1 L" */
const extrairUnidade = (quantidade: string | null | undefined): string | null => {
  if (!quantidade) return null;
  const lower = quantidade.toLowerCase();
  for (const u of ['kg', 'g', ' l', 'ml', 'litr']) {
    if (lower.includes(u)) {
      if (lower.includes('ml'))  return 'ml';
      if (lower.includes('kg'))  return 'kg';
      if (lower.includes(' g ') || lower.endsWith('g')) return 'g';
      if (lower.includes('l'))   return 'L';
    }
  }
  return null;
};

/**
 * Consulta o Open Food Facts pelo código de barras.
 * Devolve os dados do produto ou null se não encontrado.
 */
export const buscarNaOpenFoodFacts = async (codigo: string): Promise<DadosProdutoOFF | null> => {
  try {
    const r = await fetch(
      `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(codigo)}.json?fields=product_name,brands,quantity`,
      { signal: AbortSignal.timeout(6000) }
    );
    if (!r.ok) return null;
    const data = await r.json();
    if (data.status !== 1 || !data.product) return null;

    const { product } = data;
    const nome  = (product.product_name as string)?.trim() || null;
    const marca = (product.brands as string)?.split(',')[0].trim() || null;
    const unid  = extrairUnidade(product.quantity as string);

    if (!nome) return null;
    return { nome, unidade: unid, marca };
  } catch {
    return null;
  }
};
