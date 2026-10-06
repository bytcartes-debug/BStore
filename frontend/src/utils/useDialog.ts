import { useEffect } from 'react';
import type { RefObject } from 'react';

export function useDialog(ref: RefObject<HTMLDialogElement | null>, open = true) {
  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) return;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    const trapFocus = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const controls = [
        ...dialog.querySelectorAll<HTMLElement>(
          'button, input, select, textarea, a[href], [tabindex]',
        ),
      ].filter(
        (element) =>
          element.tabIndex >= 0 &&
          !element.matches(':disabled') &&
          element.getClientRects().length > 0,
      );
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (!first) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      if (
        event.shiftKey &&
        (document.activeElement === first || document.activeElement === dialog)
      ) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    dialog.showModal();
    dialog.addEventListener('keydown', trapFocus);
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.removeEventListener('keydown', trapFocus);
      dialog.close();
      document.body.style.overflow = overflow;
      if (previous?.isConnected) previous.focus();
    };
  }, [ref, open]);
}
