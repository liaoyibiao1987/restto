/* Airbnb JS 风格 + Vue 插件（对应 AGENT.MD 第 4 节） */
module.exports = {
  root: true,
  env: { browser: true, es2022: true, node: true },
  extends: ['airbnb-base', 'plugin:vue/vue3-essential', 'prettier'],
  parserOptions: { ecmaVersion: 2022, sourceType: 'module' },
  rules: {
    'vue/multi-word-component-names': 'off',
    'import/no-extraneous-dependencies': 'off',
    'no-param-reassign': 'off',
    'max-len': ['warn', { code: 280 }],
  },
};
