(function (root) {
    'use strict';
    // 전투 코드에서 확인한 교차 객체 참조만 등록합니다. 값이 없다고 다른 객체로 추측하지 않습니다.
    const references = {
        lightning_cloud: {damage: ['lightning_drop', 'damage'], effect_radius: ['lightning_drop', 'radius']},
        bean_shooter: {projectile_speed: ['bean', 'speed'], projectile_radius: ['bean', 'radius']},
        water_slime: {attack_range: ['aqua_archer', 'attack_range']},
        grass_generator: {leaf_field_radius: ['leaf_field', 'radius']}
    };
    const metrics = ['summon_count', 'total_dps', 'dps_per_mana', 'total_hp', 'hp_per_mana', 'individual_dps'];
    const labels = {quantity: '원본 수량', summon_count: '소환 수', mana_cost: '마나', total_dps: '총 DPS', dps_per_mana: 'DPS/마나',
        total_hp: '총 HP', hp_per_mana: 'HP/마나', individual_dps: '개체 DPS', hp: '개체 HP',
        damage: '공격력', attack_interval: '공격 주기', attack_range: '사거리', speed: '이동 속도',
        projectile_speed: '투사체 속도', projectile_radius: '투사체 반지름'};
    function magic(snapshot, name) { return snapshot.magics.find(m => m.name === name); }
    function kind(snapshot, name) {
        const m = magic(snapshot, name) || snapshot.magics.find(m => m.objectName === name);
        const tags = (snapshot.objects[name]?.tags || []).map(t => t.toLowerCase());
        if (m?.kind === 'Spawn' || tags.includes('unit') || tags.includes('mob')) return 'unit';
        if (m?.kind === 'Summon' || tags.includes('building')) return 'building';
        if (m?.kind === 'Shot' || tags.includes('projectile')) return 'projectile';
        return m ? 'effect' : 'other';
    }
    function value(snapshot, name, key, raw = false) {
        let source = name, parameter = key;
        const m = magic(snapshot, name);
        if (!raw && m && !['mana_cost', 'quantity', 'magic_id'].includes(key)) source = m.objectName;
        if (!raw && references[source]?.[key]) [source, parameter] = references[source][key];
        const number = snapshot.objects[source]?.values[parameter];
        return {value: typeof number === 'number' && Number.isFinite(number) ? number : null,
            source, parameter, reason: `${source}.${parameter}`};
    }
    function calculate(snapshot, name, key) {
        if (!metrics.includes(key)) return value(snapshot, name, key);
        const q = value(snapshot, name, 'quantity'), d = value(snapshot, name, 'damage');
        if (key === 'summon_count') {
            const valid = kind(snapshot, name) === 'unit' && q.value > 0 && Number.isInteger(q.value);
            return {value: valid ? q.value : null, reason: valid ? `소환 수: ${q.reason} = ${q.value}` : '유닛의 양의 정수 quantity가 필요합니다. 효과 횟수를 소환 수로 사용하지 않습니다.'};
        }
        const t = value(snapshot, name, 'attack_interval'), h = value(snapshot, name, 'hp');
        const m = value(snapshot, name, 'mana_cost');
        const hp = key === 'total_hp' || key === 'hp_per_mana';
        const perMana = key.endsWith('per_mana');
        const total = key !== 'individual_dps';
        const terms = hp ? [h] : [d, t];
        if (total) terms.push(q);
        if (perMana) terms.push(m);
        const missing = terms.filter(v => v.value === null);
        if (missing.length) return {value: null, reason: `값 없음: ${missing.map(v => v.reason).join(', ')}`};
        if ((!hp && t.value <= 0) || (perMana && m.value <= 0) || (total && (q.value < 1 || !Number.isInteger(q.value))) || (hp ? h.value < 0 : d.value < 0)) {
            return {value: null, reason: '공격 주기·마나는 양수, 소환 수는 양의 정수, HP·공격력은 0 이상이어야 합니다.'};
        }
        // quantity가 낙뢰 횟수인 효과 등을 동시 소환 개체 수로 해석하지 않습니다.
        if (total && kind(snapshot, name) !== 'unit') {
            return {value: null, reason: '동시 소환 수가 확인된 유닛만 합산합니다. 건물·효과의 quantity는 소환 수로 가정하지 않습니다.'};
        }
        let result = hp ? h.value : d.value / t.value;
        let formula = hp ? `${h.value}` : `${d.value} ÷ ${t.value}`;
        if (total) { result *= q.value; formula = `(${formula}) × ${q.value}마리`; }
        if (perMana) { result /= m.value; formula = `(${formula}) ÷ ${m.value}마나`; }
        return {value: Number.isFinite(result) ? result : null,
            reason: `${formula} = ${result}; 출처: ${terms.map(v => v.reason).join(', ')}. 모든 개체가 동시에 단일 대상을 공격하는 기본 공격 기준입니다. 광역·연쇄·지속 피해는 제외합니다.`};
    }
    function matches(snapshot, name, type, tags) {
        if (!snapshot.objects[name]) return false;
        return (!type || kind(snapshot, name) === type) && tags.every(tag => snapshot.objects[name].tags.includes(tag));
    }
    function group(key) {
        if (['quantity', 'mana_cost', ...metrics].includes(key)) return '핵심 지표';
        if (['hp', 'mass', 'radius'].includes(key)) return '생존';
        if (/^(damage|attack_|sub_damage|sub_attack)/.test(key)) return '공격';
        if (/projectile/.test(key)) return '투사체';
        if (/speed|acceleration/.test(key)) return '이동';
        if (/_id$/.test(key)) return '내부 설정';
        return '특수 효과';
    }
    const api = {references, metrics, labels, kind, value, calculate, matches, group};
    if (typeof module !== 'undefined') module.exports = api;
    else root.BalanceModel = api;
})(typeof window === 'undefined' ? globalThis : window);
