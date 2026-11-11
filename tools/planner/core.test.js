const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const C = require('./core');
const fixture = () => JSON.parse(fs.readFileSync(require('node:path').join(__dirname, '../../schema/examples/demo.trip.json')));

test('airport gaps become unbooked rows and filling them again does not duplicate them', () => {
  const t = fixture(), before = C.coverage(t).filter(x => !x.row).length;
  assert.ok(before > 0);
  assert.equal(C.addAirportJourneys(t), before);
  assert.ok(C.coverage(t).every(x => x.row));
  assert.equal(C.addAirportJourneys(t), 0);
  const arrival = C.coverage(t).find(x => x.flight.id === 'fly-out' && x.journey === 'arrival');
  assert.equal(arrival.row.date, '2027-10-18');
  assert.equal(arrival.row.status, 'research');
  assert.equal(t.transfers.find(f => f.id === 'fly-out').booking.status, 'booked');
});

test('manual transfer and research export as a commitment plus portable planning data', () => {
  const p = C.openProject(fixture());
  const key = C.add(p.trip, {type: 'transfer', title: 'Taxi from airport', date: '2027-10-18', bookBy: '2027-10-17', stayId: 'palermo', status: 'resolved', critical: true, nextAction: 'Use the stand', fields: {Pickup: 'Terminal'}, options: [{name: 'Taxi', estimate: 'Unknown'}]});
  const exported = C.exportTrip(p), row = C.rows(exported).find(r => r.key === key);
  assert.equal(row.status, 'resolved');
  assert.equal(row.bookBy, '2027-10-17');
  assert.equal(exported.commitments.find(c => key.endsWith(c.id)).booked, false);
  assert.deepEqual(row.fields, {Pickup: 'Terminal'});
  assert.equal(row.options[0].name, 'Taxi');
  assert.equal(exported.revision, p.trip.revision + 1);
  assert.equal(exported.validated, undefined);
});

test('phone booking facts combine with independent desktop research, with defaults omitted', () => {
  const p = C.openProject(fixture()); C.addAirportJourneys(p.trip);
  const baseline = C.exportTrip(p), local = C.clone(baseline), phone = C.clone(baseline);
  const target = local.planning.find(p => p.flightId);
  target.options = [{name: 'Shuttle', notes: 'Compare luggage allowance'}];
  const c = phone.commitments.find(c => 'commitment:' + c.id === target.id);
  c.booked = true; c.ref = 'DEMO-CONFIRMATION';
  for (const p of phone.planning) { if (p.status === 'research') delete p.status; if (p.critical) delete p.critical; }
  const result = C.merge(baseline, local, phone);
  assert.deepEqual(result.conflicts, []);
  assert.equal(result.trip.commitments.find(x => x.id === c.id).ref, 'DEMO-CONFIRMATION');
  assert.equal(result.trip.planning.find(x => x.id === target.id).options[0].name, 'Shuttle');
});

test('concurrent edits and delete-versus-edit require an explicit conflict choice', () => {
  const b = fixture(), l = C.clone(b), p = C.clone(b);
  l.transfers[0].depart = '20:00'; p.transfers[0].depart = '21:00';
  let r = C.merge(b, l, p);
  assert.equal(r.conflicts.length, 1); assert.equal(r.trip.transfers[0].depart, '20:00');
  r = C.merge(b, l, p, {[r.conflicts[0].path]: 'phone'});
  assert.equal(r.trip.transfers[0].depart, '21:00');
  l.transfers.shift(); r = C.merge(b, l, p);
  assert.equal(r.conflicts.length, 1);
  assert.ok(!r.trip.transfers.some(t => t.id === b.transfers[0].id));
  assert.throws(() => C.merge(b, l, {...p, id: 'wrong-trip'}), /different trip/);
});

test('booking an activity creates one linked commitment and clearing a confirmation really removes it', () => {
  const t = fixture(), row = C.rows(t).find(r => r.type === 'event' && r.status !== 'booked');
  assert.ok(row);
  C.edit(t, row.key, {...row, date: t.stays.find(s => s.id === row.stayId).arrive, status: 'booked', ref: 'DEMO'});
  assert.equal(C.rows(t).filter(r => r.key === row.key).length, 1);
  assert.equal(C.rows(t).find(r => r.key === row.key).status, 'booked');
  const c = t.commitments.find(c => c.activityId === row.key.slice(9));
  C.edit(t, row.key, {...C.rows(t).find(r => r.key === row.key), ref: ''});
  assert.equal(t.commitments.find(x => x.id === c.id).ref, undefined);
  assert.equal(t.commitments.filter(x => x.activityId === row.key.slice(9)).length, 1);
});

test('editing lodging preserves its imported check-in and check-out times', () => {
  const t = fixture(), r = C.rows(t).find(r => r.key === 'lodging:palermo');
  assert.equal(r.start, '15:00'); assert.equal(r.end, '11:00');
  C.edit(t, r.key, {...r, nextAction: 'Check entry instructions'});
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.checkIn, '15:00');
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.checkOut, '11:00');
});

test('row status changes preserve reservation details and do not invent a lodging name', () => {
  const t = fixture(), original = C.clone(t.transfers[0]);
  C.setStatus(t, 'transfer:' + original.id, 'resolved');
  assert.equal(C.rows(t).find(r => r.key === 'transfer:' + original.id).status, 'resolved');
  assert.deepEqual(t.transfers[0].booking.price, original.booking.price);
  assert.equal(t.transfers[0].booking.ref, original.booking.ref);
  C.setStatus(t, 'transfer:' + original.id, 'booked');
  assert.equal(C.rows(t).find(r => r.key === 'transfer:' + original.id).status, 'booked');
  C.setStatus(t, 'transfer:' + original.id, 'research');
  assert.equal(C.rows(t).find(r => r.key === 'transfer:' + original.id).status, 'research');
  const s = t.stays.find(s => s.id === 'madryn'); delete s.lodging.name;
  const options = C.clone(s.lodging.options);
  C.setStatus(t, 'lodging:madryn', 'resolved');
  assert.equal(s.lodging.name, undefined);
  assert.deepEqual(s.lodging.options, options);
});

test('an older phone export cannot silently discard research', () => {
  const b = fixture(), p = C.clone(b); delete p.planning;
  assert.throws(() => C.merge(b, b, p), /omitted planning details/);
  assert.throws(() => C.merge({...b, revision: 2}, {...b, revision: 2}, b), /predates/);
});
