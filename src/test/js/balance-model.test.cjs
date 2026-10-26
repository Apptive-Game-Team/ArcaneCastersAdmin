const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../../main/resources/static/js/balance-model.js');
function snapshot() {
    return {objects: {
        swarm: {values: {quantity: 3, mana_cost: 5}, tags: []},
        unit: {values: {damage: 20, attack_interval: 2, hp: 100}, tags: []},
        bean_shooter: {values: {damage: 130, quantity: 2, mana_cost: 10, attack_interval: 2}, tags: ['unit']},
        bean: {values: {damage: 999, speed: 7}, tags: []},
        lightning_cloud: {values: {quantity: 10, attack_interval: 2}, tags: []},
        lightning_drop: {values: {damage: 50}, tags: []}
    }, magics: [{name: 'swarm', kind: 'Spawn', objectName: 'unit'}, {name: 'lightning_cloud', kind: 'Drop', objectName: 'lightning_cloud'}]};
}
test('군집 소환의 개체 능력치와 소환 수 및 마나 출처를 분리한다', () => {
    const s = snapshot();
    assert.equal(M.calculate(s, 'swarm', 'individual_dps').value, 10);
    assert.equal(M.calculate(s, 'swarm', 'total_dps').value, 30);
    assert.equal(M.calculate(s, 'swarm', 'dps_per_mana').value, 6);
    assert.equal(M.calculate(s, 'swarm', 'total_hp').value, 300);
    assert.equal(M.calculate(s, 'swarm', 'hp_per_mana').value, 60);
    assert.match(M.calculate(s, 'swarm', 'total_dps').reason, /unit.damage.*swarm.quantity/);
    s.objects.unit.values.damage = 40;
    assert.equal(M.calculate(s, 'swarm', 'total_dps').value, 60);
});
test('확인된 참조만 사용하고 Bean Shooter 공격력은 자체 값을 사용한다', () => {
    const s = snapshot();
    assert.equal(M.value(s, 'bean_shooter', 'damage').value, 130);
    assert.equal(M.value(s, 'bean_shooter', 'projectile_speed').source, 'bean');
    assert.equal(M.calculate(s, 'lightning_cloud', 'individual_dps').value, 25);
    assert.equal(M.calculate(s, 'lightning_cloud', 'total_dps').value, null);
});
test('누락 수량과 0 분모 및 소수 수량을 계산 불가로 처리하고 피해 0은 유지한다', () => {
    const s = snapshot();
    delete s.objects.swarm.values.quantity;
    assert.equal(M.calculate(s, 'swarm', 'total_dps').value, null);
    assert.match(M.calculate(s, 'swarm', 'total_dps').reason, /swarm.quantity/);
    s.objects.swarm.values.quantity = 1.5;
    assert.equal(M.calculate(s, 'swarm', 'total_dps').value, null);
    s.objects.swarm.values.quantity = 3; s.objects.unit.values.damage = 0;
    assert.equal(M.calculate(s, 'swarm', 'total_dps').value, 0);
    s.objects.unit.values.attack_interval = 0;
    assert.equal(M.calculate(s, 'swarm', 'total_dps').value, null);
    s.objects.unit.values.attack_interval = 2; s.objects.swarm.values.mana_cost = 0;
    assert.equal(M.calculate(s, 'swarm', 'dps_per_mana').value, null);
});
test('태그 없이도 Spawn 유닛을 분류하고 태그는 완전 일치한다', () => {
    const dev = snapshot(), deploy = {objects: {}, magics: []};
    assert.equal(M.matches(dev, 'swarm', 'unit', []), true);
    assert.equal(M.matches(deploy, 'swarm', 'unit', []), false);
    dev.objects.swarm.tags = ['unit-elite'];
    assert.equal(M.matches(dev, 'swarm', 'unit', ['unit']), false);
    assert.equal(M.matches(dev, 'swarm', 'unit', ['unit-elite']), true);
});
