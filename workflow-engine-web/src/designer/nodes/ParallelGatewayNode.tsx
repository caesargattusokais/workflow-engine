import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ParallelGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-blue-400" />
      <div className="w-10 h-10 bg-blue-600 rotate-45 border-2 border-blue-300
                      flex items-center justify-center shadow-lg shadow-blue-500/30">
        <span className="text-white text-xs font-black tracking-tighter" style={{ transform: 'rotate(-45deg)' }}>ALL</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
      <div className="text-center mt-1">
        <div className="text-xs text-blue-400 font-semibold">并行分支</div>
        <div className="text-[10px] text-gray-500">{data.name as string}</div>
      </div>
    </div>
  );
}
