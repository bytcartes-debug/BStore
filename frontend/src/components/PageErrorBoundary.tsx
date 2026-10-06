import { Component } from 'react';
import type { ReactNode } from 'react';
import { LoadError } from './UI';

export default class PageErrorBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return (
        <LoadError
          message="Não foi possível abrir esta página. Recarregue para tentar novamente."
          retry={() => window.location.reload()}
        />
      );
    }
    return this.props.children;
  }
}
