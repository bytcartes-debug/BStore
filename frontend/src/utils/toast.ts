import { createContext, useContext } from 'react';

export type ToastOptions = {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  duration?: number;
};

export type ToastInput = string | ToastOptions;

export const ToastContext = createContext<(input: ToastInput) => void>(() => {});
export const useToast = () => useContext(ToastContext);

