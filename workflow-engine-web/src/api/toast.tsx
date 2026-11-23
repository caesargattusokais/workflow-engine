import { useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

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
    <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2.5 max-w-sm">
      {toasts.map(t => (
        <div key={t.id}
          onClick={() => removeToast(t.id)}
          className={`flex items-center gap-2.5 px-3.5 py-3 rounded-lg shadow-popover text-xs cursor-pointer
            border backdrop-blur-sm transition-all hover:translate-y-[-1px] max-w-sm
            ${t.type === 'error' ? 'bg-red-600 border-red-800/60 text-red-200' :
              t.type === 'success' ? 'bg-green-600 border-emerald-800/60 text-emerald-200' :
              'bg-blue-600 border-slate-700/60 text-slate-200'}`}
          style={{ backgroundColor: t.type === 'error' ? 'rgba(69,10,10,0.96)'
            : t.type === 'success' ? 'rgba(6,46,31,0.96)' : 'rgba(30,41,59,0.96)' }}>
          {t.type === 'error'
            ? <AlertCircle size={15} className="shrink-0 text-red-400" strokeWidth={2} />
            : t.type === 'success'
              ? <CheckCircle2 size={15} className="shrink-0 text-emerald-400" strokeWidth={2} />
              : <Info size={15} className="shrink-0 text-blue-400" strokeWidth={2} />}
          <span className="flex-1 leading-relaxed">{t.message}</span>
          <button onClick={(e) => { e.stopPropagation(); removeToast(t.id); }} className="shrink-0 opacity-60 hover:opacity-100">
            <X size={13} strokeWidth={2} />
          </button>
        </div>
      ))}
    </div>
  );
}
