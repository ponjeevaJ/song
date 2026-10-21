/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#100d17',
        panel: '#191521',
        plum: '#a477ff',
        gold: '#e8bf74',
      },
      fontFamily: {
        sans: ['DM Sans', 'Noto Sans Tamil', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
