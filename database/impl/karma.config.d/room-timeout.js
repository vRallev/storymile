// The first database test loads SQLite WASM in a separate worker.
config.client.mocha = { ...config.client.mocha, timeout: 15000 };
// Karma serves generated worker assets through /absolute/, which shared chunks do not preserve.
config.webpack.optimization = {
  ...config.webpack.optimization,
  splitChunks: false,
  runtimeChunk: false,
};
