import type { Node } from '@xyflow/react';

interface ConditionItem {
  expr: string;
  to: string;
  isDefault: boolean;
}

export default function PropertyPanel({ node, onChange }: {
    node: Node | null;
    onChange: (node: Node) => void;
}) {
  if (!node) {
    return (
      <div className="w-64 bg-gray-800 border-l border-gray-700 p-4 text-sm text-gray-500">
        Select a node to edit
      </div>
    );
  }

  const updateData = (key: string, value: unknown) => {
    onChange({ ...node, data: { ...node.data, [key]: value } });
  };

  const conditions: ConditionItem[] = (node.data.conditions as ConditionItem[]) || [];

  const addCondition = () => {
    updateData('conditions', [...conditions, { expr: '', to: '', isDefault: false }]);
  };

  const removeCondition = (idx: number) => {
    updateData('conditions', conditions.filter((_, i) => i !== idx));
  };

  const updateCondition = (idx: number, field: keyof ConditionItem, value: unknown) => {
    const updated = conditions.map((c, i) => i === idx ? { ...c, [field]: value } : c);
    // If setting as default, un-default others
    if (field === 'isDefault' && value === true) {
      updated.forEach((c, i) => { if (i !== idx) c.isDefault = false; });
    }
    updateData('conditions', updated);
  };

  return (
    <div className="w-64 bg-gray-800 border-l border-gray-700 p-4 text-sm overflow-y-auto">
      <h3 className="text-gray-300 font-bold mb-1 capitalize">
        {node.type === 'exclusiveGateway' ? '判断网关' :
         node.type === 'inclusiveGateway' ? '条件分支网关' :
         node.type === 'parallelGateway' ? '并行网关' :
         node.type === 'userTask' ? '用户任务' :
         node.type === 'serviceTask' ? '服务任务' :
         node.type === 'startEvent' ? '开始事件' :
         node.type === 'endEvent' ? '结束事件' : node.type}
      </h3>
      <div className="text-[10px] text-gray-600 mb-3">{node.type}</div>

      {/* Name (all nodes) */}
      <label className="block mb-3">
        <span className="text-gray-400 text-xs">Name</span>
        <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
          value={(node.data.name as string) || ''}
          onChange={e => updateData('name', e.target.value)} />
      </label>

      {/* ── StartEvent: Initial Variables ──── */}
      {node.type === 'startEvent' && (
        <div className="border-t border-green-500/50 pt-3 mt-2">
          <span className="text-green-400 text-xs font-semibold">初始变量 (Initial Variables)</span>
          <div className="text-[10px] text-gray-500 mb-2">启动流程时传入的变量，在条件中可用</div>
          <VarEditor
            vars={(node.data.initialVars as string[]) || []}
            onChange={v => updateData('initialVars', v)}
          />
        </div>
      )}

      {/* ── UserTask ──────────────────────── */}
      {node.type === 'userTask' && (
        <>
          <label className="block mb-2">
            <span className="text-gray-400 text-xs">Assignee</span>
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
              value={(node.data.assignee as string) || ''} placeholder="e.g. ${applicant}"
              onChange={e => updateData('assignee', e.target.value)} />
          </label>
          <label className="block mb-2">
            <span className="text-gray-400 text-xs">Candidate Groups</span>
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
              value={(Array.isArray(node.data.candidateGroups) ? (node.data.candidateGroups as string[]).join(', ') : '')}
              placeholder="comma-separated"
              onChange={e => updateData('candidateGroups', e.target.value.split(',').map(s => s.trim()))} />
          </label>
        </>
      )}

      {/* ── ServiceTask ───────────────────── */}
      {node.type === 'serviceTask' && (
        <div className="border-t border-gray-700 pt-3 mt-2">
          {/* Toggle: Code / HTTP */}
          <div className="flex items-center gap-2 mb-3">
            <span className="text-gray-400 text-xs">Type:</span>
            <button
              onClick={() => updateData('httpMode', false)}
              className={`text-xs px-2 py-0.5 rounded ${!(node.data.httpMode as boolean)
                ? 'bg-purple-600 text-white' : 'bg-gray-700 text-gray-500'}`}>
              代码逻辑
            </button>
            <button
              onClick={() => updateData('httpMode', true)}
              className={`text-xs px-2 py-0.5 rounded ${(node.data.httpMode as boolean)
                ? 'bg-teal-600 text-white' : 'bg-gray-700 text-gray-500'}`}>
              HTTP 调用
            </button>
          </div>

          {!(node.data.httpMode as boolean) ? (
            <label className="block mb-2">
              <span className="text-gray-400 text-xs">Handler Class</span>
              <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
                value={(node.data.handlerClass as string) || ''}
                placeholder="com.myapp.Handler"
                onChange={e => updateData('handlerClass', e.target.value)} />
            </label>
          ) : (
            <>
              <label className="block mb-2">
                <span className="text-teal-400 text-xs">URL</span>
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 font-mono"
                  value={(node.data.url as string) || ''}
                  placeholder="https://api.example.com/check"
                  onChange={e => updateData('url', e.target.value)} />
              </label>
              <div className="flex gap-2 mb-2">
                <label className="flex-1">
                  <span className="text-gray-400 text-xs">Method</span>
                  <select className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                    value={(node.data.method as string) || 'POST'}
                    onChange={e => updateData('method', e.target.value)}>
                    {['GET','POST','PUT','DELETE','PATCH'].map(m => (
                      <option key={m} value={m}>{m}</option>
                    ))}
                  </select>
                </label>
              </div>
              <label className="block mb-2">
                <span className="text-gray-400 text-xs">Headers (JSON)</span>
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 font-mono"
                  value={JSON.stringify(node.data.headers || {})}
                  placeholder='{"Authorization":"Bearer ${token}"}'
                  onChange={e => {
                    try { updateData('headers', JSON.parse(e.target.value)); }
                    catch { /* invalid JSON, ignore */ }
                  }} />
              </label>
              <label className="block mb-2">
                <span className="text-gray-400 text-xs">Body Template</span>
                <textarea className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 font-mono"
                  rows={3} value={(node.data.body as string) || ''}
                  placeholder='{"amount": ${amount}, "type": "risk"}'
                  onChange={e => updateData('body', e.target.value)} />
              </label>
            </>
          )}
        </div>
      )}

      {/* ── ExclusiveGateway: 判断节点 ──── */}
      {node.type === 'exclusiveGateway' && (
        <div className="border-t border-orange-500/50 pt-3 mt-2">
          <div className="flex items-center justify-between mb-1">
            <span className="text-orange-400 text-xs font-semibold">条件判断 — 只走第一条命中的路</span>
            <button onClick={addCondition}
              className="text-xs bg-orange-600 hover:bg-orange-500 text-white px-2 py-0.5 rounded">
              + Add
            </button>
          </div>
          <div className="text-[10px] text-gray-500 mb-2">从上到下依次判断，命中即停止，都不命中走 default</div>

          {conditions.map((c, i) => (
            <div key={i} className={`mb-2 p-2 rounded border ${c.isDefault ? 'bg-gray-750 border-gray-600' : 'bg-gray-750 border-orange-800'}`}>
              <div className="flex items-center justify-between mb-1">
                <span className={`text-[10px] ${c.isDefault ? 'text-gray-400' : 'text-orange-400'}`}>
                  {c.isDefault ? '默认 (兜底)' : `判断 ${i + 1}`}
                </span>
                <div className="flex gap-1 items-center">
                  <label className="text-[10px] text-gray-500 flex items-center gap-1">
                    <input type="checkbox" checked={c.isDefault}
                      onChange={e => updateCondition(i, 'isDefault', e.target.checked)}
                      className="accent-orange-500" />
                    default
                  </label>
                  <button onClick={() => removeCondition(i)}
                    className="text-[10px] text-red-400 hover:text-red-300 ml-1">&times;</button>
                </div>
              </div>
              {!c.isDefault && (
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1 font-mono"
                  value={c.expr} placeholder="SpEL: days > 3"
                  onChange={e => updateCondition(i, 'expr', e.target.value)} />
              )}
              <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs"
                value={c.to} placeholder="目标节点 ID"
                onChange={e => updateCondition(i, 'to', e.target.value)} />
            </div>
          ))}
        </div>
      )}

      {/* ── InclusiveGateway: 条件分支节点 ──── */}
      {node.type === 'inclusiveGateway' && (
        <div className="border-t border-purple-500/50 pt-3 mt-2">
          <div className="flex items-center justify-between mb-1">
            <span className="text-purple-400 text-xs font-semibold">条件分支 — 满足条件的全部并行</span>
            <button onClick={addCondition}
              className="text-xs bg-purple-600 hover:bg-purple-500 text-white px-2 py-0.5 rounded">
              + Add
            </button>
          </div>
          <div className="text-[10px] text-gray-500 mb-2">每条独立判断，满足就走对应分支，可能同时走多条</div>

          {conditions.map((c, i) => (
            <div key={i} className={`mb-2 p-2 rounded border ${c.isDefault ? 'bg-gray-750 border-gray-600' : 'bg-gray-750 border-purple-800'}`}>
              <div className="flex items-center justify-between mb-1">
                <span className={`text-[10px] ${c.isDefault ? 'text-gray-400' : 'text-purple-400'}`}>
                  {c.isDefault ? '兜底 (无匹配时)' : `分支 ${i + 1}`}
                </span>
                <div className="flex gap-1 items-center">
                  <label className="text-[10px] text-gray-500 flex items-center gap-1">
                    <input type="checkbox" checked={c.isDefault}
                      onChange={e => updateCondition(i, 'isDefault', e.target.checked)}
                      className="accent-purple-500" />
                    default
                  </label>
                  <button onClick={() => removeCondition(i)}
                    className="text-[10px] text-red-400 hover:text-red-300 ml-1">&times;</button>
                </div>
              </div>
              {!c.isDefault && (
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1 font-mono"
                  value={c.expr} placeholder="SpEL: amount > 1000"
                  onChange={e => updateCondition(i, 'expr', e.target.value)} />
              )}
              <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs"
                value={c.to} placeholder="目标节点 ID"
                onChange={e => updateCondition(i, 'to', e.target.value)} />
            </div>
          ))}
        </div>
      )}

      {/* ── ParallelGateway: info ──────────── */}
      {node.type === 'parallelGateway' && (
        <div className="border-t border-gray-700 pt-3 mt-2">
          <div className="text-xs text-gray-500">
            并行网关自动分叉到所有连线目标，无需配置。
            在画布上直接连线即可。
          </div>
        </div>
      )}

      <div className="mt-4 pt-3 border-t border-gray-700">
        <span className="text-gray-500 text-xs">ID: {node.id}</span>
      </div>
    </div>
  );
}

// ── Reusable variable editor (add/remove text inputs) ────
function VarEditor({ vars, onChange }: { vars: string[]; onChange: (v: string[]) => void }) {
  const add = () => onChange([...vars, '']);
  const remove = (i: number) => onChange(vars.filter((_, idx) => idx !== i));
  const update = (i: number, val: string) => {
    const copy = [...vars];
    copy[i] = val;
    onChange(copy);
  };

  return (
    <div>
      {vars.map((v, i) => (
        <div key={i} className="flex gap-1 mb-1">
          <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
            value={v} placeholder="e.g. applicant"
            onChange={e => update(i, e.target.value)} />
          <button onClick={() => remove(i)}
            className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
      <button onClick={add}
        className="text-xs text-green-400 hover:text-green-300 mt-1">+ Add variable</button>
    </div>
  );
}
