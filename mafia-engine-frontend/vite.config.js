import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/auth': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/create-room': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/join-room': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/room': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/start-game': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/game-state': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/advance-phase': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/night-kill': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/police-guess': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/doctor-save': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/submit-vote': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/resolve-voting': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/send-message': { target: 'http://mafia-gateway-service:8000', changeOrigin: true },
      '/ws': { target: 'ws://mafia-gateway-service:8000', ws: true, changeOrigin: true },
    }
  }
})