import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { Cog, Globe2 } from 'lucide-react';

export default function ServiceTaskNode({ data }: NodeProps) {
  const isHttp = data.httpMode as boolean;
  return (
    <NodeShell
      data={data}
      icon={isHttp ? <Globe2 size={15} strokeWidth={2} /> : <Cog size={15} strokeWidth={2} />}
      tint={isHttp ? "#2dd4bf" : "#c084fc"}
      kind="Service task"
      sub={isHttp ? "HTTP" : "Code"}
    />
  );
}
