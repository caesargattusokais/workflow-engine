import { useState, useCallback } from 'react';
import { useNodesState, useEdgesState, type Node, type Edge } from '@xyflow/react';
import NodePalette from './NodePalette';
import FlowCanvas from './FlowCanvas';
import PropertyPanel from './PropertyPanel';
import { deployDefinition } from '../api/client';
import { graphToYaml } from './graphToYaml';

export default function DesignerPage() {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  const handleNodeSelect = useCallback((node: Node | null) => setSelectedNode(node), []);

  const handleNodeChange = useCallback((updatedNode: Node) => {
    setNodes(nodes.map(n => n.id === updatedNode.id ? updatedNode : n));
    setSelectedNode(updatedNode);
  }, [nodes, setNodes]);

  const handleDeploy = async () => {
    if (nodes.length === 0) { alert('Add some nodes first'); return; }
    try {
      const yaml = graphToYaml(nodes, edges, 'my-workflow');
      const result = await deployDefinition(yaml);
      alert(`Deployed: ${result.id} v${result.version}\n\nYAML:\n${yaml}`);
    } catch (e: any) { alert('Deploy failed: ' + e.message); }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-1 flex justify-between items-center">
        <span className="text-sm text-gray-400">Designer</span>
        <button onClick={handleDeploy}
          className="bg-green-600 hover:bg-green-500 text-white text-sm px-4 py-1 rounded">
          Deploy
        </button>
      </div>
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
