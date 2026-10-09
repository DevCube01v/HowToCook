'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const MarkdownIt = require('markdown-it');
const ROOT = path.resolve(__dirname, '../..');
const OUTPUT = path.resolve(__dirname, '../app/build/generated/recipeAssets');
const ORIGIN = 'https://appassets.androidplatform.net/assets/';
const REPO = 'https://github.com/DevCube01v/HowToCook';
const CATEGORIES = {
  vegetable_dish: '素菜', meat_dish: '荤菜', aquatic: '水产', breakfast: '早餐',
  staple: '主食', soup: '汤与粥', dessert: '甜品', drink: '饮品',
  condiment: '调味料', 'semi-finished': '半成品',
};
const encodePath = value => value.split('/').map(encodeURIComponent).join('/');
const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
function files(dir) {
  return fs.readdirSync(path.join(ROOT, dir), {withFileTypes: true}).sort((a,b) => a.name.localeCompare(b.name, 'en')).flatMap(e => {
    const name = `${dir}/${e.name}`;
    return e.isDirectory() ? files(name) : [name];
  });
}
function resolveLink(current, value) {
  if (/^(https?:|mailto:)/i.test(value)) return {external: true, url: value};
  if (value.startsWith('#')) return {url: value};
  if (/^[a-z][a-z\d+.-]*:/i.test(value) || value.startsWith('//')) throw new Error(`Unsupported URL in ${current}: ${value}`);
  const split = value.search(/[?#]/);
  const pathname = decodeURIComponent(split < 0 ? value : value.slice(0, split));
  const suffix = split < 0 ? '' : value.slice(split);
  let resolved = path.posix.normalize(pathname.startsWith('/') ? pathname.slice(1) : path.posix.join(path.posix.dirname(current), pathname));
  if (resolved.startsWith('../')) throw new Error(`Link escapes repository: ${value}`);
  if (!fs.existsSync(path.join(ROOT, resolved))) {
    const aliases = {'dishes/condiment/糖色.md': 'dishes/condiment/简易版炒糖色.md'};
    const candidates = [...files('dishes'), ...files('tips')];
    const matches = aliases[resolved] ? [aliases[resolved]] : candidates.filter(p => path.posix.basename(p) === path.posix.basename(resolved));
    if (matches.length !== 1 || !resolved.endsWith('.md')) throw new Error(`Missing local link in ${current}: ${value}`);
    console.log(`Repair link for app: ${current}: ${resolved} -> ${matches[0]}`);
    resolved = matches[0];
  }
  return {path: resolved, url: ORIGIN + 'content/' + encodePath(resolved.replace(/\.md$/i, '.html')) + suffix};
}
function render(markdown, current, revision) {
  const md = new MarkdownIt({html: false, linkify: true, typographer: false});
  const defaultImage = md.renderer.rules.image;
  md.renderer.rules.image = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    const link = resolveLink(current, token.attrGet('src'));
    if (link.external) return `<p class="external-image">外部图片：${escape(token.content)} · <a href="${escape(link.url)}">联网查看原图</a>（未包含在离线包中）</p>`;
    token.attrSet('src', link.url);
    return defaultImage(tokens, idx, options, env, self);
  };
  md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    token.attrSet('href', resolveLink(current, token.attrGet('href')).url);
    return self.renderToken(tokens, idx, options);
  };
  const slugs = new Map();
  md.core.ruler.push('heading-anchors', state => {
    state.tokens.forEach((t, i) => {
      if (t.type !== 'heading_open') return;
      const text = state.tokens[i + 1].content;
      const base = text.toLowerCase().replace(/[\p{P}\p{S}]/gu, '').replace(/\s/g, '-');
      const n = slugs.get(base) || 0; slugs.set(base, n + 1);
      t.attrSet('id', base + (n ? `-${n}` : ''));
    });
  });
  const source = `${REPO}/blob/${revision}/${encodePath(current)}`;
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self'; style-src 'unsafe-inline'"><style>
body{margin:0;padding:20px 20px 40px;background:#faf8f2;color:#25322b;font:17px/1.8 system-ui,sans-serif;overflow-wrap:anywhere}h1{font-size:27px;line-height:1.4}h2{font-size:21px;margin-top:30px;border-bottom:1px solid #dce4da;padding-bottom:8px}h3{font-size:19px}a{color:#236747}img{max-width:100%;height:auto;border-radius:12px}ul,ol{padding-left:25px}pre{overflow:auto;background:#edf0e8;padding:12px}blockquote,.external-image{border-left:3px solid #d1aa66;margin:16px 0;padding:8px 14px;background:#f1ecdf}table{border-collapse:collapse;display:block;overflow:auto}td,th{padding:8px;border:1px solid #d5dccf}footer{margin-top:36px;border-top:1px solid #dce4da;padding-top:16px;font-size:13px;color:#627266}
</style></head><body>${md.render(markdown)}<footer>来源：HowToCook · Anduin2017 与社区贡献者<br>菜谱原文按原 Markdown 呈现，图片与链接适配离线阅读。<br><a href="${source}">查看 GitHub 原文</a> · The Unlicense<br>内容版本：${revision.slice(0, 12)}</footer></body></html>`;
}
function build() {
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: ROOT, encoding: 'utf8'}).trim();
  const all = [...files('dishes'), ...files('tips'), 'README.md', 'LICENSE'].filter(p => !p.startsWith('dishes/template/'));
  fs.rmSync(OUTPUT, {recursive:true, force:true});
  const recipes = [];
  for (const relative of all) {
    const source = path.join(ROOT, relative);
    const destination = path.join(OUTPUT, 'content', relative);
    fs.mkdirSync(path.dirname(destination), {recursive:true});
    fs.copyFileSync(source, destination); // Preserve the original bytes.
    if (!relative.endsWith('.md') || relative === 'README.md') continue;
    const markdown = fs.readFileSync(source, 'utf8');
    const html = render(markdown, relative, revision);
    fs.writeFileSync(destination.replace(/\.md$/, '.html'), html);
    if (!relative.startsWith('dishes/')) continue;
    const category = CATEGORIES[relative.split('/')[1]];
    if (!category) throw new Error(`Unknown category: ${relative}`);
    const title = path.posix.basename(relative, '.md');
    const difficulty = (markdown.match(/预估烹饪难度：\s*(★+)/) || [,''])[1];
    const calories = (markdown.match(/预估卡路里：\s*(\d+)\s*大卡/) || [,''])[1];
    recipes.push({id: relative, title, category, difficulty, calories,
      html: 'content/' + relative.replace(/\.md$/, '.html'), search: title + '\n' + markdown});
  }
  fs.writeFileSync(path.join(OUTPUT, 'recipes.json'), JSON.stringify({revision, repository: REPO, categories:Object.values(CATEGORIES), recipes}));
  fs.writeFileSync(path.join(OUTPUT, 'attribution.html'), render(`# 关于下厨指南\n\n本应用的菜谱来自 [HowToCook](https://github.com/Anduin2017/HowToCook)，作者为 Anduin2017 与社区贡献者；本次内容快照来自 [当前仓库](${REPO})。\n\n内容版本：\`${revision}\`\n\n收录 ${recipes.length} 道菜谱，支持分类、全文搜索、收藏与离线阅读。菜谱正文和仓库内图片均内置，无需下载。外部图片保留原始链接，需要联网查看。外部网页通过系统浏览器打开。\n\n菜谱中的难度、热量等信息保持原文。菜谱更新随新版 APK 提供，收藏保存在本机，卸载后清除。\n\n## 许可\n\n${fs.readFileSync(path.join(ROOT, 'LICENSE'), 'utf8')}\n`, 'README.md', revision));
  console.log(`Generated ${recipes.length} recipes; revision ${revision}; assets ${OUTPUT}`);
  return {recipes, revision, all};
}
if (require.main === module) build();
module.exports = {build, render, resolveLink, ROOT, OUTPUT};
