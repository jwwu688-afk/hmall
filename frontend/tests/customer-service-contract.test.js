const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const frontend = path.resolve(__dirname, '..');
const nginx = fs.readFileSync(path.join(frontend, 'conf', 'nginx.conf'), 'utf8');
const script = fs.readFileSync(path.join(frontend, 'html', 'hmall-portal', 'js', 'customer-service.js'), 'utf8');
const page = fs.readFileSync(path.join(frontend, 'html', 'hmall-portal', 'customer-service.html'), 'utf8');

test('browser keeps the public customer-service API and resume cursor', () => {
  assert.match(script, /fetch\(`\/api\/customer-service\$\{path\}`/);
  assert.match(script, /events\?after=\$\{session\.lastEventId \|\| 0\}/);
  assert.match(script, /result\.Authorization = userToken/);
});

test('nginx routes customer-service before the generic API proxy', () => {
  const customerLocation = nginx.indexOf('location /api/customer-service/');
  const genericLocation = nginx.indexOf('location /api {');
  assert.ok(customerLocation >= 0, 'missing customer-service location');
  assert.ok(customerLocation < genericLocation, 'customer-service route must precede /api');
  const block = nginx.slice(customerLocation, genericLocation);
  assert.match(block, /proxy_pass http:\/\/localhost:8087\/customer-service\//);
  assert.match(block, /proxy_set_header Authorization \$http_authorization/);
  assert.match(block, /proxy_buffering off/);
  assert.match(block, /proxy_read_timeout 300s/);
});

test('page exposes guest, account and handoff entry points', () => {
  assert.match(page, /data-question="帮我看看有什么在售的商品"/);
  assert.match(page, /data-question="查询我的最近订单"/);
  assert.match(page, /id="handoff-button"/);
  assert.match(script, /ticket\.status !== 'QUEUED'/);
});
