import { useState, useCallback } from 'react';

export interface Toast {
  id: number;
  message: string;
  type: 'error' | 'success' | 'info';
}

let nextId = 0;
let addToastFn: ((toast: Omit<Toast, 'id'>) => void) | null = null;

/** Show a toast notification from anywhere (even outside React) */
export function showToast(message: string, type: Toast['type'] = 'error') {
  if (addToastFn) addToastFn({ message, type });
  else console.warn('[Toast]', message);
}

/** React hook for toast state */
export function useToasts() {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((toast: Omit<Toast, 'id'>) => {
    const id = nextId++;
    setToasts(prev => [...prev, { ...toast, id }]);
    addToastFn = (t) => {
      const tid = nextId++;
      setToasts(prev => [...prev, { ...t, id: tid }]);
    };
    // Auto-dismiss after 5s
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 5000);
  }, []);

  const removeToast = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return { toasts, addToast, removeToast };
}

/** Toast container — render once at app root */
export function ToastContainer({ toasts, removeToast }: { toasts: Toast[]; removeToast: (id: number) => void }) {
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      {toasts.map(t => (
        <div key={t.id}
          onClick={() => removeToast(t.id)}
          className={`px-4 py-2.5 rounded-lg shadow-lg text-sm cursor-pointer transition-all
            ${t.type === 'error' ? 'bg-red-600 text-white' :
              t.type === 'success' ? 'bg-green-600 text-white' :
              'bg-blue-600 text-white'}`}>
          {t.message}
        </div>
      ))}
    </div>
  );
}
