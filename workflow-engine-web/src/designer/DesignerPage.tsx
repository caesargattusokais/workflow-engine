import { useState, useCallback, useMemo } from 'react';
import { useNodesState, useEdgesState, type Node, type Edge } from '@xyflow/react';
import NodePalette from './NodePalette';
import FlowCanvas from './FlowCanvas';
import PropertyPanel from './PropertyPanel';
import { deployDefinition } from '../api/client';
import { graphToYaml } from './graphToYaml';

interface VarInfo {
  name: string;
  source: string;
}

export default function DesignerPage() {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [showVars, setShowVars] = useState(false);

  const handleNodeSelect = useCallback((node: Node | null) => setSelectedNode(node), []);

  const handleNodeChange = useCallback((updatedNode: Node) => {
    setNodes(nodes.map(n => n.id === updatedNode.id ? updatedNode : n));
    setSelectedNode(updatedNode);
  }, [nodes, setNodes]);

  const handleDeleteNode = useCallback(() => {
    if (!selectedNode) return;
    setNodes(nodes.filter(n => n.id !== selectedNode.id));
    setEdges(edges.filter(e => e.source !== selectedNode.id && e.target !== selectedNode.id));
    setSelectedNode(null);
  }, [selectedNode, nodes, edges, setNodes, setEdges]);

  const handleDeploy = async () => {
    if (nodes.length === 0) { alert('Add some nodes first'); return; }
    try {
      const yaml = graphToYaml(nodes, edges, 'my-workflow');
      const result = await deployDefinition(yaml);
      alert(`Deployed: ${result.id} v${result.version}\n\nYAML:\n${yaml}`);
    } catch (e: any) { alert('Deploy failed: ' + e.message); }
  };

  // ── Collect all known variables ────────────
  const allVars = useMemo<VarInfo[]>(() => {
    const vars: VarInfo[] = [];

    // StartEvent initial variables
    for (const node of nodes) {
      if (node.type === 'startEvent') {
        const initials = (node.data.initialVars as string[]) || [];
        for (const v of initials) {
          if (v.trim()) vars.push({ name: v.trim(), source: 'Start (初始变量)' });
        }
      }
    }

    // Gateway condition expressions — extract variable names
    for (const node of nodes) {
      if (node.type === 'exclusiveGateway' || node.type === 'inclusiveGateway') {
        const conds = (node.data.conditions as any[]) || [];
        for (const c of conds) {
          if (!c.expr || c.isDefault) continue;
          // Extract bare identifiers from SpEL like "days > 3" or "amount >= 5000"
          const names = c.expr.match(/\b([a-zA-Z_]\w*)\b/g) || [];
          for (const name of names) {
            if (!['and', 'or', 'not', 'true', 'false', 'null', 'eq', 'ne', 'gt', 'lt'].includes(name.toLowerCase())) {
              const exists = vars.some(v => v.name === name);
              if (!exists) vars.push({ name, source: `Gateway expression` });
            }
          }
        }
      }
    }

    // ServiceTask return values
    for (const node of nodes) {
      if (node.type === 'serviceTask') {
        const hc = node.data.handlerClass as string;
        const label = hc ? hc.split('.').pop() : (node.data.httpMode ? 'HTTP' : 'Code');
        const retVals = (node.data.returnValues as Array<{key:string;type:string}>) || [];
        if (retVals.length > 0) {
          for (const rv of retVals) {
            if (rv.key && !vars.some(v => v.name === `result.${rv.key}`)) {
              vars.push({ name: `result.${rv.key}`, source: `ServiceTask: ${label} (${rv.type})` });
            }
          }
        } else {
          const exists = vars.some(v => v.name === 'result.*');
          if (!exists) vars.push({ name: 'result.*', source: `ServiceTask: ${label}` });
        }
      }
    }

    // UserTask assignee expressions
    for (const node of nodes) {
      if (node.type === 'userTask') {
        const expr = (node.data.assignee as string) || '';
        const match = expr.match(/\$\{(\w+)\}/);
        if (match && !vars.some(v => v.name === match[1])) {
          vars.push({ name: match[1], source: 'Assignee expression' });
        }
      }
    }

    return vars;
  }, [nodes]);

  return (
    <div className="flex flex-col h-full">
      {/* Toolbar */}
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-1 flex justify-between items-center">
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-400">Designer</span>
          {selectedNode && (
            <button onClick={handleDeleteNode}
              className="bg-red-600 hover:bg-red-500 text-white text-xs px-2 py-0.5 rounded">
              Delete Node
            </button>
          )}
          <button onClick={() => setShowVars(!showVars)}
            className={`text-xs px-2 py-1 rounded ${showVars
              ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400 hover:text-white'}`}>
            Variables ({allVars.length})
          </button>
        </div>
        <button onClick={handleDeploy}
          className="bg-green-600 hover:bg-green-500 text-white text-sm px-4 py-1 rounded">
          Deploy
        </button>
      </div>

      {/* Variables Panel */}
      {showVars && (
        <div className="bg-gray-800 border-b border-gray-700 px-4 py-2">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-gray-400">Global Variables</span>
            <span className="text-[10px] text-gray-600">
              在条件中直接使用变量名，如 days &gt; 3
            </span>
          </div>
          {allVars.length === 0 ? (
            <div className="text-xs text-gray-600 italic">
              暂无变量。在 Start 节点定义初始变量，或在条件中引用变量后自动出现。
            </div>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {allVars.map((v, i) => (
                <span key={i} className="inline-flex items-center gap-1 bg-gray-750 border border-gray-600
                         rounded px-2 py-0.5 text-xs text-gray-300" title={v.source}>
                  <code className="text-blue-400">{v.name}</code>
                  <span className="text-[10px] text-gray-600">← {v.source}</span>
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Main content */}
      <div className="flex flex-1 overflow-hidden">
        <NodePalette />
        <FlowCanvas
          nodes={nodes} edges={edges}
          onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
          setNodes={setNodes} setEdges={setEdges}
          onNodeSelect={handleNodeSelect}
        />
        <PropertyPanel node={selectedNode} onChange={handleNodeChange} />
      </div>
    </div>
  );
}
