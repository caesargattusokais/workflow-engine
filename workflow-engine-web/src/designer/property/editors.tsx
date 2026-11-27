import { useT } from '../../i18n';

// ── KvEditor ──────────────────────────
export function KvEditor({ label, entries, onChange, keyPlaceholder, valPlaceholder, emptyText }: {
    label: string; entries: Array<{key: string; value: string}>;
    onChange: (v: Array<{key: string; value: string}>) => void;
    keyPlaceholder: string; valPlaceholder: string;
    emptyText?: string;
}) {
  const { t } = useT();
  const add = () => onChange([...entries, { key: '', value: '' }]);
  const remove = (i: number) => onChange(entries.filter((_, idx) => idx !== i));
  const updateKey = (i: number, k: string) => onChange(entries.map((e, idx) => idx === i ? { ...e, key: k } : e));
  const updateVal = (i: number, v: string) => onChange(entries.map((e, idx) => idx === i ? { ...e, value: v } : e));

  return (
    <div className="mb-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-gray-400 text-xs">{label}</span>
        <button onClick={add} className="text-xs text-teal-400 hover:text-teal-300">+ {t.designer.addParam}</button>
      </div>
      {entries.length === 0 && <div className="text-[10px] text-gray-600 mb-1">{emptyText || t.designer.noParams}</div>}
      {entries.map((e, i) => (
        <div key={i} className="flex gap-1 mb-1">
          <input className="flex-1 bg-gray-900/70 border border-gray-700 rounded-md px-2 py-1 text-white text-xs font-mono"
            value={e.key} placeholder={keyPlaceholder} onChange={ev => updateKey(i, ev.target.value)} />
          <input className="flex-1 bg-gray-900/70 border border-gray-700 rounded-md px-2 py-1 text-white text-xs font-mono"
            value={e.value} placeholder={valPlaceholder} onChange={ev => updateVal(i, ev.target.value)} />
          <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
    </div>
  );
}

// ── ReturnValue editor (key + type, add/remove) ────
const TYPES = ['String', 'Number', 'Boolean', 'JSON', 'Object'];
export function ReturnValueEditor({ label, entries, onChange }: {
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
          <input className="flex-1 bg-gray-900/70 border border-gray-700 rounded-md px-2 py-1 text-white text-xs font-mono"
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

// ── RetryOn editor (simple SpEL expressions) ──
export function RetryOnEditor({ entries, onChange, addLabel }: { entries: any[]; onChange: (v: any[]) => void; addLabel: string }) {
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
          <input className="flex-1 bg-gray-900/70 border border-gray-700 rounded-md px-2 py-1 text-white text-xs font-mono"
            value={e.expr||''} placeholder="exception.type.contains('TimeoutException')"
            onChange={ev => update(i, ev.target.value)} />
          <button onClick={() => remove(i)} className="text-red-400 hover:text-red-300 text-xs px-1">&times;</button>
        </div>
      ))}
      <button onClick={add} className="text-xs text-blue-400 hover:text-blue-300">{addLabel}</button>
    </div>
  );
}

// ── MappingEditor (from, to, expr for callActivity input/output mapping) ──
export function MappingEditor({ label, entries, onChange }: {
    label: string; entries: Array<{from: string; to: string; expr: string}>;
    onChange: (v: Array<{from: string; to: string; expr: string}>) => void;
}) {
  const { t } = useT();
  const add = () => onChange([...entries, { from: '', to: '', expr: '' }]);
  const remove = (i: number) => onChange(entries.filter((_, idx) => idx !== i));
  const updateField = (i: number, field: 'from' | 'to' | 'expr', value: string) =>
    onChange(entries.map((e, idx) => idx === i ? { ...e, [field]: value } : e));

  return (
    <div className="mb-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-gray-400 text-xs">{label}</span>
        <button onClick={add} className="text-xs text-blue-400 hover:text-blue-300">+ Add</button>
      </div>
      {entries.length === 0 && <div className="text-[10px] text-gray-600 mb-1">No mappings defined</div>}
      {entries.map((e, i) => (
        <div key={i} className="mb-1.5 p-1.5 bg-gray-750 rounded border border-gray-700">
          <div className="flex items-center justify-between mb-0.5">
            <span className="text-[10px] text-gray-500">#{i + 1}</span>
            <button onClick={() => remove(i)} className="text-[10px] text-red-400">&times;</button>
          </div>
          <div className="flex gap-1 mb-0.5">
            <input className="flex-1 bg-gray-700 rounded px-1.5 py-0.5 text-white text-[11px] font-mono"
              value={e.from} placeholder={t.props.mappingFrom}
              onChange={ev => updateField(i, 'from', ev.target.value)} />
            <input className="flex-1 bg-gray-700 rounded px-1.5 py-0.5 text-white text-[11px] font-mono"
              value={e.to} placeholder={t.props.mappingTo}
              onChange={ev => updateField(i, 'to', ev.target.value)} />
          </div>
          <input className="w-full bg-gray-700 rounded px-1.5 py-0.5 text-white text-[11px] font-mono"
            value={e.expr || ''} placeholder={t.props.mappingExpr + ' (optional SpEL)'}
            onChange={ev => updateField(i, 'expr', ev.target.value)} />
        </div>
      ))}
    </div>
  );
}

// ── Timer helpers ─────────────────────────
export function parseDurationValue(dur: string | undefined): number {
  if (!dur) return 0;
  const m = dur.match(/^PT(\d+)/);
  return m ? parseInt(m[1]) : 0;
}
export function parseDurationUnit(dur: string | undefined): string {
  if (!dur) return 'M';
  const m = dur.match(/^PT\d+([SMH])/);
  return m ? m[1] : 'M';
}
export function toDatetimeLocal(iso: string | undefined): string {
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
