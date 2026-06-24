export default {
  plugins: {
    tailwindcss: {},
    'postcss-px-to-viewport': {
      viewportWidth: 375,
      unitPrecision: 5,
      propList: ['*', '!letter-spacing'],
      viewportUnit: 'vw',
      fontViewportUnit: 'vw',
      minPixelValue: 1,
      mediaQuery: false,
      exclude: [
        /node_modules/,
        /src[\\/]App\.vue/,
        /src[\\/]assets/,
        /src[\\/]styles[\\/]index\.css/,
        /src[\\/]layouts/,
        /src[\\/]views(?![\\/]mobile-dashboard)/,
        /src[\\/]components(?![\\/]mobile-dashboard)/,
      ],
    },
    autoprefixer: {},
  },
}
