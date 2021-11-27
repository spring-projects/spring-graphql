const HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');
const isDev = process.env.NODE_ENV === 'development';

module.exports = {
  entry: isDev
    ? [
        'react-hot-loader/patch',
        'webpack-dev-server/client?http://localhost:8080',
        'webpack/hot/only-dev-server',
        './src/index.tsx',
      ]
    : './src/index.tsx',
  mode: 'production',
  devtool: '',
  // mode: 'development',
  // devtool: 'inline-source-map',
  performance: {
    hints: false,
  },
  module: {
    rules: [
      {
        test: /\.html$/,
        use: ['file?name=[name].[ext]'],
      },
      {
        test: /\.(ts|tsx)$/,
        use: [
          {
            loader: 'babel-loader',
            options: {
              presets: [
                [
                  '@babel/preset-env',
                  {
                    modules: false
                  }
                ],
                '@babel/preset-react',
              ],
            },
          },
        ],
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader'],
      },
      {
        test: /\.svg$/,
        use: [{ loader: 'svg-inline-loader' }],
      },
    ],
  },
  resolve: {
    extensions: ['.ts', '.js', '.json', '.tsx', '.jsx', '.css', '.mjs'],
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: path.join(__dirname, '/index.html.ejs'),
    }),
  ],
  devServer: {
    hot: true,
  },
  node: {
    fs: 'empty',
    module: 'empty',
  },
};
