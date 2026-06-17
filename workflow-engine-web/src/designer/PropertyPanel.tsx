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
  const isGateway = node.type === 'exclusiveGateway' || node.type === 'inclusiveGateway';

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
        <label className="block mb-2">
          <span className="text-gray-400 text-xs">Handler Class</span>
          <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
            value={(node.data.handlerClass as string) || ''}
            placeholder="com.myapp.Handler"
            onChange={e => updateData('handlerClass', e.target.value)} />
        </label>
      )}

      {/* ── Gateways: Conditions ──────────── */}
      {isGateway && (
        <div className="border-t border-gray-700 pt-3 mt-2">
          <div className="flex items-center justify-between mb-2">
            <span className="text-gray-400 text-xs">
              {node.type === 'exclusiveGateway' ? '条件列表 (按顺序匹配)' : '条件列表 (可多选)'}
            </span>
            <button onClick={addCondition}
              className="text-xs bg-blue-600 hover:bg-blue-500 text-white px-2 py-0.5 rounded">
              + Add
            </button>
          </div>

          {conditions.length === 0 && (
            <div className="text-xs text-gray-600 italic mb-2">
              点击 + Add 添加条件
            </div>
          )}

          {conditions.map((c, i) => (
            <div key={i} className="mb-3 p-2 bg-gray-750 rounded border border-gray-700">
              <div className="flex items-center justify-between mb-1">
                <span className="text-[10px] text-gray-500">条件 #{i + 1}</span>
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
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1"
                  value={c.expr} placeholder="SpEL: days > 3"
                  onChange={e => updateCondition(i, 'expr', e.target.value)} />
              )}
              {c.isDefault && (
                <div className="text-[10px] text-gray-500 mb-1">Fallback — 无匹配时走此路</div>
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
