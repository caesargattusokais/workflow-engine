import { useEffect, useState } from 'react';
import type { Node, Edge } from '@xyflow/react';
import { useT } from '../../i18n';
import { listDefinitions } from '../../api/client';
import { showToast } from '../../api/toast';
import { KvEditor, ReturnValueEditor, RetryOnEditor, MappingEditor,
         parseDurationValue, parseDurationUnit, toDatetimeLocal } from './editors';
import OrgTreePicker from '../OrgTreePicker';

type UpdateData = (key: string, value: unknown) => void;

interface SectionProps {
  node: Node;
  updateData: UpdateData;
}

interface ConditionItem {
  expr: string;
  to: string;
  isDefault: boolean;
}

// ── StartEvent: initial variables ────
export function StartEventSection({ node, updateData }: SectionProps) {
  const { t } = useT();
  return (
    <div className="border-t border-green-500/50 pt-3 mt-2">
      <span className="text-green-400 text-xs font-semibold">{t.props.initialVars}</span>
      <div className="text-[10px] text-gray-500 mb-2">{t.props.initialVarsHint}</div>
      <KvEditor label="" entries={(node.data.initialVars as Array<{key:string;value:string}>) || []}
        onChange={v => updateData('initialVars', v)}
        keyPlaceholder={t.designer.varName} valPlaceholder={t.designer.defaultValue} />
    </div>
  );
}

// ── UserTask completion form fields ──
interface FormFieldItem {
  key: string; label: string; type: string;
  required: boolean; options: string[]; placeholder: string;
}

const FORM_FIELD_TYPES = ['text', 'textarea', 'number', 'select', 'date'];

export function FormFieldsEditor({ node, updateData }: SectionProps) {
  const { t } = useT();
  const fields = (node.data.formFields as FormFieldItem[]) || [];
  const setFields = (v: FormFieldItem[]) => updateData('formFields', v);
  const add = () => setFields([...fields, { key: '', label: '', type: 'text', required: false, options: [], placeholder: '' }]);
  const remove = (i: number) => setFields(fields.filter((_, idx) => idx !== i));
  const patch = (i: number, p: Partial<FormFieldItem>) =>
    setFields(fields.map((f, idx) => idx === i ? { ...f, ...p } : f));

  return (
    <div className="border-t border-cyan-500/50 pt-2 mt-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-cyan-400 text-xs font-semibold">{t.props.formFields}</span>
        <button onClick={add} className="text-xs text-cyan-400 hover:text-cyan-300">{t.props.addField}</button>
      </div>
      <div className="text-[10px] text-gray-500 mb-2">{t.props.formFieldsHint}</div>
      {fields.length === 0 && <div className="text-[10px] text-gray-600 mb-1">{t.props.noFields}</div>}
      {fields.map((f, i) => (
        <div key={i} className="bg-gray-750/60 rounded p-2 mb-2 border border-gray-700">
          <div className="flex gap-1 mb-1">
            <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs font-mono"
              value={f.key || ''} placeholder={t.props.fieldKeyPlaceholder}
              onChange={ev => patch(i, { key: ev.target.value })} />
            <select className="w-24 bg-gray-700 rounded px-1 py-1 text-white text-xs"
              value={f.type || 'text'} onChange={ev => patch(i, { type: ev.target.value })}>
              {FORM_FIELD_TYPES.map(tp => <option key={tp} value={tp}>{tp}</option>)}
            </select>
            <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
          </div>
          <div className="flex gap-1 mb-1">
            <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs"
              value={f.label || ''} placeholder={t.props.fieldLabelPlaceholder}
              onChange={ev => patch(i, { label: ev.target.value })} />
            <input className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs"
              value={f.placeholder || ''} placeholder={t.props.fieldPlaceholderPlaceholder}
              onChange={ev => patch(i, { placeholder: ev.target.value })} />
          </div>
          {f.type === 'select' && (
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1"
              value={(f.options || []).join(', ')} placeholder={t.props.fieldOptionsPlaceholder}
              onChange={ev => patch(i, { options: ev.target.value.split(',').map(s => s.trim()).filter(Boolean) })} />
          )}
          <label className="flex items-center gap-1 text-xs text-gray-400">
            <input type="checkbox" checked={Boolean(f.required)}
              onChange={ev => patch(i, { required: ev.target.checked })} />
            {t.props.fieldRequired}
          </label>
        </div>
      ))}
    </div>
  );
}

// ── UserTask ────────────────────────
export function UserTaskSection({ node, updateData }: SectionProps) {
  const { t } = useT();
  return (
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
          <FormFieldsEditor node={node} updateData={updateData} />
        </>
      ) : (
        <HttpCallbackFields node={node} updateData={updateData}
          urlPlaceholder="https://oa.company.com/api/approval" paramKeyPlaceholder="applicant" />
      )}

      <div className="border-t border-orange-500/50 pt-2 mt-2">
        <span className="text-orange-400 text-xs font-semibold">{t.props.timeout}</span>
        <div className="text-[10px] text-gray-500 mb-2">{t.props.timeoutHint}</div>
        <DurationField label={t.props.duration} value={node.data.boundaryTimer as string}
          onChange={v => updateData('boundaryTimer', v)} />
      </div>
    </>
  );
}

// ── ServiceTask ─────────────────────
export function ServiceTaskSection({ node, updateData }: SectionProps) {
  const { t } = useT();
  return (
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
        <HttpCallbackFields node={node} updateData={updateData}
          urlPlaceholder="https://api.example.com/check" paramKeyPlaceholder="amount" />
      )}

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
  );
}

// ── AI Task: mode / prompt / output / categories ──
export function AiTaskSection({ node, updateData }: SectionProps) {
  const { t } = useT();
  const mode = (node.data.mode as string) || 'summary';
  const options = (node.data.options as string[]) || [];
  return (
    <div className="border-t border-pink-500/50 pt-3 mt-2 space-y-3">
      <div>
        <label className="text-gray-400 text-xs">{t.props.aiMode}</label>
        <select className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
          value={mode} onChange={e => updateData('mode', e.target.value)}>
          <option value="summary" className="bg-gray-700">{t.props.aiModeSummary}</option>
          <option value="extract" className="bg-gray-700">{t.props.aiModeExtract}</option>
          <option value="classify" className="bg-gray-700">{t.props.aiModeClassify}</option>
        </select>
      </div>
      <div>
        <label className="text-gray-400 text-xs">{t.props.aiPrompt}</label>
        <textarea rows={5} spellCheck={false}
          className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 resize-y"
          value={(node.data.prompt as string) || ''}
          onChange={e => updateData('prompt', e.target.value)}
          placeholder={t.props.aiPromptHint} />
      </div>
      <div>
        <label className="text-gray-400 text-xs">{t.props.aiOutputVar}</label>
        <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
          value={(node.data.outputVar as string) || ''}
          onChange={e => updateData('outputVar', e.target.value)}
          placeholder={t.props.aiOutputVarHint} />
      </div>
      {mode === 'classify' && (
        <div>
          <label className="text-gray-400 text-xs">{t.props.aiCategories}</label>
          <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
            value={options.join(', ')}
            onChange={e => updateData('options', e.target.value.split(',').map(s => s.trim()).filter(Boolean))}
            placeholder={t.props.aiCategoriesHint} />
          <p className="text-[10px] text-gray-500 mt-0.5">{t.props.aiCategoriesTip}</p>
        </div>
      )}
    </div>
  );
}

// ── Gateway conditions (XOR / OR — one implementation, two color schemes) ──
const GATEWAY_STYLES = {
  exclusive: {
    wrap: 'border-t border-orange-500/50 pt-3 mt-2',
    heading: 'text-orange-400',
    addBtn: 'bg-orange-600 hover:bg-orange-500',
    itemBorder: 'border-orange-800',
    checkbox: 'accent-orange-500',
  },
  inclusive: {
    wrap: 'border-t border-purple-500/50 pt-3 mt-2',
    heading: 'text-purple-400',
    addBtn: 'bg-purple-600 hover:bg-purple-500',
    itemBorder: 'border-purple-800',
    checkbox: 'accent-purple-500',
  },
} as const;

export function GatewayConditionsSection({ node, updateData, kind }: SectionProps & { kind: 'exclusive' | 'inclusive' }) {
  const { t } = useT();
  const s = GATEWAY_STYLES[kind];
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

  const isXor = kind === 'exclusive';
  return (
    <div className={s.wrap}>
      <div className="flex items-center justify-between mb-1">
        <span className={`${s.heading} text-xs font-semibold`}>
          {isXor ? t.props.conditionsXor : t.props.conditionsOr}</span>
        <button onClick={addCondition} className={`text-xs text-white px-2 py-0.5 rounded ${s.addBtn}`}>{t.props.addCondition}</button>
      </div>
      <div className="text-[10px] text-gray-500 mb-2">{isXor ? t.props.conditionsHint : t.props.conditionsOrHint}</div>
      {conditions.map((c, i) => (
        <div key={i} className={`mb-2 p-2 rounded border bg-gray-750 ${c.isDefault ? 'border-gray-600' : s.itemBorder}`}>
          <div className="flex items-center justify-between mb-1">
            <span className={`text-[10px] ${c.isDefault ? 'text-gray-400' : s.heading}`}>
              {c.isDefault
                ? (isXor ? t.props.defaultBranch : t.props.fallbackNoMatch)
                : (isXor ? t.designer.conditionN : t.designer.branchN).replace('{n}', String(i + 1))}</span>
            <div className="flex gap-1 items-center">
              <label className="text-[10px] text-gray-500 flex items-center gap-1">
                <input type="checkbox" checked={c.isDefault} onChange={e => updateCondition(i, 'isDefault', e.target.checked)} className={s.checkbox} /> default
              </label>
              <button onClick={() => removeCondition(i)} className="text-[10px] text-red-400 hover:text-red-300 ml-1">&times;</button>
            </div>
          </div>
          {!c.isDefault && (
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mb-1 font-mono"
              value={c.expr} placeholder={isXor ? 'SpEL: days > 3' : 'SpEL: amount > 1000'}
              onChange={e => updateCondition(i, 'expr', e.target.value)} />
          )}
          <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs"
            value={c.to} placeholder={t.designer.targetNodeId} onChange={e => updateCondition(i, 'to', e.target.value)} />
        </div>
      ))}
    </div>
  );
}

// ── Timer ────────────────────────────
export function TimerSection({ node, updateData }: SectionProps) {
  const { t } = useT();
  return (
    <div className="border-t border-amber-500/50 pt-3 mt-2">
      <span className="text-amber-400 text-xs font-semibold">{t.props.timerConfig}</span>
      <div className="text-[10px] text-gray-500 mb-2">{t.props.timerHint}</div>
      <DurationField label={t.props.duration} value={node.data.duration as string}
        onChange={v => updateData('duration', v)} />
      {/* Deadline: datetime-local picker → ISO 8601 */}
      <label className="block mb-2">
        <span className="text-gray-400 text-xs">{t.props.deadline}</span>
        <input type="datetime-local"
          className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5"
          style={{ colorScheme: 'dark' }}
          value={toDatetimeLocal(node.data.deadline as string)}
          onChange={e => {
            updateData('deadline', e.target.value ? new Date(e.target.value).toISOString() : '');
          }} />
      </label>
    </div>
  );
}

// ── CallActivity ────────────────────
export function CallActivitySection({ node, updateData }: SectionProps) {
  const { t } = useT();
  // Fetch deployed definitions for the calledId dropdown (mounted only for callActivity nodes)
  const [allDefs, setAllDefs] = useState<any[]>([]);
  useEffect(() => {
    listDefinitions(1, 200).then((r: any) => setAllDefs(r.items || r)).catch(e => showToast(e.message, 'error'));
  }, []);

  return (
    <div className="border-t border-blue-500/50 pt-3 mt-2">
      {/* calledId dropdown */}
      <label className="block mb-2">
        <span className="text-gray-400 text-xs">{t.props.calledProcess}</span>
        <select value={(node.data.calledId as string) || ''}
          onChange={e => updateData('calledId', e.target.value)}
          className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1">
          <option value="">-- select definition --</option>
          {allDefs.map((d: any) => (
            <option key={`${d.id}-${d.version}`} value={d.id}>{d.name || d.id} (v{d.version})</option>
          ))}
        </select>
      </label>

      {/* calledVersion dropdown */}
      {(node.data.calledId as string) && (
        <label className="block mb-2">
          <span className="text-gray-400 text-xs">{t.props.calledVersion}</span>
          <select value={(node.data.calledVersion as number) || 'latest'}
            onChange={e => updateData('calledVersion', e.target.value === 'latest' ? undefined : parseInt(e.target.value))}
            className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1">
            <option value="latest">latest</option>
            {allDefs.filter((d: any) => d.id === (node.data.calledId as string)).map((d: any) => (
              <option key={d.version} value={d.version}>v{d.version}</option>
            ))}
          </select>
        </label>
      )}

      <MappingEditor
        label={t.props.inputMapping}
        entries={(node.data.inputMapping as Array<{from:string;to:string;expr:string}>) || []}
        onChange={v => updateData('inputMapping', v)}
      />

      <MappingEditor
        label={t.props.outputMapping}
        entries={(node.data.outputMapping as Array<{from:string;to:string;expr:string}>) || []}
        onChange={v => updateData('outputMapping', v)}
      />
    </div>
  );
}

// ── Outgoing edges ──
const EDGE_COLORS: Record<string,string> = { result: '#22c55e', exception: '#ef4444', timeout: '#f97316', conditional: '#e5a50a', default: '#888', direct: '#666' };

export function OutgoingEdgesSection({ node, edges, onSelectEdge, onEdgesChange }: {
  node: Node;
  edges?: Edge[];
  onSelectEdge?: (edgeId: string) => void;
  onEdgesChange?: (edges: Edge[]) => void;
}) {
  const { t } = useT();
  if (!edges || edges.length === 0) return null;
  return (
    <div className="border-t border-gray-700 pt-2 mt-2">
      <span className="text-gray-400 text-xs font-semibold">{t.props.outgoingEdges}</span>
      {edges.filter(e => e.source === node.id).map(e => {
        const et = (e.data as any)?.edgeType || 'direct';
        const needsExpr = et === 'result' || et === 'exception' || et === 'conditional';
        const isDefault = (e.data as any)?.isDefault;
        return (
          <div key={e.id} className="mt-1.5 text-xs">
            <div className="flex items-center gap-1 cursor-pointer hover:bg-gray-750 rounded px-1 py-0.5"
              onClick={() => onSelectEdge?.(e.id)}>
              <span className="w-2 h-2 rounded-full inline-block flex-shrink-0" style={{background: EDGE_COLORS[et]||'#666'}} />
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
                  onEdgesChange?.(updated);
                }}
              />
            )}
            {et === 'default' && <div className="text-[10px] text-gray-500 mt-0.5">{t.designer.fallback}</div>}
          </div>
        );
      })}
    </div>
  );
}

// ── Shared pieces ─────────────────────

// HTTP callback fields: url / method / headers / query-or-body params
function HttpCallbackFields({ node, updateData, urlPlaceholder, paramKeyPlaceholder }: SectionProps & {
  urlPlaceholder: string;
  paramKeyPlaceholder: string;
}) {
  const { t } = useT();
  return (
    <>
      <div className="text-[10px] text-gray-500 mb-2">{t.props.callbackHint}</div>
      <label className="block mb-2">
        <span className="text-teal-400 text-xs">{t.props.url}</span>
        <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-0.5 font-mono"
          value={(node.data.url as string) || ''} placeholder={urlPlaceholder}
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
        keyPlaceholder={paramKeyPlaceholder} valPlaceholder="${applicant}" emptyText={t.props.noParams} />
    </>
  );
}

// Duration: number + unit → ISO 8601 (PT{n}{S|M|H})
function DurationField({ label, value, onChange }: { label: string; value: string | undefined; onChange: (v: string) => void }) {
  const { t } = useT();
  return (
    <label className="block mb-2">
      <span className="text-gray-400 text-xs">{label}</span>
      <div className="flex gap-1 mt-0.5">
        <input type="number" min="1" className="flex-1 bg-gray-700 rounded px-2 py-1 text-white text-xs"
          value={parseDurationValue(value)}
          onChange={e => {
            const val = parseInt(e.target.value) || 0;
            const unit = parseDurationUnit(value);
            if (val > 0) onChange(`PT${val}${unit}`);
            else onChange('');
          }} placeholder="30" />
        <select className="w-16 bg-gray-700 rounded px-1 py-1 text-white text-xs"
          value={parseDurationUnit(value)}
          onChange={e => {
            const val = parseDurationValue(value) || 1;
            onChange(`PT${val}${e.target.value}`);
          }}>
          <option value="S">{t.props.seconds}</option>
          <option value="M">{t.props.minutes}</option>
          <option value="H">{t.props.hours}</option>
        </select>
      </div>
    </label>
  );
}
