(function () {
    'use strict';
    const M = window.BalanceModel, original = window.balanceSheetData;
    const current = structuredClone(original), $ = id => document.getElementById(id);
    const storageKey = 'balance-sheet-view-v1';
    const controls = ['search', 'kind', 'tags', 'basis', 'view', 'sort-db', 'sort', 'desc', 'empty', 'diff-rows', 'diff-cols', 'selected'];
    const selected = new Set(), collapsed = new Set(['내부 설정']);
    const names = [...new Set([...Object.keys(current.primary.objects), ...Object.keys(current.secondary.objects)])].sort();
    const priority = ['summon_count', 'mana_cost', 'total_dps', 'dps_per_mana', 'total_hp', 'hp_per_mana', 'individual_dps', 'quantity', 'hp', 'damage', 'attack_interval', 'attack_range', 'speed'];
    const rawKeys = new Set(names.flatMap(name => ['primary', 'secondary'].flatMap(db => Object.keys(current[db].objects[name]?.values || {}))));
    Object.values(M.references).forEach(refs => Object.keys(refs).forEach(key => rawKeys.add(key)));
    let columns = [...new Set([...priority, ...[...rawKeys].sort()])];
    let saving = false;
    const fmt = n => n === null ? 'N/A' : new Intl.NumberFormat('ko', {maximumFractionDigits: 4}).format(n);
    const field = key => $('sheet-' + key);
    function element(tag, text, className) {
        const e = document.createElement(tag);
        if (text !== undefined) e.textContent = text;
        if (className) e.className = className;
        return e;
    }
    function button(text, action) {
        const b = element('button', text); b.type = 'button'; b.disabled = saving; b.addEventListener('click', action); return b;
    }
    function setting(key) { const e = field(key); return e.type === 'checkbox' ? e.checked : e.value; }
    function changes() {
        return names.flatMap(name => ['primary', 'secondary'].flatMap(db => {
            const values = current[db].objects[name]?.values || {};
            return Object.entries(values).filter(([key, value]) => value !== (original[db].objects[name]?.values[key] ?? null))
                .map(([key, value]) => ({gameObjectName: name, parameterName: key, value, db}));
        }));
    }
    function delta(before, after) {
        if (before === after) return '';
        const percent = before !== null && before !== 0 && after !== null ? ` (${((after - before) / Math.abs(before) * 100).toFixed(1)}%)` : '';
        return `${fmt(before)} → ${fmt(after)}${percent}`;
    }
    function remember() {
        try { localStorage.setItem(storageKey, JSON.stringify({settings: Object.fromEntries(controls.map(k => [k, setting(k)])), columns, collapsed: [...collapsed], selected: [...selected]})); } catch (_) { /* 저장 공간이 없어도 편집은 유지합니다. */ }
    }
    const sort = field('sort');
    [ ['name', '이름'], ...columns.map(k => [k, M.labels[k] || k]) ].forEach(([value, label]) => { const o = element('option', label); o.value = value; sort.append(o); });
    const queryTags = new URLSearchParams(location.search).getAll('tags').join(',');
    try {
        const saved = JSON.parse(localStorage.getItem(storageKey) || 'null');
        if (saved) {
            for (const k of controls) if (saved.settings?.[k] !== undefined) {
                const e = field(k); if (e.type === 'checkbox') e.checked = saved.settings[k]; else e.value = saved.settings[k];
            }
            columns = [...new Set([...(saved.columns || []).filter(k => columns.includes(k)), ...columns])];
            collapsed.clear(); (saved.collapsed || []).forEach(k => collapsed.add(k));
            (saved.selected || []).filter(n => names.includes(n)).forEach(n => selected.add(n));
        }
    } catch (_) { /* 손상된 보기 설정은 기본값으로 엽니다. */ }
    if (queryTags) field('tags').value = queryTags;
    if (!current.secondaryEnabled) { field('view').value = 'primary'; field('basis').value = 'primary'; field('sort-db').value = 'primary'; }
    const allTags = [...new Set(['primary', 'secondary'].flatMap(db => Object.values(current[db].objects).flatMap(o => o.tags)))].sort();
    allTags.forEach(tag => { const o = element('option'); o.value = tag; $('sheet-tag-list').append(o); });
    function columnControls() {
        const host = $('sheet-columns'); host.replaceChildren();
        [...new Set(columns.map(M.group))].forEach(group => {
            const label = element('label'), box = document.createElement('input'); box.type = 'checkbox'; box.checked = !collapsed.has(group);
            box.addEventListener('change', () => { box.checked ? collapsed.delete(group) : collapsed.add(group); render(); });
            label.append(box, document.createTextNode(group)); host.append(label);
        });
        const picker = document.createElement('select'); picker.setAttribute('aria-label', '앞으로 이동할 열');
        columns.forEach(k => { const o = element('option', M.labels[k] || k); o.value = k; picker.append(o); });
        host.append(picker, button('선택 열을 맨 앞으로', () => { columns = [picker.value, ...columns.filter(k => k !== picker.value)]; columnControls(); render(); }));
    }
    function differs(name, key) {
        return M.calculate(current.primary, name, key).value !== M.calculate(current.secondary, name, key).value
            || Boolean(current.primary.objects[name]) !== Boolean(current.secondary.objects[name]);
    }
    function visibleNames() {
        const query = setting('search').trim().toLowerCase();
        const tags = setting('tags').split(',').map(t => t.trim()).filter(Boolean);
        const dbs = setting('basis') === 'either' ? ['primary', 'secondary'] : [setting('basis')];
        const rows = names.filter(name => dbs.some(db => M.matches(current[db], name, setting('kind'), tags)))
            .filter(name => !setting('selected') || selected.has(name))
            .filter(name => !query || name.toLowerCase().includes(query) || ['primary', 'secondary'].some(db => Object.entries(current[db].objects[name]?.values || {}).some(([k, v]) => v !== null && k.toLowerCase().includes(query))))
            .filter(name => !setting('diff-rows') || columns.some(k => differs(name, k)));
        const key = setting('sort'), db = setting('sort-db'), direction = setting('desc') ? -1 : 1;
        rows.sort((a, b) => {
            if (key === 'name') return direction * a.localeCompare(b);
            const x = M.calculate(current[db], a, key).value, y = M.calculate(current[db], b, key).value;
            if (x === null || y === null) return x === y ? a.localeCompare(b) : x === null ? 1 : -1;
            return direction * (x - y) || a.localeCompare(b);
        });
        return rows;
    }
    function jump(db, source, parameter) {
        field('search').value = source; field('kind').value = ''; field('tags').value = '';
        field('basis').value = db; field('view').value = db;
        field('selected').checked = false; field('diff-rows').checked = false; field('diff-cols').checked = false;
        collapsed.delete(M.group(parameter)); render(); columnControls();
        const input = [...$('balance-table').querySelectorAll('input[data-source]')].find(e => e.dataset.source === source && e.dataset.parameter === parameter && e.dataset.db === db);
        if (input) { input.focus(); input.scrollIntoView({block: 'nearest', inline: 'center'}); }
    }
    function render() {
        const rows = visibleNames();
        const dbs = setting('view') === 'both' ? ['primary', 'secondary'] : [setting('view')];
        const visibleColumns = columns.filter(k => !collapsed.has(M.group(k)))
            .filter(k => !setting('empty') || rows.some(n => ['primary', 'secondary'].some(db => M.calculate(current[db], n, k).value !== null)))
            .filter(k => !setting('diff-cols') || rows.some(n => differs(n, k)));
        const table = $('balance-table'), head = table.tHead, body = table.tBodies[0]; head.replaceChildren(); body.replaceChildren();
        const header = element('tr'); header.append(element('th', '객체 / 비교 선택', 'object-name'));
        for (const key of visibleColumns) for (const db of dbs) {
            const th = element('th'); th.scope = 'col';
            th.append(button(`${M.labels[key] || key} · ${db === 'primary' ? 'Deploy' : 'Dev'}`, () => {
                field('desc').checked = setting('sort') === key ? !setting('desc') : true; field('sort').value = key; field('sort-db').value = db; render();
            })); header.append(th);
        }
        head.append(header);
        for (const name of rows) {
            const row = element('tr'), title = element('th', undefined, 'object-name'); title.scope = 'row';
            const select = document.createElement('input'); select.type = 'checkbox'; select.checked = selected.has(name); select.setAttribute('aria-label', `${name} 비교 선택`);
            select.addEventListener('change', () => { select.checked ? selected.add(name) : selected.delete(name); render(); });
            title.append(select, document.createTextNode(' ' + name));
            ['primary', 'secondary'].forEach(db => title.append(element('small', `${db === 'primary' ? 'Deploy' : 'Dev'}: ${current[db].objects[name] ? (current[db].objects[name].tags.join(', ') || M.kind(current[db], name)) : '객체 없음'}`)));
            row.append(title);
            for (const key of visibleColumns) for (const db of dbs) {
                const result = M.calculate(current[db], name, key), before = M.calculate(original[db], name, key).value;
                const cell = element('td'); if (differs(name, key)) cell.classList.add('different');
                if (result.value !== before) cell.classList.add('modified');
                const derived = M.metrics.includes(key), reference = !derived && (result.source !== name || result.parameter !== key);
                const present = Boolean(current[db].objects[name]);
                if (derived || reference || !present || (db === 'secondary' && !current.secondaryEnabled)) {
                    cell.append(element('span', fmt(result.value)));
                } else {
                    const input = document.createElement('input'); input.type = 'number'; input.step = 'any'; input.value = result.value ?? ''; input.placeholder = 'N/A';
                    input.dataset.source = name; input.dataset.parameter = key; input.dataset.db = db;
                    input.setAttribute('aria-label', `${name} ${key} ${db === 'primary' ? 'Deploy' : 'Dev'}`); input.disabled = saving;
                    input.addEventListener('input', () => {
                        if (input.validity.badInput || (input.value !== '' && !Number.isFinite(input.valueAsNumber))) { input.setCustomValidity('유한한 숫자를 입력하세요.'); return; }
                        input.setCustomValidity(''); current[db].objects[name].values[key] = input.value === '' ? null : input.valueAsNumber;
                        refreshDerived(); renderChanges();
                    });
                    input.addEventListener('change', () => { if (input.checkValidity()) render(); }); cell.append(input);
                }
                if (reference) cell.append(button(`← ${result.reason}`, () => jump(db, result.source, result.parameter)));
                if (derived || reference) {
                    cell.dataset.metric = key; cell.dataset.name = name; cell.dataset.db = db;
                    const details = element('details'), summary = element('summary', derived ? '계산 근거' : '참조 출처'); details.append(summary, element('small', result.reason)); cell.append(details);
                }
                const change = delta(before, result.value); if (change) cell.append(element('small', change, 'delta'));
                if (!derived && !reference && change) cell.append(button('되돌리기', () => { current[db].objects[name].values[key] = before; render(); }));
                row.append(cell);
            }
            body.append(row);
        }
        if (!rows.length) { const row = element('tr'), cell = element('td', '조건에 맞는 객체가 없습니다. 필터를 변경하세요.', 'balance-empty'); cell.colSpan = 1 + visibleColumns.length * dbs.length; row.append(cell); body.append(row); }
        $('sheet-status').textContent = `${rows.length}개 객체 · ${visibleColumns.length}개 항목 · 비교 선택 ${selected.size}개. 파란 표시: Deploy/Dev 차이, 노란 표시: 저장 전 변경.`;
        renderChanges(); remember();
    }
    function refreshDerived() {
        $('balance-table').querySelectorAll('td[data-metric]').forEach(cell => {
            const {metric, name, db} = cell.dataset, result = M.calculate(current[db], name, metric);
            cell.querySelector('span').textContent = fmt(result.value);
            cell.querySelector('details small').textContent = result.reason;
            cell.classList.toggle('modified', result.value !== M.calculate(original[db], name, metric).value);
            cell.classList.toggle('different', differs(name, metric));
            cell.querySelector('.delta')?.remove();
            const change = delta(M.calculate(original[db], name, metric).value, result.value);
            if (change) cell.append(element('small', change, 'delta'));
        });
    }
    function renderChanges() {
        const updates = changes(); $('sheet-change-count').textContent = `변경 ${updates.length}개 (숨겨진 행·열 포함)`;
        const list = $('sheet-changes'); list.replaceChildren();
        for (const u of updates) {
            const before = original[u.db].objects[u.gameObjectName]?.values[u.parameterName] ?? null;
            const item = element('li', `${u.db === 'primary' ? 'Deploy' : 'Dev'} · ${u.gameObjectName}.${u.parameterName}: ${delta(before, u.value)} `);
            const affected = names.filter(name => columns.some(key => {
                const result = M.value(current[u.db], name, key);
                return name !== u.gameObjectName && result.source === u.gameObjectName && result.parameter === u.parameterName;
            }));
            if (affected.length) item.append(element('small', `참조 영향: ${affected.join(', ')} `));
            item.append(button('되돌리기', () => { current[u.db].objects[u.gameObjectName].values[u.parameterName] = before; render(); })); list.append(item);
        }
        $('sheet-save').disabled = saving || !updates.length;
    }
    controls.forEach(k => field(k).addEventListener(k === 'search' || k === 'tags' ? 'input' : 'change', render));
    $('sheet-reset').addEventListener('click', () => {
        if (saving) return;
        if (changes().length && !confirm('저장하지 않은 변경을 버리고 보기를 초기화할까요?')) return;
        try { localStorage.removeItem(storageKey); } catch (_) { /* 초기화는 저장 공간 없이도 진행합니다. */ }
        window.onbeforeunload = null; location.href = location.pathname;
    });
    $('sheet-undo').addEventListener('click', () => { if (saving) return; if (changes().length && confirm('저장 전 변경을 모두 되돌릴까요?')) { current.primary = structuredClone(original.primary); current.secondary = structuredClone(original.secondary); render(); } });
    async function post(url, body) {
        const headers = {}; if (body !== undefined) headers['Content-Type'] = 'application/json';
        const csrf = document.querySelector('meta[name="_csrf"]'), csrfHeader = document.querySelector('meta[name="_csrf_header"]');
        if (csrf && csrfHeader) headers[csrfHeader.content] = csrf.content;
        const response = await fetch(url, {method: 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body)});
        if (!response.ok) throw new Error((await response.text()) || `저장 실패 (${response.status})`);
    }
    $('sheet-save').addEventListener('click', async () => {
        if (saving) return;
        const invalid = $('balance-table').querySelector('input:invalid'); if (invalid) { invalid.reportValidity(); return; }
        const updates = changes(); if (!updates.length) return;
        $('sheet-change-panel').open = true;
        if (!confirm(`${updates.length}개 원본 값을 저장할까요? 숨겨진 변경과 참조 객체에도 반영됩니다.`)) return;
        saving = true; render();
        try {
            await post('/api/admin/spread-sheets/game-objects/by-name', updates);
            window.onbeforeunload = null; location.reload();
        } catch (e) { saving = false; render(); $('sheet-status').textContent = `저장하지 못했습니다: ${e.message}. 일부 반영 여부를 확인한 후 다시 시도하세요.`; }
    });
    async function sync(toSecondary) {
        if (saving) return;
        if (changes().length) { alert('먼저 변경을 저장하거나 되돌리세요.'); return; }
        if (!confirm(`현재 필터와 관계없이 ${toSecondary ? 'Deploy → Dev' : 'Dev → Deploy'} 전체 값을 복사할까요?`)) return;
        try { await post('/api/admin/spread-sheets/' + (toSecondary ? 'sync-to-secondary' : 'sync-to-primary')); location.reload(); }
        catch (e) { $('sheet-status').textContent = `복사하지 못했습니다: ${e.message}`; }
    }
    $('sheet-to-dev').addEventListener('click', () => sync(true)); $('sheet-to-deploy').addEventListener('click', () => sync(false));
    window.onbeforeunload = e => { if (changes().length) { e.preventDefault(); e.returnValue = ''; } };
    columnControls(); render();
})();
