import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ServiceTaskNode({ data }: NodeProps) {
  const isHttp = data.httpMode as boolean;

  return (
    <div className="relative">
      <Handle type="target" position={Position.Top}
        className={isHttp ? '!bg-teal-400' : '!bg-purple-400'} />
      <div className={`min-w-[120px] px-4 py-2 rounded-lg border shadow-lg
                      flex items-center gap-2 text-sm text-white
                      ${isHttp
                        ? 'bg-teal-700 border-teal-500'
                        : 'bg-purple-700 border-purple-500'}`}>
        <span className="text-lg">{isHttp ? '⇄' : '⚙'}</span>
        <div>
          <div className="truncate max-w-[100px]">{data.name as string}</div>
          <div className="text-[10px] opacity-60">{isHttp ? 'HTTP' : 'Code'}</div>
        </div>
      </div>
      <Handle type="source" position={Position.Bottom}
        className={isHttp ? '!bg-teal-400' : '!bg-purple-400'} />
    </div>
  );
}
