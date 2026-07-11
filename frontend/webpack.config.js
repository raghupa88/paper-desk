const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
  const isDev = argv.mode !== 'production';
  return {
    entry: './src/index.tsx',
    output: {
      path: path.resolve(__dirname, 'dist'),
      filename: isDev ? '[name].js' : '[name].[contenthash].js',
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
      new HtmlWebpackPlugin({ template: './src/index.html', title: 'Paper Desk' }),
      new webpack.DefinePlugin({
        'process.env.NODE_ENV': JSON.stringify(isDev ? 'development' : 'production'),
      }),
    ],
    devtool: isDev ? 'eval-source-map' : 'source-map',
    devServer: {
      port: 3000,
      historyApiFallback: true,
      hot: true,
      proxy: [
        { context: ['/api'], target: 'http://localhost:8080' },
        { context: ['/ws'], target: 'http://localhost:8080', ws: true },
      ],
    },
    performance: { hints: false },
  };
};
