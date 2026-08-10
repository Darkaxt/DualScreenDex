import { render } from 'preact';
import { App } from './App';
import './styles.css';

const root = document.getElementById('app')!;

if (import.meta.env.DEV) {
  import('./dev/DevApp').then(({ DevApp }) => render(<DevApp />, root));
} else {
  render(<App />, root);
}
