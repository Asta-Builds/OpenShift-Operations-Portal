/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        background: 'var(--background)',
        foreground: 'var(--foreground)',
        content1: 'var(--content1)',
        content2: 'var(--content2)',
        content3: 'var(--content3)',
        content4: 'var(--content4)',
        divider: 'var(--divider)',
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: 'var(--primary-foreground)',
          50: '#e6f1fe',
          100: '#cce3fd',
          200: '#99c7fb',
          300: '#66abf9',
          400: '#338ef7',
          500: '#006fee',
          600: '#005bc4',
          700: '#004799',
          800: '#00346e',
          900: '#002044',
        },
        secondary: {
          DEFAULT: 'var(--secondary)',
          foreground: 'var(--secondary-foreground)',
        },
        success: {
          DEFAULT: 'var(--success)',
          foreground: 'var(--success-foreground)',
        },
        warning: {
          DEFAULT: 'var(--warning)',
          foreground: 'var(--warning-foreground)',
        },
        danger: {
          DEFAULT: 'var(--danger)',
          foreground: 'var(--danger-foreground)',
        },
        default: {
          DEFAULT: 'var(--default-500)',
          50: 'var(--default-50)',
          100: 'var(--default-100)',
          200: 'var(--default-200)',
          300: 'var(--default-300)',
          400: 'var(--default-400)',
          500: 'var(--default-500)',
          600: 'var(--default-600)',
          700: 'var(--default-700)',
          800: 'var(--default-800)',
          900: 'var(--default-900)',
        }
      },
      borderRadius: {
        'small': '8px',
        'medium': '12px',
        'large': '16px',
        '2xl': '20px',
        '3xl': '24px',
      },
      boxShadow: {
        'heroui-sm': '0px 0px 5px 0px rgba(0, 0, 0, 0.05), 0px 2px 8px 0px rgba(0, 0, 0, 0.06)',
        'heroui-md': '0px 0px 15px 0px rgba(0, 0, 0, 0.06), 0px 4px 16px 0px rgba(0, 0, 0, 0.08)',
        'heroui-lg': '0px 0px 30px 0px rgba(0, 0, 0, 0.08), 0px 8px 24px 0px rgba(0, 0, 0, 0.12)',
        'glow-primary': '0 0 20px -3px rgba(0, 111, 238, 0.35)',
        'glow-success': '0 0 20px -3px rgba(23, 201, 100, 0.35)',
        'glow-warning': '0 0 20px -3px rgba(245, 165, 36, 0.35)',
        'glow-danger': '0 0 20px -3px rgba(243, 18, 96, 0.35)',
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
