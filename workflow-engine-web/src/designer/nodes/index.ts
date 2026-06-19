import StartEventNode from './StartEventNode';
import EndEventNode from './EndEventNode';
import UserTaskNode from './UserTaskNode';
import ServiceTaskNode from './ServiceTaskNode';
import ExclusiveGatewayNode from './ExclusiveGatewayNode';
import ParallelGatewayNode from './ParallelGatewayNode';
import InclusiveGatewayNode from './InclusiveGatewayNode';
import TimerNode from './TimerNode';

export const nodeTypes = {
  startEvent: StartEventNode,
  endEvent: EndEventNode,
  userTask: UserTaskNode,
  serviceTask: ServiceTaskNode,
  exclusiveGateway: ExclusiveGatewayNode,
  parallelGateway: ParallelGatewayNode,
  inclusiveGateway: InclusiveGatewayNode,
  timer: TimerNode,
};
