import { useState, useEffect, useCallback } from 'react';
import { apiRequest } from './api';
import { useToast } from './toast';

export interface IaStatus {
  ativa: boolean;
  provedor: string;
  temApiKey: boolean;
  usadosHoje: number;
  limiteDiario: number;
  usadosMes: number;
  limiteMensal: number;
}

export function useIa() {
  const [status, setStatus] = useState<IaStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [testando, setTestando] = useState(false);
  const toast = useToast();

  const carregarStatus = useCallback(async () => {
    try {
      const data = await apiRequest<IaStatus>('/api/ia/status');
      setStatus(data);
    } catch {
      setStatus(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    carregarStatus();
  }, [carregarStatus]);

  const atualizarConfig = async (config: {
    activa?: boolean;
    provedor?: string;
    apiKey?: string;
    limiteDiario?: number;
    limiteMensal?: number;
  }) => {
    try {
      const atualizado = await apiRequest<IaStatus>('/api/ia/config', {
        method: 'PUT',
        body: JSON.stringify(config),
      });
      setStatus(atualizado);
      toast('Configurações de IA salvas com sucesso.');
      return true;
    } catch (err) {
      toast('Erro ao salvar configurações de IA: ' + (err instanceof Error ? err.message : String(err)));
      return false;
    }
  };

  const testarLigacao = async (): Promise<boolean> => {
    setTestando(true);
    try {
      const res = await apiRequest<{ sucesso: boolean; mensagem?: string; erro?: string }>(
        '/api/ia/testar',
        { method: 'POST' }
      );
      if (res.sucesso) {
        toast('Ligação de IA testada com sucesso!');
        await carregarStatus();
        return true;
      } else {
        toast('Falha no teste: ' + (res.erro || 'Erro desconhecido'));
        return false;
      }
    } catch (err) {
      toast('Erro ao testar IA: ' + (err instanceof Error ? err.message : String(err)));
      return false;
    } finally {
      setTestando(false);
    }
  };

  return {
    status,
    loading,
    testando,
    recarregar: carregarStatus,
    atualizarConfig,
    testarLigacao,
  };
}
