const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {chromium} = require('playwright');
test('광역 범위 참조 등록·수정·삭제와 DB별 저장 및 재계산을 검증한다', async () => {
    const browser = await chromium.launch({headless: true, channel: process.env.BROWSER_CHANNEL || 'msedge'});
    try {
        const page = await browser.newPage({viewport: {width: 1440, height: 960}});
        const errors = []; page.on('pageerror', error => errors.push(error.message)); page.on('dialog', dialog => dialog.accept());
        const primary = {objects: {shooter: {values: {damage: 10, attack_interval: 2, quantity: 2, mana_cost: 5, radius: 1}, tags: ['TYPE_Unit']}, shot: {values: {damage: 40, radius: 4}, tags: []}}, magics: [], references: {}};
        const secondary = structuredClone(primary), data = {primary, secondary, secondaryEnabled: true};
        let fail = false, lastRequest;
        await page.route('**/api/admin/spread-sheets/references', route => {
            const body = route.request().postDataJSON(); lastRequest = body;
            if (fail) return route.fulfill({status: 400, body: '순환 참조는 저장할 수 없습니다.'});
            const refs = data[body.db].references;
            refs[body.objectName] ||= {};
            if (body.remove) delete refs[body.objectName][body.parameterName];
            else refs[body.objectName][body.parameterName] = {objectName: body.targetObjectName, parameterName: body.targetParameterName, multiplier: body.multiplier};
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(refs)});
        });
        const html = fs.readFileSync('src/main/resources/templates/admin-spreadsheet.html', 'utf8').replace(/<script[\s\S]*?<\/script>/g, '').replace('</head>', '<style>' + fs.readFileSync('src/main/resources/static/css/balance-sheet.css', 'utf8') + '</style></head>');
        await page.route('http://balance.test/', route => route.fulfill({contentType: 'text/html; charset=utf-8', body: html}));
        async function open() {
            await page.goto('http://balance.test/'); await page.evaluate(data => window.balanceSheetData = data, data);
            await page.addScriptTag({path: 'src/main/resources/static/js/balance-model.js'});
            await page.addScriptTag({path: 'src/main/resources/static/js/balance-sheet.js'});
        }
        await open();
        assert.doesNotMatch(await page.locator('#balance-table thead').innerText(), /광역 피해 반경/);
        await page.locator('#sheet-reference-panel').evaluate(e => e.open = true);
        await page.selectOption('#ref-db', 'secondary'); await page.selectOption('#ref-object', 'shooter');
        await page.fill('#ref-parameter', 'attack_area_radius'); await page.selectOption('#ref-target', 'shot');
        await page.selectOption('#ref-target-parameter', 'radius'); await page.fill('#ref-multiplier', '0.5');
        await page.click('#ref-save'); await page.waitForFunction(() => document.getElementById('ref-status').textContent.includes('참조를 저장했습니다'));
        assert.equal(lastRequest.db, 'secondary');
        assert.equal(await page.locator('td[data-name="shooter"][data-metric="attack_area_radius"][data-db="secondary"] span').innerText(), '2');
        assert.match(await page.locator('td[data-name="shooter"][data-metric="attack_area"][data-db="secondary"] span').innerText(), /12.5664/);
        assert.equal(Object.keys(primary.references).length, 0);
        await page.fill('#ref-parameter', 'damage'); await page.selectOption('#ref-target-parameter', 'damage'); await page.fill('#ref-multiplier', '1');
        await page.click('#ref-save'); await page.waitForFunction(() => document.querySelector('td[data-name="shooter"][data-metric="total_dps"][data-db="secondary"] span').textContent === '40');
        await open();
        assert.equal(await page.locator('td[data-name="shooter"][data-metric="attack_area_radius"][data-db="secondary"] span').innerText(), '2');
        await page.locator('#sheet-reference-panel').evaluate(e => e.open = true); await page.selectOption('#ref-db', 'secondary');
        await page.locator('#ref-list li').filter({hasText: 'shooter.attack_area_radius'}).getByRole('button', {name: '수정'}).click();
        await page.fill('#ref-multiplier', '1'); await page.click('#ref-save');
        await page.waitForFunction(() => document.querySelector('td[data-name="shooter"][data-metric="attack_area_radius"][data-db="secondary"] span').textContent === '4');
        await page.screenshot({path: 'build/balance-reference-preview.png', fullPage: true});
        fail = true; await page.click('#ref-save');
        await page.waitForFunction(() => document.getElementById('ref-status').textContent.includes('순환 참조'));
        assert.equal(await page.locator('#ref-save').isEnabled(), true); fail = false;
        await page.locator('#ref-list li').filter({hasText: 'shooter.attack_area_radius'}).getByRole('button', {name: '삭제'}).click();
        await page.waitForFunction(() => document.getElementById('ref-status').textContent.includes('삭제했습니다'));
        assert.doesNotMatch(await page.locator('#balance-table thead').innerText(), /광역 피해 반경/);
        assert.deepEqual(errors, []);
    } finally { await browser.close(); }
});
