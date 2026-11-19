export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        gray: {
          // The codebase uses gray-750 widely but Tailwind's default ramp
          // jumps 700 -> 800, so those classes were silently dropped.
          // Sit it between gray-700 (#374151) and gray-800 (#1f2937).
          750: '#2d3748',
        },
      },
    },
  },
  plugins: [],
};
