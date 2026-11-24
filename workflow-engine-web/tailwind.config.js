/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        app: '#12151b',
        gray: {
          // Linear-inspired neutral ramp. Class NAMES stay unchanged so the
          // E2E selectors (bg-gray-800 / -750 / ...) keep working; only the
          // values are remapped to a cooler, tighter blue-gray palette.
          750: '#252a35',
          800: '#1e222b',
          850: '#191d25',
          900: '#14171e',
        },
        accent: {
          DEFAULT: '#6d74e8',
          hover: '#8288f0',
          soft: 'rgba(109,116,232,0.15)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      fontSize: {
        '2xs': ['11px', '16px'],
      },
      boxShadow: {
        panel: '0 8px 30px rgba(0,0,0,0.35)',
        popover: '0 16px 48px rgba(0,0,0,0.55)',
        // Soft top-edge highlight + ambient lift — the two things that make
        // a flat dark card read as "designed" instead of "a rectangle".
        card: 'inset 0 1px 0 rgba(255,255,255,0.06), 0 1px 2px rgba(0,0,0,0.25)',
        lift: 'inset 0 1px 0 rgba(255,255,255,0.09), 0 10px 30px rgba(0,0,0,0.45)',
        glow: '0 0 0 1px rgba(109,116,232,0.4), 0 8px 24px rgba(109,116,232,0.15)',
      },
      backgroundImage: {
        // Subtle light-from-top gradient for elevated cards.
        'card-sheen': 'linear-gradient(180deg, rgba(255,255,255,0.035) 0%, rgba(255,255,255,0) 55%)',
        'accent-glow': 'radial-gradient(circle at 15% 0%, rgba(109,116,232,0.13), transparent 60%)',
      },
      transitionTimingFunction: {
        swift: 'cubic-bezier(0.2, 0.8, 0.2, 1)',
      },
    },
  },
  plugins: [],
};
