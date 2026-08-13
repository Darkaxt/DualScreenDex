import { defineConfig, loadEnv } from 'vite';
import preact from '@preact/preset-vite';

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, '.', 'DUALDEX_');
  return {
    plugins: [preact()],
    server: {
      proxy: {
        '/api': environment.DUALDEX_API_TARGET ?? 'http://127.0.0.1:47831'
      }
    },
    test: {
      include: ['src/**/*.test.{ts,tsx}'],
      environment: 'jsdom',
      pool: 'forks',
      fileParallelism: false,
      minWorkers: 1,
      maxWorkers: 1
    }
  };
});
