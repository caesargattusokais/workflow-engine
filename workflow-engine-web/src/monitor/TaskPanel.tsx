import { useState } from 'react';
import { useT } from '../i18n';

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
    <div className="bg-gray-800 border-t border-gray-700 p-3 text-xs text-gray-500">
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

  return (
    <div className="bg-gray-800 border-t border-gray-700 p-3">
      <div className="text-xs text-gray-400 mb-2">{t.monitor.tasks}</div>
      {tasks.map(task => {
        const fields = task.formFields || [];
        const hasForm = fields.length > 0;
        const expanded = openId === task.id;
        return (
          <div key={task.id} className="border-b border-gray-700 last:border-b-0 py-2">
            <div className="flex items-center justify-between text-sm">
              <button className="text-gray-300 hover:text-white flex items-center gap-1"
                onClick={() => hasForm && setOpenId(expanded ? null : task.id)}>
                {hasForm && <span className="text-cyan-400 text-xs">{expanded ? '▾' : '▸'}</span>}
                {task.nodeId}
                {hasForm && <span className="text-cyan-500 text-[10px] ml-1">({fields.length})</span>}
              </button>
              <span className="text-gray-500">&rarr; {task.assignee}</span>
              <div className="flex gap-2">
                {hasForm && (
                  <button onClick={() => setOpenId(expanded ? null : task.id)}
                    className="bg-cyan-700 hover:bg-cyan-600 text-white text-xs px-2 py-0.5 rounded">
                    {t.monitor.fillForm}
                  </button>
                )}
                {!hasForm && (
                  <button onClick={() => onComplete(task.id, {}, '')}
                    className="bg-green-600 hover:bg-green-500 text-white text-xs px-2 py-0.5 rounded">
                    {t.monitor.complete}
                  </button>
                )}
                <button onClick={() => onReject(task.id)}
                  className="bg-red-600 hover:bg-red-500 text-white text-xs px-2 py-0.5 rounded">
                  {t.monitor.reject}
                </button>
              </div>
            </div>
            {hasForm && expanded && (
              <div className="mt-2 ml-4 bg-gray-750/60 rounded p-2 border border-gray-700">
                {fields.map((f, i) => (
                  <label key={i} className="block mb-2">
                    <span className="text-gray-400 text-xs">
                      {f.label || f.key}
                      {f.required && <span className="text-red-400 ml-0.5">*</span>}
                    </span>
                    {f.type === 'textarea' ? (
                      <textarea className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1"
                        rows={3} value={fieldValue(task, f)} placeholder={f.placeholder || ''}
                        onChange={ev => setField(task.id, f.key, ev.target.value)} />
                    ) : f.type === 'select' ? (
                      <select className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1"
                        value={fieldValue(task, f)} onChange={ev => setField(task.id, f.key, ev.target.value)}>
                        <option value="">--</option>
                        {(f.options || []).map((o, oi) => <option key={oi} value={o}>{o}</option>)}
                      </select>
                    ) : (
                      <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1"
                        type={f.type === 'number' ? 'number' : f.type === 'date' ? 'date' : 'text'}
                        value={fieldValue(task, f)} placeholder={f.placeholder || ''}
                        onChange={ev => setField(task.id, f.key, ev.target.value)} />
                    )}
                  </label>
                ))}
                <label className="block mb-2">
                  <span className="text-gray-400 text-xs">{t.monitor.comment}</span>
                  <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1"
                    value={comments[task.id] || ''} placeholder={t.monitor.commentPlaceholder}
                    onChange={ev => setComments(prev => ({ ...prev, [task.id]: ev.target.value }))} />
                </label>
                {errors[task.id] && <div className="text-red-400 text-xs mb-2">{errors[task.id]}</div>}
                <button onClick={() => submit(task)}
                  className="bg-green-600 hover:bg-green-500 text-white text-xs px-3 py-1 rounded">
                  {t.monitor.submitForm}
                </button>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
