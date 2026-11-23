import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { GitFork } from 'lucide-react';
import { useT } from '../../i18n';

export default function ParallelGatewayNode({ data }: NodeProps) {
  const { t } = useT();
  return (
    <NodeShell
      data={data}
      icon={<GitFork size={15} strokeWidth={2} />}
      tint={'#60a5fa'}
      kind="AND gateway"
      sub={t.nodes.and}
    />
  );
}
