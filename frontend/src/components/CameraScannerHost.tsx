import React, { lazy, Suspense, useEffect, useState } from 'react';
import { registerScannerUI, resolverScannerWeb } from '../utils/scanner';

const CameraScannerModal = lazy(() =>
  import('./CameraScannerModal').then((m) => ({ default: m.CameraScannerModal })),
);

export const CameraScannerHost: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);

  useEffect(() => {
    return registerScannerUI((open) => setIsOpen(open));
  }, []);

  if (!isOpen) return null;

  return (
    <Suspense fallback={null}>
      <CameraScannerModal
        isOpen={isOpen}
        onScan={(codigo) => resolverScannerWeb(codigo)}
        onClose={() => resolverScannerWeb(null)}
      />
    </Suspense>
  );
};
