import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function InclusiveGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="w-10 h-10 bg-purple-600 rotate-45 border-2 border-purple-300
                      flex items-center justify-center shadow-lg shadow-purple-500/30">
        <span className="text-white text-sm font-black" style={{ transform: 'rotate(-45deg)' }}>?</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
      <div className="text-center mt-1">
        <div className="text-xs text-purple-400 font-semibold">条件分支</div>
        <div className="text-[10px] text-gray-500">{data.name as string}</div>
      </div>
    </div>
  );
}
