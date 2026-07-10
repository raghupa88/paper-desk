/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{ts,tsx,html}'],
  theme: {
    extend: {
      colors: {
        desk: {
          bg: '#0d1117',
          panel: '#161b22',
          border: '#30363d',
          text: '#c9d1d9',
          dim: '#8b949e',
          up: '#3fb950',
          down: '#f85149',
          accent: '#58a6ff',
          warn: '#d29922',
        },
      },
    },
  },
  plugins: [],
};
