'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {build, render, resolveLink, ROOT, OUTPUT} = require('./build-content');
const result = build();
test('every real recipe is included once and original bytes are retained', () => {
  const ids = new Set(result.recipes.map(r => r.id));
  assert.equal(ids.size, result.all.filter(p => p.startsWith('dishes/') && p.endsWith('.md')).length);
  assert.ok(ids.size > 300);
  assert.equal(result.recipes.length, ids.size);
  assert.equal(new Set(result.recipes.map(r => r.category)).size, 10);
  for (const p of result.all) {
    assert.deepEqual(fs.readFileSync(path.join(ROOT, p)), fs.readFileSync(path.join(OUTPUT, 'content', p)));
  }
});
test('every local HTML image and link points to a bundled asset', () => {
  let images = 0;
  for (const r of result.recipes) {
    const html = fs.readFileSync(path.join(OUTPUT, r.html), 'utf8');
    for (const match of html.matchAll(/(?:src|href)="https:\/\/appassets.androidplatform.net\/assets\/([^"?#]+)/g)) {
      assert.ok(fs.existsSync(path.join(OUTPUT, decodeURIComponent(match[1]))), `${r.id}: ${match[1]}`);
      if (match[0].startsWith('src')) images++;
    }
    assert.ok(html.includes(result.revision));
  }
  assert.ok(images > 250);
});
test('relative links, encoded paths and existing broken upstream links are resolved', () => {
  assert.equal(resolveLink('dishes/drink/金菲士/金菲士.md', '../../condiment/蔗糖糖浆/蔗糖糖浆.md').path, 'dishes/condiment/蔗糖糖浆/蔗糖糖浆.md');
  assert.equal(resolveLink('dishes/meat_dish/卤菜/卤菜.md', '../../condiment/糖色.md').path, 'dishes/condiment/简易版炒糖色.md');
  assert.throws(() => resolveLink('README.md', '../outside.md'));
  assert.throws(() => resolveLink('README.md', 'javascript:alert(1)'));
});
test('external images remain explicit source links; script and raw HTML cannot execute', () => {
  const html = render('# Test\n\n![外图](https://example.com/a.jpg)\n\n<script>alert(1)</script>\n\n[unsafe](javascript:alert(1))', 'README.md', 'test-revision');
  assert.ok(html.includes('联网查看原图'));
  assert.ok(html.includes('href="https://example.com/a.jpg"'));
  assert.ok(!html.includes('<script>'));
  assert.ok(!html.includes('href="javascript:'));
  assert.ok(!html.includes('src="https://example.com'));
});
test('headings support fragment navigation and attribution includes license', () => {
  const html = render('# 菜名\n\n## 必备原料和工具\n\n## 操作\n\n## 操作', 'README.md', 'version');
  assert.ok(html.includes('id="必备原料和工具"'));
  assert.ok(html.includes('id="操作-1"'));
  assert.ok(fs.readFileSync(path.join(OUTPUT, 'attribution.html'), 'utf8').includes('free and unencumbered'));
});
