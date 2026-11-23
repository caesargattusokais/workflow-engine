import { useState } from 'react';
import { useT } from '../i18n';
import { CheckCircle2, XCircle, ChevronDown, ChevronRight, ClipboardList, AlertCircle, UserRound } from 'lucide-react';

interface FormField {
  key: string;
  label?: string;
  type?: string;
  required?: boolean;
  options?: string[];
  placeholder?: string;
}

interface TaskInfo {
  id: string;
  nodeId: string;
  assignee: string;
  status: string;
  formFields?: FormField[];
  variables?: Record<string, unknown>;
}

export default function TaskPanel({ tasks, onComplete, onReject }:
    {
      tasks: TaskInfo[],
      onComplete: (id: string, vars: Record<string, unknown>, comment: string) => void,
      onReject: (id: string) => void,
    }) {
  const { t } = useT();
  const [openId, setOpenId] = useState<string | null>(null);
  const [values, setValues] = useState<Record<string, Record<string, string>>>({});
  const [comments, setComments] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (tasks.length === 0) return (
    <div className="bg-gray-800 border-t border-gray-700/70 px-4 py-4 text-[11px] text-gray-600 text-center">
      <ClipboardList size={18} strokeWidth={1.5} className="mx-auto mb-1.5 text-gray-700" />
      {t.monitor.noTasks}
    </div>
  );

  const fieldValue = (task: TaskInfo, f: FormField): string => {
    if (values[task.id] && values[task.id][f.key] !== undefined) return values[task.id][f.key];
    const init = task.variables && task.variables[f.key];
    return init != null ? String(init) : '';
  };

  const setField = (taskId: string, key: string, v: string) => {
    setValues(prev => ({ ...prev, [taskId]: { ...(prev[taskId] || {}), [key]: v } }));
    setErrors(prev => ({ ...prev, [taskId]: '' }));
  };

  const submit = (task: TaskInfo) => {
    const fields = task.formFields || [];
    const vars: Record<string, unknown> = {};
    const missing: string[] = [];
    for (const f of fields) {
      if (!f.key) continue;
      let v = fieldValue(task, f);
      if (f.type === 'number' && v.trim() !== '') v = v.trim();
      if (f.required && !v.trim()) {
        missing.push(f.label || f.key);
      }
      if (v.trim() !== '') vars[f.key] = v;
    }
    if (missing.length > 0) {
      setErrors(prev => ({ ...prev, [task.id]: `${t.monitor.requiredFields}: ${missing.join(', ')}` }));
      return;
    }
    onComplete(task.id, vars, comments[task.id] || '');
    setOpenId(null);
  };

  const inputCls = 'w-full bg-gray-900/70 border border-gray-700 rounded-md px-2.5 py-1.5 ' +
    'text-xs text-gray-200 placeholder:text-gray-600 hover:border-gray-600 ' +
    'focus:border-blue-500 transition-colors';

  return (
    <div className="bg-gray-800 border-t border-gray-700/70 p-3">
      <div className="flex items-center gap-2 mb-2 px-1">
        <ClipboardList size={12} className="text-gray-500" strokeWidth={2} />
        <span className="text-[11px] uppercase tracking-wider text-gray-500 font-medium">
          {t.monitor.tasks} ({tasks.length})
        </span>
      </div>
      <div className="space-y-1.5">
        {tasks.map(task => {
          const fields = task.formFields || [];
          const hasForm = fields.length > 0;
          const expanded = openId === task.id;
          return (
            <div key={task.id} className="bg-gray-750/50 border border-gray-700/60 rounded-lg overflow-hidden">
              <div className="flex items-center gap-3 px-3 py-2">
                <button
                  className="flex items-center gap-1.5 text-xs text-gray-200 font-medium hover:text-white min-w-0"
                  onClick={() => hasForm && setOpenId(expanded ? null : task.id)}
                >
                  {hasForm
                    ? (expanded ? <ChevronDown size={13} className="text-cyan-400 shrink-0" /> : <ChevronRight size={13} className="text-cyan-400 shrink-0" />)
                    : <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 shrink-0" />}
                  <span className="truncate">{task.nodeId}</span>
                  {hasForm && <span className="text-[10px] text-cyan-500/80 font-mono shrink-0">({fields.length})</span>}
                </button>
                <span className="flex items-center gap-1 text-[10px] text-gray-600 ml-auto shrink-0">
                  <UserRound size={10} /> {task.assignee}
                </span>
                <div className="flex gap-1.5">
                  {hasForm && (
                    <button onClick={() => setOpenId(expanded ? null : task.id)}
                      className="btn-ghost !bg-cyan-900/40 !text-cyan-300 hover:!bg-cyan-700 hover:!text-white">
                      {t.monitor.fillForm}
                    </button>
                  )}
                  {!hasForm && (
                    <button onClick={() => onComplete(task.id, {}, '')} className="btn-success">
                      <CheckCircle2 size={12} strokeWidth={2} />
                      {t.monitor.complete}
                    </button>
                  )}
                  <button onClick={() => onReject(task.id)}
                    className="btn-ghost hover:!bg-red-600 hover:!text-white">
                    <XCircle size={12} strokeWidth={2} />
                    {t.monitor.reject}
                  </button>
                </div>
              </div>
              {hasForm && expanded && (
                <div className="px-3 py-3 bg-gray-900/40 border-t border-gray-700/60 space-y-3">
                  {fields.map((f, i) => (
                    <label key={i} className="block">
                      <span className="text-[11px] text-gray-400 font-medium">
                        {f.label || f.key}
                        {f.required && <span className="text-red-400 ml-0.5">*</span>}
                      </span>
                      {f.type === 'textarea' ? (
                        <textarea className={inputCls + ' mt-1.5 resize-none leading-relaxed'}
                          rows={3} value={fieldValue(task, f)} placeholder={f.placeholder || ''}
                          onChange={ev => setField(task.id, f.key, ev.target.value)} />
                      ) : f.type === 'select' ? (
                        <select className={inputCls + ' mt-1.5'}
                          value={fieldValue(task, f)} onChange={ev => setField(task.id, f.key, ev.target.value)}>
                          <option value="">--</option>
                          {(f.options || []).map((o, oi) => <option key={oi} value={o}>{o}</option>)}
                        </select>
                      ) : (
                        <input className={inputCls + ' mt-1.5'}
                          type={f.type === 'number' ? 'number' : f.type === 'date' ? 'date' : 'text'}
                          value={fieldValue(task, f)} placeholder={f.placeholder || ''}
                          onChange={ev => setField(task.id, f.key, ev.target.value)} />
                      )}
                    </label>
                  ))}
                  <label className="block">
                    <span className="text-[11px] text-gray-400 font-medium">{t.monitor.comment}</span>
                    <input className={inputCls + ' mt-1.5'}
                      value={comments[task.id] || ''} placeholder={t.monitor.commentPlaceholder}
                      onChange={ev => setComments(prev => ({ ...prev, [task.id]: ev.target.value }))} />
                  </label>
                  {errors[task.id] && (
                    <div className="flex items-center gap-1.5 text-[11px] text-red-400">
                      <AlertCircle size={12} /> {errors[task.id]}
                    </div>
                  )}
                  <button onClick={() => submit(task)} className="btn-success">
                    <CheckCircle2 size={13} strokeWidth={2} />
                    {t.monitor.submitForm}
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
