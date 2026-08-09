import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';

export default defineConfig({
  plugins: [preact()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:47831'
    }
  },
  test: {
    environment: 'jsdom',
    pool: 'forks',
    fileParallelism: false,
    minWorkers: 1,
    maxWorkers: 1
  }
});
