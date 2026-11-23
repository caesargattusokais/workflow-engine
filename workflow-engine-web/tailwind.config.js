/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        gray: {
          // Linear-inspired neutral ramp. Class NAMES stay unchanged so the
          // E2E selectors (bg-gray-800 / -750 / ...) keep working; only the
          // values are remapped to a cooler, tighter blue-gray palette.
          750: '#22262e',
          800: '#1c1f26',
          850: '#171a20',
        },
        // Semantic aliases used by newer styles. Keep the raw blue-600 /
        // green-900 etc. class names intact for the existing E2E suite.
        accent: {
          DEFAULT: '#5e6ad2', // Linear indigo
          hover: '#6e79d8',
          soft: 'rgba(94,106,210,0.15)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      fontSize: {
        // Raise the tiny 10px text that makes Chinese hard to read; keep the
        // class names (text-[10px] is a literal class — handled in index.css).
        '2xs': ['11px', '16px'],
      },
      boxShadow: {
        panel: '0 8px 30px rgba(0,0,0,0.35)',
        popover: '0 12px 40px rgba(0,0,0,0.5)',
      },
    },
  },
  plugins: [],
};
