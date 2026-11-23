import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { GitBranch } from 'lucide-react';
import { useT } from '../../i18n';

export default function ExclusiveGatewayNode({ data }: NodeProps) {
  const { t } = useT();
  return (
    <NodeShell
      data={data}
      icon={<GitBranch size={15} strokeWidth={2} />}
      tint={'#fb923c'}
      kind="XOR gateway"
      sub={t.nodes.xor}
    />
  );
}
