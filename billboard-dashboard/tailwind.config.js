/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: '#0B0F19',
        surface: '#1A1F2C',
        surfaceHighlight: '#2A303C',
        primary: '#3B82F6', // Blue
        secondary: '#8B5CF6', // Purple
        success: '#10B981', // Green
        warning: '#F59E0B', // Yellow
        danger: '#EF4444', // Red
        textPrimary: '#F3F4F6',
        textSecondary: '#9CA3AF',
      },
    },
  },
  plugins: [],
}
