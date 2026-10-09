import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { LoadError } from './UI';

interface Props {
  children: ReactNode;
}

interface State {
  failed: boolean;
  error: Error | null;
}

export default class PageErrorBoundary extends Component<Props, State> {
  state: State = { failed: false, error: null };

  static getDerivedStateFromError(error: Error) {
    return { failed: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Erro na renderização da página:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ failed: false, error: null });
  };

  render() {
    if (this.state.failed) {
      return (
        <LoadError
          message="Não foi possível abrir esta página. Tente novamente."
          retry={this.handleRetry}
        />
      );
    }
    return this.props.children;
  }
}
