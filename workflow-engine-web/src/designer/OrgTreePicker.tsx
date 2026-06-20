import { useState, useEffect } from 'react';

interface OrgNode {
  uid: string;
  name: string;
  title: string;
  children: OrgNode[];
}

interface Props {
  value?: string;             // selected uid (single-select)
  values?: string[];          // selected uids (multi-select)
  onChange?: (v: string) => void;
  onChangeMulti?: (v: string[]) => void;
  multi?: boolean;
}

export default function OrgTreePicker({ value, values, onChange, onChangeMulti, multi }: Props) {
  const [tree, setTree] = useState<OrgNode[]>([]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');

  const [status, setStatus] = useState<'loading'|'empty'|'ok'>('loading');

  useEffect(() => {
    fetch('/api/org/tree').then(r => r.json())
      .then(data => {
        const nodes = data || [];
        setTree(nodes);
        setStatus(nodes.length > 0 ? 'ok' : 'empty');
        if (nodes.length > 0) expandAll(nodes, 2);
      })
      .catch(() => setStatus('empty'));
  }, []);

  const expandAll = (nodes: OrgNode[], depth: number) => {
    if (depth <= 0) return;
    setExpanded(prev => {
      const next = new Set(prev);
      for (const n of nodes) { next.add(n.uid); if (n.children) expandAll(n.children, depth - 1); }
      return next;
    });
  };

  const toggle = (uid: string) => {
    setExpanded(prev => {
      const next = new Set(prev);
      next.has(uid) ? next.delete(uid) : next.add(uid);
      return next;
    });
  };

  const renderNode = (node: OrgNode, depth: number): any => {
    if (search && !node.name.includes(search) && !node.uid.includes(search)) return null;
    const hasChildren = node.children && node.children.length > 0;
    const open = expanded.has(node.uid);
    const selected = multi ? (values || []).includes(node.uid) : (value || '') === node.uid;

    return (
      <div key={node.uid}>
        <div className={`flex items-center gap-1 py-0.5 px-1 rounded cursor-pointer hover:bg-gray-700 ${selected ? 'bg-blue-600/30' : ''}`}
          style={{ paddingLeft: depth * 16 + 4 }}
          onClick={() => {
            if (multi && onChangeMulti) {
              const set = new Set(values || []);
              set.has(node.uid) ? set.delete(node.uid) : set.add(node.uid);
              onChangeMulti([...set]);
            } else {
              onChange?.(node.uid);
            }
          }}>
          {hasChildren ? (
            <span onClick={e => { e.stopPropagation(); toggle(node.uid); }}
              className="text-gray-500 w-4 text-center cursor-pointer text-[10px]">{open ? '▾' : '▸'}</span>
          ) : <span className="w-4" />}
          {multi && (
            <input type="checkbox" checked={selected} onChange={() => {}}
              className="w-3 h-3 accent-blue-500 pointer-events-none" />
          )}
          <span className="text-sm text-gray-300">{node.name}</span>
          <span className="text-[10px] text-gray-500">{node.title}</span>
        </div>
        {hasChildren && open && node.children.map(c => renderNode(c, depth + 1))}
      </div>
    );
  };

  return (
    <div className="border border-gray-600 rounded bg-gray-750 max-h-48 overflow-y-auto">
      <input className="w-full bg-transparent px-2 py-1 text-xs text-gray-400 border-b border-gray-600 outline-none"
        placeholder="搜索..." value={search} onChange={e => setSearch(e.target.value)} />
      {tree.map(n => renderNode(n, 0))}
      {status === 'loading' && <div className="p-2 text-xs text-gray-600">加载中...</div>}
      {status === 'empty' && <div className="p-2 text-xs text-gray-600">未配置组织架构 — 后端未连接 LDAP 或无用户数据</div>}
    </div>
  );
}
