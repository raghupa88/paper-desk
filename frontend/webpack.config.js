const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
  const isDev = argv.mode !== 'production';
  return {
    // Two independent entry points: "main" is the trading app; "devtools" is
    // the standalone esp DevTools window (its own HTML document, its own
    // bundle, opened as a separate browser tab/window — see
    // src/devtools/activation.ts). They share no runtime state; the only
    // link between them is a same-origin BroadcastChannel.
    entry: {
      main: './src/index.tsx',
      devtools: './src/devtools/panel/panelEntry.tsx',
    },
    output: {
      path: path.resolve(__dirname, 'dist'),
      filename: isDev ? '[name].js' : '[name].[contenthash].js',
      chunkFilename: isDev ? '[name].chunk.js' : '[name].[contenthash].chunk.js',
      publicPath: '/',
      clean: true,
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.js'],
    },
    module: {
      rules: [
        {
          test: /\.tsx?$/,
          use: { loader: 'ts-loader', options: { transpileOnly: true } },
          exclude: /node_modules/,
        },
        {
          test: /\.css$/,
          use: ['style-loader', 'css-loader', 'postcss-loader'],
        },
      ],
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: './src/index.html',
        filename: 'index.html',
        chunks: ['main'],
        title: 'Paper Desk',
      }),
      new HtmlWebpackPlugin({
        template: './src/devtools/panel/panel.html',
        filename: 'devtools.html',
        chunks: ['devtools'],
        title: 'Paper Desk — esp DevTools',
      }),
      new webpack.DefinePlugin({
        'process.env.NODE_ENV': JSON.stringify(isDev ? 'development' : 'production'),
      }),
    ],
    devtool: isDev ? 'eval-source-map' : 'source-map',
    devServer: {
      port: 3000,
      // Only unknown app routes fall back to index.html; devtools.html (and
      // its chunk) must be served as themselves, or the standalone window
      // would just load the main app again.
      historyApiFallback: {
        rewrites: [
          { from: /^\/devtools\.html$/, to: '/devtools.html' },
          { from: /./, to: '/index.html' },
        ],
      },
      hot: true,
      proxy: [
        { context: ['/api'], target: 'http://localhost:8080' },
        { context: ['/ws'], target: 'http://localhost:8080', ws: true },
      ],
    },
    performance: { hints: false },
  };
};
