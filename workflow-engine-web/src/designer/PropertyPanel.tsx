import { useState, useCallback, useRef, useEffect } from 'react';
import type { Node } from '@xyflow/react';
import { useT } from '../i18n';

interface ConditionItem {
  expr: string;
  to: string;
  isDefault: boolean;
}

import type { Edge } from '@xyflow/react';
import OrgTreePicker from './OrgTreePicker';

export default function PropertyPanel({ node, onChange, edges, onSelectEdge, onEdgesChange }: {
    node: Node | null;
    onChange: (node: Node) => void;
    edges?: Edge[];
    onSelectEdge?: (edgeId: string) => void;
    onEdgesChange?: (edges: Edge[]) => void;
}) {
  const { t } = useT();
  const [width, setWidth] = useState(280);
  const dragging = useRef(false);
  const startX = useRef(0);
  const startW = useRef(0);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    dragging.current = true;
    startX.current = e.clientX;
    startW.current = width;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, [width]);

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!dragging.current) return;
      const delta = startX.current - e.clientX;
      setWidth(Math.max(200, Math.min(600, startW.current + delta)));
    };
    const onUp = () => {
      dragging.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
  }, []);

  if (!node) {
    return (
      <div className="bg-gray-800 border-l border-gray-700 p-4 text-sm text-gray-500" style={{ width, minWidth: width }}>
        {t.props.selectNode}
      </div>
    );
  }

  const updateData = (key: string, value: unknown) => {
    const newData = { ...(node.data || {}), [key]: value };
    onChange({ id: node.id, type: node.type, position: node.position, data: newData } as Node);
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
    if (field === 'isDefault' && value === true) {
      updated.forEach((c, i) => { if (i !== idx) c.isDefault = false; });
    }
    updateData('conditions', updated);
  };

  return (
    <div className="bg-gray-800 border-l border-gray-700 overflow-y-auto flex-shrink-0 relative"
         style={{ width, minWidth: width }}>
      {/* Resize handle — left edge */}
      <div
        onMouseDown={onMouseDown}
        className="absolute left-0 top-0 bottom-0 w-1.5 cursor-col-resize hover:bg-blue-500/50 transition-colors z-10"
        style={{ marginLeft: -1 }}
      />

      <div className="p-4 text-sm">
        <h3 className="text-gray-300 font-bold mb-1 capitalize">
          {node.type === 'exclusiveGateway' ? t.nodes.exclusiveGateway :
           node.type === 'inclusiveGateway' ? t.nodes.inclusiveGateway :
           node.type === 'parallelGateway' ? t.nodes.parallelGateway :
           node.type === 'userTask' ? t.nodes.userTask :
           node.type === 'serviceTask' ? t.nodes.serviceTask :
           node.type === 'startEvent' ? t.nodes.startEvent :
           node.type === 'endEvent' ? t.nodes.endEvent :
           node.type === 'timer' ? t.nodes.timer : node.type}
        </h3>
        <div className="text-[10px] text-gray-600 mb-3">{node.type}</div>

        {/* Name (all nodes) */}
        <label className="block mb-3">
          <span className="text-gray-400 text-xs">{t.props.name}</span>
          <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
            value={(node.data.name as string) || ''}
            onChange={e => updateData('name', e.target.value)} />
        </label>

        {/* ── StartEvent: Initial Variables ──── */}
        {node.type === 'startEvent' && (
          <div className="border-t border-green-500/50 pt-3 mt-2">
            <span className="text-green-400 text-xs font-semibold">{t.props.initialVars}</span>
            <div className="text-[10px] text-gray-500 mb-2">{t.props.initialVarsHint}</div>
            <KvEditor label="" entries={(node.data.initialVars as Array<{key:string;value:string}>) || []}
              onChange={v => updateData('initialVars', v)}
              keyPlaceholder="变量名" valPlaceholder="默认值" />
          </div>
        )}

        {/* ── UserTask ──────────────────────── */}
        {node.type === 'userTask' && (
          <>
            <div className="flex items-center gap-2 mb-3">
              <span className="text-gray-400 text-xs">{t.props.mode}:</span>
              <button onClick={() => updateData('httpMode', false)}
                className={`text-xs px-2 py-0.5 rounded ${!(node.data.httpMode as boolean) ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-500'}`}>
                {t.props.localTask}
              </button>
              <button onClick={() => updateData('httpMode', true)}
                className={`text-xs px-2 py-0.5 rounded ${(node.data.httpMode as boolean) ? 'bg-teal-600 text-white' : 'bg-gray-700 text-gray-500'}`}>
                {t.props.httpCallback}
              </button>
            </div>

            {!(node.data.httpMode as boolean) ? (
              <>
                <label className="block mb-2">
                  <span className="text-gray-400 text-xs">{t.props.assignee}</span>
                  <OrgTreePicker value={(node.data.assignee as string) || ''}
                    onChange={v => updateData('assignee', v)} />
                </label>
                <label className="block mb-2">
                  <span className="text-gray-400 text-xs">{t.props.candidateGroups}</span>
                  <OrgTreePicker values={(node.data.candidateGroups as string[]) || []}
                    onChangeMulti={v => updateData('candidateGroups', v)} multi />
                </label>
              </>
            ) : (
              <>
                <div className="text-[10px] text-gray-500 mb-2">{t.props.callbackHint}</div>
                <label className="block mb-2">
                  <span className="text-teal-400 text-xs">{t.props.url}</span>
                  <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 font-mono"
                    value={(node.data.url as string) || ''} placeholder="https://oa.company.com/api/approval"
                    onChange={e => updateData('url', e.target.value)} />
                </label>
                <div className="flex gap-2 mb-2">
                  <label className="flex-1">
                    <span className="text-gray-400 text-xs">{t.props.method}</span>
                    <select className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                      value={(node.data.method as string) || 'POST'}
                      onChange={e => updateData('method', e.target.value)}>
                      {['GET','POST','PUT','DELETE','PATCH'].map(m => (
                        <option key={m} value={m}>{m}</option>
                      ))}
                    </select>
                  </label>
                </div>
                <KvEditor label={t.props.headers}
                  entries={(node.data.headerEntries as Array<{key:string;value:string}>) || []}
                  onChange={v => updateData('headerEntries', v)}
                  keyPlaceholder="Content-Type" valPlaceholder="application/json" emptyText={t.props.noParams} />
                <KvEditor label={['GET','DELETE'].includes((node.data.method as string)||'POST') ? t.props.queryParams : t.props.bodyParams}
                  entries={(node.data.paramEntries as Array<{key:string;value:string}>) || []}
                  onChange={v => updateData('paramEntries', v)}
                  keyPlaceholder="applicant" valPlaceholder="${applicant}" emptyText={t.props.noParams} />
              </>
            )}

            <div className="border-t border-orange-500/50 pt-2 mt-2">
              <span className="text-orange-400 text-xs font-semibold">{t.props.timeout}</span>
              <div className="text-[10px] text-gray-500 mb-2">{t.props.timeoutHint}</div>
              <label className="block mb-1">
                <span className="text-gray-400 text-xs">{t.props.duration}</span>
                <div className="flex gap-1 mt-0.5">
                  <input type="number" min="1" className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs"
                    value={parseDurationValue(node.data.boundaryTimer as string)}
                    onChange={e => {
                      const val = parseInt(e.target.value) || 0;
                      const unit = parseDurationUnit(node.data.boundaryTimer as string);
                      if (val > 0) updateData('boundaryTimer', `PT${val}${unit}`);
                      else updateData('boundaryTimer', '');
                    }} placeholder="30" />
                  <select className="w-16 bg-gray-700 rounded px-1 py-1 text-white text-xs"
                    value={parseDurationUnit(node.data.boundaryTimer as string)}
                    onChange={e => {
                      const val = parseDurationValue(node.data.boundaryTimer as string) || 1;
                      const newData = { ...(node.data || {}), boundaryTimer: `PT${val}${e.target.value}` };
                      onChange({ id: node.id, type: node.type, position: node.position, data: newData } as Node);
                    }}>
                    <option value="S">{t.props.seconds}</option>
                    <option value="M">{t.props.minutes}</option>
                    <option value="H">{t.props.hours}</option>
                  </select>
                </div>
              </label>
            </div>
          </>
        )}

        {/* ── ServiceTask ───────────────────── */}
        {node.type === 'serviceTask' && (
          <div className="border-t border-gray-700 pt-3 mt-2">
            <div className="flex items-center gap-2 mb-3">
              <span className="text-gray-400 text-xs">Type:</span>
              <button onClick={() => updateData('httpMode', false)}
                className={`text-xs px-2 py-0.5 rounded ${!(node.data.httpMode as boolean) ? 'bg-purple-600 text-white' : 'bg-gray-700 text-gray-500'}`}>
                {t.props.codeLogic}
              </button>
              <button onClick={() => updateData('httpMode', true)}
                className={`text-xs px-2 py-0.5 rounded ${(node.data.httpMode as boolean) ? 'bg-teal-600 text-white' : 'bg-gray-700 text-gray-500'}`}>
                {t.props.httpCall}
              </button>
            </div>

            {!(node.data.httpMode as boolean) ? (
              <>
                <label className="block mb-2">
                  <span className="text-gray-400 text-xs">{t.props.handlerClass}</span>
                  <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
                    value={(node.data.handlerClass as string) || ''} placeholder="com.myapp.Handler"
                    onChange={e => updateData('handlerClass', e.target.value)} />
                </label>
                <KvEditor label={t.props.inputParams}
                  entries={(node.data.paramEntries as Array<{key:string;value:string}>) || []}
                  onChange={v => updateData('paramEntries', v)}
                  keyPlaceholder="paramName" valPlaceholder="${variable} or fixed" emptyText={t.props.noParams} />
              </>
            ) : (
              <>
                <label className="block mb-2">
                  <span className="text-teal-400 text-xs">{t.props.url}</span>
                  <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 font-mono"
                    value={(node.data.url as string) || ''} placeholder="https://api.example.com/check"
                    onChange={e => updateData('url', e.target.value)} />
                </label>
                <div className="flex gap-2 mb-2">
                  <label className="flex-1">
                    <span className="text-gray-400 text-xs">{t.props.method}</span>
                    <select className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                      value={(node.data.method as string) || 'POST'}
                      onChange={e => updateData('method', e.target.value)}>
                      {['GET','POST','PUT','DELETE','PATCH'].map(m => (
                        <option key={m} value={m}>{m}</option>
                      ))}
                    </select>
                  </label>
                </div>
                <KvEditor label={t.props.headers}
                  entries={(node.data.headerEntries as Array<{key:string;value:string}>) || []}
                  onChange={v => updateData('headerEntries', v)}
                  keyPlaceholder="Content-Type" valPlaceholder="application/json" emptyText={t.props.noParams} />
                <KvEditor label={['GET','DELETE'].includes((node.data.method as string)||'POST') ? t.props.queryParams : t.props.bodyParams}
                  entries={(node.data.paramEntries as Array<{key:string;value:string}>) || []}
                  onChange={v => updateData('paramEntries', v)}
                  keyPlaceholder="amount" valPlaceholder="${amount}" emptyText={t.props.noParams} />
              </>
            )}
            {/* Return Values — shared by both modes */}
            {/* ── Retry Config ──────────────── */}
            <div className="border-t border-gray-700 pt-2 mt-2">
              <span className="text-gray-400 text-xs font-semibold">{t.props.retryConfig}</span>
              <div className="text-[10px] text-gray-500 mb-1">Set maxAttempts &gt; 1 to enable retry. Leave at 1 for no retry.</div>
              <label className="block mb-1">
                <span className="text-gray-400 text-xs">{t.props.maxAttempts}</span>
                <input type="number" min="1" className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                  value={(node.data.retryMaxAttempts as number) || 1}
                  onChange={e => updateData('retryMaxAttempts', parseInt(e.target.value)||1)} />
              </label>
              <label className="block mb-1">
                <span className="text-gray-400 text-xs">{t.props.delayMs}</span>
                <input type="number" className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                  value={(node.data.retryDelayMs as number) || 1000}
                  onChange={e => updateData('retryDelayMs', parseInt(e.target.value)||1000)} />
              </label>
              <label className="block mb-2">
                <span className="text-gray-400 text-xs">{t.props.backoff}</span>
                <input type="number" step="0.5" className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                  value={(node.data.retryBackoff as number) || 2}
                  onChange={e => updateData('retryBackoff', parseFloat(e.target.value)||2)} />
              </label>
              <div className="text-gray-400 text-xs mb-1">{t.props.retryOn}</div>
              <RetryOnEditor entries={(node.data.retryOn as any[]) || []}
                onChange={v => updateData('retryOn', v)} addLabel={t.props.addCondition} />
            </div>

            <ReturnValueEditor
              label={t.props.returnValues}
              entries={(node.data.returnValues as Array<{key:string;type:string}>) || []}
              onChange={v => updateData('returnValues', v)}
            />
          </div>
        )}

        {/* ── ExclusiveGateway ──────────────── */}
        {node.type === 'exclusiveGateway' && (
          <div className="border-t border-orange-500/50 pt-3 mt-2">
            <div className="flex items-center justify-between mb-1">
              <span className="text-orange-400 text-xs font-semibold">{t.props.conditionsXor}</span>
              <button onClick={addCondition} className="text-xs bg-orange-600 hover:bg-orange-500 text-white px-2 py-0.5 rounded">{t.props.addCondition}</button>
            </div>
            <div className="text-[10px] text-gray-500 mb-2">{t.props.conditionsHint}</div>
            {conditions.map((c, i) => (
              <div key={i} className={`mb-2 p-2 rounded border ${c.isDefault ? 'bg-gray-750 border-gray-600' : 'bg-gray-750 border-orange-800'}`}>
                <div className="flex items-center justify-between mb-1">
                  <span className={`text-[10px] ${c.isDefault ? 'text-gray-400' : 'text-orange-400'}`}>
                    {c.isDefault ? t.props.defaultBranch : `判断 ${i + 1}`}</span>
                  <div className="flex gap-1 items-center">
                    <label className="text-[10px] text-gray-500 flex items-center gap-1">
                      <input type="checkbox" checked={c.isDefault} onChange={e => updateCondition(i, 'isDefault', e.target.checked)} className="accent-orange-500" /> default
                    </label>
                    <button onClick={() => removeCondition(i)} className="text-[10px] text-red-400 hover:text-red-300 ml-1">&times;</button>
                  </div>
                </div>
                {!c.isDefault && (
                  <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1 font-mono"
                    value={c.expr} placeholder="SpEL: days > 3" onChange={e => updateCondition(i, 'expr', e.target.value)} />
                )}
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs"
                  value={c.to} placeholder="目标节点 ID" onChange={e => updateCondition(i, 'to', e.target.value)} />
              </div>
            ))}
          </div>
        )}

        {/* ── InclusiveGateway ──────────────── */}
        {node.type === 'inclusiveGateway' && (
          <div className="border-t border-purple-500/50 pt-3 mt-2">
            <div className="flex items-center justify-between mb-1">
              <span className="text-purple-400 text-xs font-semibold">{t.props.conditionsOr}</span>
              <button onClick={addCondition} className="text-xs bg-purple-600 hover:bg-purple-500 text-white px-2 py-0.5 rounded">{t.props.addCondition}</button>
            </div>
            <div className="text-[10px] text-gray-500 mb-2">{t.props.conditionsOrHint}</div>
            {conditions.map((c, i) => (
              <div key={i} className={`mb-2 p-2 rounded border ${c.isDefault ? 'bg-gray-750 border-gray-600' : 'bg-gray-750 border-purple-800'}`}>
                <div className="flex items-center justify-between mb-1">
                  <span className={`text-[10px] ${c.isDefault ? 'text-gray-400' : 'text-purple-400'}`}>
                    {c.isDefault ? t.props.fallbackNoMatch : `分支 ${i + 1}`}</span>
                  <div className="flex gap-1 items-center">
                    <label className="text-[10px] text-gray-500 flex items-center gap-1">
                      <input type="checkbox" checked={c.isDefault} onChange={e => updateCondition(i, 'isDefault', e.target.checked)} className="accent-purple-500" /> default
                    </label>
                    <button onClick={() => removeCondition(i)} className="text-[10px] text-red-400 hover:text-red-300 ml-1">&times;</button>
                  </div>
                </div>
                {!c.isDefault && (
                  <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1 font-mono"
                    value={c.expr} placeholder="SpEL: amount > 1000" onChange={e => updateCondition(i, 'expr', e.target.value)} />
                )}
                <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs"
                  value={c.to} placeholder="目标节点 ID" onChange={e => updateCondition(i, 'to', e.target.value)} />
              </div>
            ))}
          </div>
        )}

        {/* ── ParallelGateway: info ──────────── */}
        {node.type === 'parallelGateway' && (
          <div className="border-t border-gray-700 pt-3 mt-2">
            <div className="text-xs text-gray-500">{t.props.gatewayInfo}</div>
          </div>
        )}

        {/* ── Timer ──────────────────────────── */}
        {node.type === 'timer' && (
          <div className="border-t border-amber-500/50 pt-3 mt-2">
            <span className="text-amber-400 text-xs font-semibold">{t.props.timerConfig}</span>
            <div className="text-[10px] text-gray-500 mb-2">{t.props.timerHint}</div>
            {/* Duration: number + unit → ISO 8601 */}
            <label className="block mb-2">
              <span className="text-gray-400 text-xs">{t.props.duration}</span>
              <div className="flex gap-1 mt-0.5">
                <input type="number" min="1" className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs"
                  value={parseDurationValue(node.data.duration as string)}
                  onChange={e => {
                    const val = parseInt(e.target.value) || 0;
                    const unit = parseDurationUnit(node.data.duration as string);
                    if (val > 0) updateData('duration', `PT${val}${unit}`);
                    else updateData('duration', '');
                  }} placeholder="30" />
                <select className="w-16 bg-gray-700 rounded px-1 py-1 text-white text-xs"
                  value={parseDurationUnit(node.data.duration as string)}
                  onChange={e => {
                    const val = parseDurationValue(node.data.duration as string) || 1;
                    const newData = { ...(node.data || {}), duration: `PT${val}${e.target.value}` };
                    onChange({ id: node.id, type: node.type, position: node.position, data: newData } as Node);
                  }}>
                  <option value="S">{t.props.seconds}</option>
                  <option value="M">{t.props.minutes}</option>
                  <option value="H">{t.props.hours}</option>
                </select>
              </div>
            </label>
            {/* Deadline: datetime-local picker → ISO 8601 */}
            <label className="block mb-2">
              <span className="text-gray-400 text-xs">{t.props.deadline}</span>
              <input type="datetime-local"
                className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
                style={{ colorScheme: 'dark' }}
                value={toDatetimeLocal(node.data.deadline as string)}
                onChange={e => {
                  if (e.target.value) {
                    updateData('deadline', new Date(e.target.value).toISOString());
                  } else {
                    updateData('deadline', '');
                  }
                }} />
            </label>
          </div>
        )}

        {/* ── Outgoing Edges ── */}
        {edges && edges.length > 0 && (
          <div className="border-t border-gray-700 pt-2 mt-2">
            <span className="text-gray-400 text-xs font-semibold">{t.props.outgoingEdges}</span>
            {edges.filter(e => e.source === node.id).map(e => {
              const et = (e.data as any)?.edgeType || 'direct';
              const colors: Record<string,string> = { result: '#22c55e', exception: '#ef4444', timeout: '#f97316', conditional: '#e5a50a', default: '#888', direct: '#666' };
              const needsExpr = et === 'result' || et === 'exception' || et === 'conditional';
              const isDefault = (e.data as any)?.isDefault;
              return (
                <div key={e.id} className="mt-1.5 text-xs">
                  <div className="flex items-center gap-1 cursor-pointer hover:bg-gray-750 rounded px-1 py-0.5"
                    onClick={() => onSelectEdge?.(e.id)}>
                    <span className="w-2 h-2 rounded-full inline-block flex-shrink-0" style={{background: colors[et]||'#666'}} />
                    <span className="text-gray-500 w-14 flex-shrink-0">{et}</span>
                    <span className="text-gray-600">→</span>
                    <span className="text-gray-300 truncate">{e.target}</span>
                  </div>
                  {needsExpr && !isDefault && (
                    <input className="w-full bg-gray-700 rounded px-1.5 py-0.5 text-white text-[11px] mt-0.5 font-mono"
                      value={(e.data as any)?.expr || ''}
                      placeholder={et === 'timeout' ? 'PT30M' : 'SpEL expression'}
                      onChange={ev => {
                        const updated = edges.map(ed => ed.id === e.id ? {
                          ...ed, data: { ...ed.data, expr: ev.target.value }
                        } : ed);
                        // Update edges through parent callback
                        onEdgesChange?.(updated);
                      }}
                    />
                  )}
                  {et === 'default' && <div className="text-[10px] text-gray-500 mt-0.5">{t.designer.fallback}</div>}
                </div>
              );
            })}
          </div>
        )}

        <div className="mt-4 pt-3 border-t border-gray-700">
          <span className="text-gray-500 text-xs">ID: {node.id}</span>
        </div>
      </div>
    </div>
  );
}

// ── VarEditor ─────────────────────────
function VarEditor({ vars, onChange, addLabel }: { vars: string[]; onChange: (v: string[]) => void; addLabel: string }) {
  const add = () => onChange([...vars, '']);
  const remove = (i: number) => onChange(vars.filter((_, idx) => idx !== i));
  const update = (i: number, val: string) => {
    const copy = [...vars]; copy[i] = val; onChange(copy);
  };
  return (
    <div>
      {vars.map((v, i) => (
        <div key={i} className="flex gap-1 mb-1">
          <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
            value={v} placeholder="e.g. applicant" onChange={e => update(i, e.target.value)} />
          <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
      <button onClick={add} className="text-xs text-green-400 hover:text-green-300 mt-1">{addLabel}</button>
    </div>
  );
}

// ── KvEditor ──────────────────────────
function KvEditor({ label, entries, onChange, keyPlaceholder, valPlaceholder, emptyText }: {
    label: string; entries: Array<{key: string; value: string}>;
    onChange: (v: Array<{key: string; value: string}>) => void;
    keyPlaceholder: string; valPlaceholder: string;
    emptyText?: string;
}) {
  const add = () => onChange([...entries, { key: '', value: '' }]);
  const remove = (i: number) => onChange(entries.filter((_, idx) => idx !== i));
  const updateKey = (i: number, k: string) => onChange(entries.map((e, idx) => idx === i ? { ...e, key: k } : e));
  const updateVal = (i: number, v: string) => onChange(entries.map((e, idx) => idx === i ? { ...e, value: v } : e));

  return (
    <div className="mb-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-gray-400 text-xs">{label}</span>
        <button onClick={add} className="text-xs text-teal-400 hover:text-teal-300">+ Add</button>
      </div>
      {entries.length === 0 && <div className="text-[10px] text-gray-600 mb-1">{emptyText || 'No params yet'}</div>}
      {entries.map((e, i) => (
        <div key={i} className="flex gap-1 mb-1">
          <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
            value={e.key} placeholder={keyPlaceholder} onChange={ev => updateKey(i, ev.target.value)} />
          <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
            value={e.value} placeholder={valPlaceholder} onChange={ev => updateVal(i, ev.target.value)} />
          <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
    </div>
  );
}

// ── ReturnValue editor (key + type, add/remove) ────
const TYPES = ['String', 'Number', 'Boolean', 'JSON', 'Object'];
function ReturnValueEditor({ label, entries, onChange }: {
    label: string; entries: Array<{key: string; type: string}>;
    onChange: (v: Array<{key: string; type: string}>) => void;
}) {
  const add = () => onChange([...entries, { key: '', type: 'String' }]);
  const remove = (i: number) => onChange(entries.filter((_, idx) => idx !== i));
  const updateKey = (i: number, k: string) => onChange(entries.map((e, idx) => idx === i ? { ...e, key: k } : e));
  const updateType = (i: number, t: string) => onChange(entries.map((e, idx) => idx === i ? { ...e, type: t } : e));

  return (
    <div className="border-t border-gray-700 pt-3 mt-2">
      <div className="flex items-center justify-between mb-1">
        <span className="text-gray-400 text-xs">{label}</span>
        <button onClick={add} className="text-xs text-yellow-400 hover:text-yellow-300">+ Add</button>
      </div>
      <div className="text-[10px] text-gray-600 mb-1">Fields accessible as nodeName_fieldName. Full map: nodeName_result['field']</div>
      {entries.length === 0 && <div className="text-[10px] text-gray-600 mb-1">No return values defined</div>}
      {entries.map((e, i) => (
        <div key={i} className="flex gap-1 mb-1">
          <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
            value={e.key} placeholder="fieldName" onChange={ev => updateKey(i, ev.target.value)} />
          <select className="w-20 bg-gray-700 rounded px-1 py-1 text-white text-xs"
            value={e.type} onChange={ev => updateType(i, ev.target.value)}>
            {TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
          <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
    </div>
  );
}

// ── Collapsible section ────────────────────
function CollapsibleSection({ title, defaultOpen, children }: { title: string; defaultOpen?: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(defaultOpen ?? false);
  return (
    <div className="border-t border-gray-700 pt-2 mt-2">
      <div className="flex items-center justify-between cursor-pointer" onClick={() => setOpen(!open)}>
        <span className="text-gray-400 text-xs">{title}</span>
        <span className="text-gray-500 text-xs">{open ? '▾' : '▸'}</span>
      </div>
      {open && <div className="mt-2">{children}</div>}
    </div>
  );
}

// ── Route editor (result/exception routing) ──
function RouteEditor({ entries, onChange, label }: { entries: any[]; onChange: (v: any[]) => void; label: string }) {
  const add = () => onChange([...entries, { expr: '', to: '', isDefault: false }]);
  const remove = (i: number) => onChange(entries.filter((_:any,idx:number) => idx !== i));
  const update = (i: number, f: string, v: any) => {
    const copy = entries.map((e:any,idx:number) => idx===i ? {...e, [f]:v} : e);
    if (f==='isDefault' && v===true) copy.forEach((e:any,idx:number) => { if(idx!==i) e.isDefault=false; });
    onChange(copy);
  };
  return (
    <div>
      <div className="text-[10px] text-gray-500 mb-1">Use {"{" + "}"}{label}.xxx in expressions, e.g. {label}.status == &apos;OK&apos;</div>
      {entries.map((e:any,i:number) => (
        <div key={i} className="mb-1.5 p-1.5 bg-gray-750 rounded border border-gray-700">
          <div className="flex items-center justify-between mb-0.5">
            <span className="text-[10px] text-gray-500">#{i+1}</span>
            <div className="flex gap-1 items-center">
              <label className="text-[9px] text-gray-500"><input type="checkbox" checked={e.isDefault} onChange={ev => update(i,'isDefault',ev.target.checked)} className="accent-orange-500" /> default</label>
              <button onClick={() => remove(i)} className="text-[10px] text-red-400">&times;</button>
            </div>
          </div>
          {!e.isDefault && <input className="w-full bg-gray-700 rounded px-1.5 py-0.5 text-white text-[11px] mb-0.5 font-mono" value={e.expr||''} placeholder={`${label}.status == 'PASS'`} onChange={ev => update(i,'expr',ev.target.value)} />}
          {e.isDefault && <div className="text-[9px] text-gray-500 mb-0.5">Fallback</div>}
          <input className="w-full bg-gray-700 rounded px-1.5 py-0.5 text-white text-[11px]" value={e.to||''} placeholder="target node id" onChange={ev => update(i,'to',ev.target.value)} />
        </div>
      ))}
      <button onClick={add} className="text-xs text-blue-400 hover:text-blue-300 mt-0.5">+ Add route</button>
    </div>
  );
}

// ── RetryOn editor (simple SpEL expressions) ──
function RetryOnEditor({ entries, onChange, addLabel }: { entries: any[]; onChange: (v: any[]) => void; addLabel: string }) {
  const add = () => onChange([...entries, { expr: '' }]);
  const remove = (i: number) => onChange(entries.filter((_:any,idx:number) => idx !== i));
  const update = (i: number, v: string) => {
    const copy = entries.map((e:any,idx:number) => idx===i ? {...e, expr:v} : e);
    onChange(copy);
  };
  return (
    <div>
      {entries.map((e:any,i:number) => (
        <div key={i} className="flex gap-1 mb-1">
          <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
            value={e.expr||''} placeholder="exception.type.contains('TimeoutException')"
            onChange={ev => update(i, ev.target.value)} />
          <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
      <button onClick={add} className="text-xs text-blue-400 hover:text-blue-300">{addLabel}</button>
    </div>
  );
}

// ── Timer helpers ─────────────────────────
function parseDurationValue(dur: string | undefined): number {
  if (!dur) return 0;
  const m = dur.match(/^PT(\d+)/);
  return m ? parseInt(m[1]) : 0;
}
function parseDurationUnit(dur: string | undefined): string {
  if (!dur) return 'M';
  const m = dur.match(/^PT\d+([SMH])/);
  return m ? m[1] : 'M';
}
function toDatetimeLocal(iso: string | undefined): string {
  if (!iso) return '';
  try {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    // Convert to local datetime-local format: YYYY-MM-DDTHH:mm
    return d.getFullYear() + '-' +
      String(d.getMonth()+1).padStart(2,'0') + '-' +
      String(d.getDate()).padStart(2,'0') + 'T' +
      String(d.getHours()).padStart(2,'0') + ':' +
      String(d.getMinutes()).padStart(2,'0');
  } catch { return ''; }
}
