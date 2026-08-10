import { App } from '../App';
import { SimulatorPanel } from './SimulatorPanel';
import './simulator.css';

export function DevApp() {
  return <App DevelopmentTools={SimulatorPanel} />;
}
