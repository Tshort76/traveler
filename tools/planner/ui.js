(() => {
  'use strict';
  const C = PlannerCore, $ = s => document.querySelector(s);
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const labels = {flight: 'Flight', transfer: 'Transfer', lodging: 'Lodging', event: 'Event', research: 'Research needed', ready: 'Ready to book', booked: 'Booked', resolved: 'Resolved'};
  const fmt = s => s ? new Date(s + 'T12:00:00').toLocaleDateString('en-US', {month: 'short', day: 'numeric'}) : '—';
  let project = C.openProject(JSON.parse($('#seed').textContent)), selected = null, newDraft = null, formDirty = false, dirty = false, filter = 'all', type = 'all', city = 'all', sort = 'date', direction = 1, handle = null, pendingPhone = null;
  let filename = project.trip.id + '.planner.json';
  const storageKey = () => 'traveler-planner:' + project.trip.id;
  const fieldSets = {
    flight: ['Airline', 'Flight number', 'Departure airport', 'Arrival airport', 'Arrival date', 'Baggage', 'Connection', 'Cancellation terms'],
    transfer: ['Operator', 'Pickup', 'Drop-off', 'Duration', 'Cancellation terms'],
    lodging: ['Address', 'Room', 'Cancellation deadline', 'Payment due'],
    event: ['Venue / operator', 'Meeting point', 'Ticket option', 'Cancellation deadline', 'Payment due'],
  };
  function message(text, error = false) { $('#message').textContent = text; $('#message').classList.toggle('error', error); }
  function draftBackup() {
    try { localStorage.setItem(storageKey(), JSON.stringify(project)); return true; } catch { return false; }
  }
  function changed() {
    dirty = true;
    const backed = draftBackup();
    $('#save-state').textContent = backed ? 'Unsaved to file · browser draft updated' : 'Unsaved to file · browser recovery unavailable';
  }
  function leave() { if (formDirty && !confirm('Discard unapplied changes in the detail form?')) return false; formDirty = false; return true; }
  function render() {
    const all = C.rows(project.trip);
    $('#trip-title').textContent = project.trip.title;
    $('#trip-dates').textContent = fmt(project.trip.startDate) + ' – ' + fmt(project.trip.endDate) + ', ' + project.trip.startDate.slice(0, 4);
    $('#filename').textContent = filename;
    const cities = [...new Set(all.map(r => r.city))].sort((a, b) => a.localeCompare(b));
    if (city !== 'all' && !cities.includes(city)) city = 'all';
    $('#city').innerHTML = '<option value="all">All cities</option>' + cities.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join(''); $('#city').value = city;
    $('#type').value = type;
    document.querySelectorAll('[data-filter]').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === filter));
    document.querySelectorAll('[data-sort]').forEach(b => { const active = b.dataset.sort === sort; b.textContent = b.dataset.label + ' ' + (active ? (direction === 1 ? '↑' : '↓') : '↕'); b.parentElement.setAttribute('aria-sort', active ? (direction === 1 ? 'ascending' : 'descending') : 'none'); });
    const shown = all.filter(r => (filter === 'all' || (filter === 'unbooked' ? r.status !== 'booked' : !['booked', 'resolved'].includes(r.status))) && (type === 'all' || (type === 'logistics' ? r.type !== 'event' : r.type === type)) && (city === 'all' || r.city === city));
    shown.sort((a, b) => { const av = a[sort], bv = b[sort]; if (!av || !bv) return !av === !bv ? 0 : !av ? 1 : -1; return direction * String(av).localeCompare(String(bv)); });
    $('#counts').textContent = `${shown.length} of ${all.length} arrangements shown · ${all.filter(r => !['booked', 'resolved'].includes(r.status)).length} unresolved`;
    $('#list-state').textContent = filter === 'all' && type === 'all' && city === 'all' ? 'All arrangements visible' : `${all.length - shown.length} hidden by filters`;
    $('#rows').innerHTML = shown.map(r => `<tr class="${r.key === selected ? 'selected' : ''} ${['booked', 'resolved'].includes(r.status) ? 'completed' : ''}"><td><button class="item" data-key="${esc(r.key)}">${esc(r.title)}</button><div class="subline">${esc(r.nextAction || r.how)}</div></td><td>${labels[r.type]}</td><td>${esc(r.city)}</td><td>${fmt(r.date)}</td><td>${fmt(r.bookBy)}</td><td><select class="row-status" data-status-key="${esc(r.key)}" aria-label="Status for ${esc(r.title)}">${C.statuses.map(s => `<option value="${s}" ${s === r.status ? 'selected' : ''}>${labels[s]}</option>`).join('')}</select></td></tr>`).join('') || '<tr><td colspan="6" class="empty">No matching arrangements.</td></tr>';
    if (selected && !shown.some(r => r.key === selected)) selected = null;
    renderDetail(); renderCoverage();
    if (!$('#json-preview').hidden) $('#json-preview').textContent = JSON.stringify(project, null, 2);
  }
  function input(label, name, value, kind = 'text', attrs = '') { return `<label>${label}<input name="${name}" type="${kind}" value="${esc(value)}" ${attrs}></label>`; }
  function stayOptions(current, allowEmpty = true) { return (allowEmpty ? '<option value="">Unassigned</option>' : '') + project.trip.stays.map(s => `<option value="${esc(s.id)}" ${s.id === current ? 'selected' : ''}>${esc(s.name)} · ${fmt(s.arrive)}–${fmt(s.depart)}</option>`).join(''); }
  function renderDetail() {
    const r = newDraft || C.rows(project.trip).find(r => r.key === selected);
    $('#detail').hidden = !r; $('#layout').classList.toggle('closed', !r); if (!r) return;
    const isNew = newDraft && !newDraft.existing;
    const fields = [...new Set([...fieldSets[r.type], ...Object.keys(r.fields || {})])];
    const source = r.key?.split(':')[0];
    $('#detail').innerHTML = `<div class="detail-label"><h2>${isNew ? 'New arrangement' : esc(r.title)}</h2><button class="drawer-close" id="close-detail" aria-label="Close details">×</button></div><form id="edit">
      <label>Type<select name="type" ${isNew ? 'id="new-type"' : 'disabled'}>${C.types.map(t => `<option value="${t}" ${t === r.type ? 'selected' : ''}>${labels[t]}</option>`).join('')}</select></label>
      ${input('Title', 'title', r.title, 'text', 'required')}
      <label>Stay<select name="stayId">${stayOptions(r.stayId)}</select></label>
      <label>Status<select name="status">${C.statuses.map(s => `<option value="${s}" ${s === r.status ? 'selected' : ''}>${labels[s]}</option>`).join('')}</select></label>
      <div class="pair">${input(r.type === 'lodging' ? 'Check-in date' : 'Date', 'date', r.date, 'date', source === 'lodging' ? 'readonly' : '')}${input('Book by', 'bookBy', r.bookBy, 'date')}</div>
      ${r.endDate ? `<div class="quiet" style="margin-top:8px">Check-out ${fmt(r.endDate)} · stay dates come from the itinerary</div>` : ''}
      <div class="pair">${input(r.type === 'lodging' ? 'Check-in time' : r.type === 'transfer' ? 'Pickup time' : 'Start / departure', 'start', r.start, 'time')}${input(r.type === 'lodging' ? 'Check-out time' : 'End / arrival', 'end', r.end, 'time')}</div>
      ${isNew && r.type === 'flight' ? `<label>From stay<select name="from">${stayOptions(r.from, false)}</select></label><label>To stay<select name="to">${stayOptions(r.to, false)}</select></label>` : ''}
      <label class="checkbox"><input type="checkbox" name="critical" ${r.critical ? 'checked' : ''}>Trip-critical</label>
      ${input('Next action', 'nextAction', r.nextAction)}${input('Booking link', 'url', r.url, 'url', 'placeholder="https://…"')}${input('Confirmation', 'ref', r.ref)}
      <div class="pair">${input('Cost', 'amount', r.price?.amount ?? '', 'number', 'min="0" step="0.01"')}${input('Currency', 'currency', r.price?.currency || 'USD', 'text', 'pattern="[A-Z]{3}" maxlength="3"')}</div>
      <label>Cost unit<select name="unit">${[['total', 'Total'], ...(r.type === 'lodging' ? [['night', 'Per night']] : []), ['person', 'Per person']].map(([v, l]) => `<option value="${v}" ${(r.price?.unit || 'total') === v ? 'selected' : ''}>${l}</option>`).join('')}</select></label>
      <h3>${labels[r.type]} details</h3>${fields.map(k => input(esc(k), 'field:' + esc(k), r.fields?.[k] || '')).join('')}
      ${r.type === 'transfer' ? `<label>Linked flight<select name="flightId"><option value="">None</option>${(project.trip.transfers || []).filter(f => f.mode === 'flight').map(f => `<option value="${esc(f.id)}" ${r.flightId === f.id ? 'selected' : ''}>${esc(f.date)} · ${esc(project.trip.stays.find(s => s.id === f.from)?.name)} → ${esc(project.trip.stays.find(s => s.id === f.to)?.name)}</option>`).join('')}</select></label><label>Airport journey<select name="journey"><option value="">None</option><option value="departure" ${r.journey === 'departure' ? 'selected' : ''}>To departure airport</option><option value="arrival" ${r.journey === 'arrival' ? 'selected' : ''}>From arrival airport</option></select></label>` : ''}
      <label>Planning notes<textarea name="notes" rows="3">${esc(r.notes)}</textarea></label>
      ${r.sourceNotes || r.how ? `<details><summary>Imported notes & booking guidance</summary><div class="source-notes">${esc([r.sourceNotes, r.how].filter(Boolean).join('\n\n'))}</div></details>` : ''}
      <h3>Research options</h3><div id="options">${r.options.map((o, i) => `<div class="options-editor" data-option="${i}"><div class="option-head"><span class="quiet">Option ${i + 1}</span><button type="button" data-remove-option="${i}" aria-label="Remove option ${i + 1}">×</button></div>${input('Name', `option:${i}:name`, o.name, 'text', 'required')}${input('Link', `option:${i}:url`, o.url, 'url')}${input('Estimate', `option:${i}:estimate`, o.estimate)}<label>Notes<textarea name="option:${i}:notes" rows="2">${esc(o.notes)}</textarea></label></div>`).join('')}</div>
      <div class="actions"><button type="submit" class="primary">${isNew ? 'Create arrangement' : 'Apply changes'}</button><button type="button" id="add-option" aria-label="Add research option">+</button></div><p id="detail-error" role="alert"></p></form>`;
    $('#edit').oninput = () => { formDirty = true; };
    $('#edit').onchange = () => { formDirty = true; };
    $('#edit').onsubmit = e => { e.preventDefault(); applyForm(); };
    $('#close-detail').onclick = () => { if (!leave()) return; selected = null; newDraft = null; render(); };
    const refreshDraft = change => { const v = captureForm(); change(v); if (newDraft) newDraft = v; else { newDraft = {...v, key: r.key, existing: true}; selected = null; } formDirty = true; renderDetail(); };
    $('#add-option').onclick = () => refreshDraft(v => v.options.push({name: '', url: '', estimate: '', notes: ''}));
    $('#options').onclick = e => { const b = e.target.closest('[data-remove-option]'); if (b) refreshDraft(v => v.options.splice(Number(b.dataset.removeOption), 1)); };
    if ($('#new-type')) $('#new-type').onchange = e => refreshDraft(v => { v.type = e.target.value; });
  }
  function captureForm() {
    const base = newDraft || C.rows(project.trip).find(r => r.key === selected), data = new FormData($('#edit')), v = {...C.clone(base), fields: {}, options: []};
    for (const key of ['title', 'stayId', 'status', 'date', 'bookBy', 'start', 'end', 'nextAction', 'ref', 'url', 'notes', 'from', 'to', 'flightId', 'journey']) if (data.has(key)) v[key] = String(data.get(key)).trim();
    v.type = data.get('type') || base.type; v.critical = data.has('critical');
    const amount = data.get('amount');
    v.price = amount !== '' ? {...C.clone(base.price || {}), amount: Number(amount), currency: data.get('currency'), unit: data.get('unit')} : undefined;
    if (v.price && (base.price?.amount !== v.price.amount || base.price?.currency !== v.price.currency || (base.price?.unit || 'total') !== v.price.unit)) { delete v.price.max; delete v.price.note; }
    for (const [key, value] of data) {
      if (key.startsWith('field:') && value.trim()) v.fields[key.slice(6)] = value.trim();
      if (key.startsWith('option:')) { const [, i, field] = key.split(':'); (v.options[Number(i)] ||= {})[field] = value.trim(); }
    }
    return v;
  }
  function applyForm() {
    if (!$('#edit')) return true;
    if (!$('#edit').reportValidity()) return false;
    try {
      const value = captureForm(), trip = C.clone(project.trip);
      const key = newDraft && !newDraft.existing ? C.add(trip, value) : C.edit(trip, value.key, value);
      project.trip = trip; selected = key; newDraft = null; formDirty = false; changed();
      // A newly created item must not disappear behind the current filters.
      filter = 'all'; type = 'all'; city = 'all'; render(); message('Arrangement updated. Save JSON to write it to your project file.'); return true;
    } catch (e) { $('#detail-error').textContent = e.message; return false; }
  }
  function flushForm() { return !formDirty || applyForm(); }
  function renderCoverage() {
    const checks = C.coverage(project.trip), missing = checks.filter(x => !x.row).length;
    $('#coverage-title').textContent = `Airport journeys · ${checks.length - missing}/${checks.length} listed${missing ? ' · ' + missing + ' missing' : ''}`;
    $('#fill-gaps').hidden = !missing;
    $('#checks').innerHTML = checks.map(({flight: f, journey: j, row}) => `<div class="check"><strong>${esc(f.date)} · ${esc((j === 'departure' ? f.fromPlace?.name : f.toPlace?.name) || project.trip.stays.find(s => s.id === (j === 'departure' ? f.from : f.to))?.name || f.id)}</strong><span class="quiet">${j === 'departure' ? 'To airport' : 'From airport'} · ${row ? `<button data-coverage-key="${esc(row.key)}">${labels[row.status]}</button>` : 'Missing arrangement'}</span></div>`).join('') || '<span class="quiet">No flight records.</span>';
  }
  async function saveFile(value, name, reuse = false) {
    const text = JSON.stringify(value, null, 2) + '\n';
    if (window.showSaveFilePicker) {
      const target = reuse && handle ? handle : await window.showSaveFilePicker({suggestedName: name, types: [{description: 'JSON', accept: {'application/json': ['.json']}}]});
      const writable = await target.createWritable(); await writable.write(text); await writable.close();
      if (reuse) { handle = target; filename = target.name; } return true;
    }
    const url = URL.createObjectURL(new Blob([text], {type: 'application/json'})), a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 2000); return false;
  }
  async function saveProject() {
    if (!flushForm()) return;
    try {
      const disk = await saveFile(project, filename, true);
      if (disk) { dirty = false; $('#save-state').textContent = 'Saved to ' + filename; $('#filename').textContent = filename; message('Project file saved.'); }
      else { $('#save-state').textContent = 'Download requested · choose where to keep the JSON'; message('Reopen the downloaded project to confirm its contents. Browser drafts remain available.'); }
    } catch (e) { if (e.name !== 'AbortError') message('Save failed: ' + e.message, true); }
  }
  function importPhone(t) {
    const result = C.merge(project.lastExchange, project.trip, t);
    pendingPhone = t;
    $('#merge-summary').textContent = result.conflicts.length ? `${result.conflicts.length} conflicting field(s). Choose which value to keep; desktop is selected by default.` : 'No conflicting edits. Phone changes will be combined with your desktop work.';
    $('#merge-body').innerHTML = result.conflicts.map((c, i) => `<div class="conflict"><strong>${esc(c.path)}</strong><div class="pair"><div><div class="quiet">Desktop</div><pre>${esc(c.local === undefined ? '(removed)' : JSON.stringify(c.local, null, 2))}</pre></div><div><div class="quiet">Phone</div><pre>${esc(c.phone === undefined ? '(removed)' : JSON.stringify(c.phone, null, 2))}</pre></div></div><select data-conflict="${esc(c.path)}" aria-label="Conflict ${i + 1}"><option value="local">Keep desktop</option><option value="phone">Use phone</option></select></div>`).join('');
    $('#merge-dialog').showModal();
  }
  $('#rows').onclick = e => { const b = e.target.closest('[data-key]'); if (b && leave()) { selected = b.dataset.key; newDraft = null; render(); } };
  $('#rows').onchange = e => {
    const control = e.target.closest('[data-status-key]'); if (!control) return;
    const key = control.dataset.statusKey, status = control.value;
    if (!flushForm()) { control.value = C.rows(project.trip).find(r => r.key === key)?.status || 'research'; return; }
    const row = C.rows(project.trip).find(r => r.key === key);
    if (row && status === 'booked' && !row.date) {
      selected = key; newDraft = null; render();
      $('#edit [name=status]').value = status; formDirty = true; $('#edit [name=date]').focus();
      message('Choose a date, then Apply changes to record this booking.'); return;
    }
    try {
      const trip = C.clone(project.trip); C.setStatus(trip, key, status); project.trip = trip;
      changed(); render(); message('Status updated. Save JSON to keep the change in your project file.');
    } catch (error) { render(); message(error.message, true); }
  };
  $('#filters').onclick = e => { const b = e.target.closest('[data-filter]'); if (b && leave()) { filter = b.dataset.filter; newDraft = null; render(); } };
  $('#type').onchange = e => { if (leave()) { type = e.target.value; newDraft = null; render(); } else e.target.value = type; };
  $('#city').onchange = e => { if (leave()) { city = e.target.value; newDraft = null; render(); } else e.target.value = city; };
  $('thead').onclick = e => { const b = e.target.closest('[data-sort]'); if (!b || !leave()) return; direction = sort === b.dataset.sort ? -direction : 1; sort = b.dataset.sort; render(); };
  $('#add').onclick = () => { if (!leave()) return; selected = null; newDraft = {type: 'transfer', title: '', date: project.trip.startDate, bookBy: '', stayId: '', status: 'research', critical: true, nextAction: '', ref: '', url: '', notes: '', fields: {}, options: []}; render(); $('#detail').scrollIntoView({block: 'nearest'}); };
  $('#checks').onclick = e => { const b = e.target.closest('[data-coverage-key]'); if (b && leave()) { selected = b.dataset.coverageKey; newDraft = null; filter = type = city = 'all'; render(); $('#detail').scrollIntoView({block: 'nearest'}); } };
  $('#fill-gaps').onclick = () => { if (!flushForm()) return; const n = C.addAirportJourneys(project.trip); changed(); render(); message(`${n} airport journey row(s) added or linked. Research and reservation status have not been inferred.`); };
  $('#save').onclick = saveProject;
  $('#open').onclick = () => { if (!leave() || (dirty && !confirm('Open another file? Save JSON first if you need to keep this workspace.'))) return; $('#file').click(); };
  $('#file').onchange = async e => {
    const f = e.target.files[0]; if (!f) return;
    try { const next = C.openProject(JSON.parse(await f.text())); project = next; filename = f.name.endsWith('.planner.json') ? f.name : project.trip.id + '.planner.json'; handle = null; dirty = formDirty = false; newDraft = selected = null; filter = type = city = 'all'; $('#restore').hidden = true; $('#save-state').textContent = 'Loaded from ' + f.name; render(); message('File opened.'); checkRecovery(); }
    catch (err) { message('Could not open file: ' + err.message, true); }
    finally { e.target.value = ''; }
  };
  $('#export').onclick = async () => {
    if (!flushForm()) return;
    try {
      const trip = C.exportTrip(project), disk = await saveFile(trip, trip.id + '.trip.json');
      project.trip = trip; project.lastExchange = C.clone(trip); changed(); render();
      message((disk ? 'Trip revision written.' : 'Trip revision download requested.') + ' Import it in Traveler, then Save JSON here to retain the exchange baseline.');
    } catch (e) { if (e.name !== 'AbortError') message('Export failed: ' + e.message, true); }
  };
  $('#phone').onclick = () => { if (flushForm()) $('#phone-file').click(); };
  $('#phone-file').onchange = async e => { const f = e.target.files[0]; if (!f) return; try { importPhone(JSON.parse(await f.text())); } catch (err) { message('Import failed: ' + err.message, true); } finally { e.target.value = ''; } };
  $('#merge-cancel').onclick = () => { pendingPhone = null; $('#merge-dialog').close(); };
  $('#merge-apply').onclick = () => {
    try {
      const choices = Object.fromEntries([...document.querySelectorAll('[data-conflict]')].map(s => [s.dataset.conflict, s.value]));
      const result = C.merge(project.lastExchange, project.trip, pendingPhone, choices); C.validateTrip(result.trip);
      project.trip = result.trip; project.lastExchange = C.clone(pendingPhone); pendingPhone = null; selected = newDraft = null; formDirty = false; changed(); $('#merge-dialog').close(); render(); message('Phone edits imported. Save JSON to keep the merged project.');
    } catch (e) { message('Import failed: ' + e.message, true); }
  };
  $('#inspect').onclick = () => { $('#json-preview').hidden = !$('#json-preview').hidden; $('#inspect').textContent = $('#json-preview').hidden ? 'Inspect JSON' : 'Hide JSON'; if (flushForm()) render(); };
  document.querySelectorAll('.bar a').forEach(a => a.onclick = () => { const el = document.querySelector(a.getAttribute('href')); if (el?.tagName === 'DETAILS') el.open = true; });
  function checkRecovery() {
    try { const raw = localStorage.getItem(storageKey()); if (raw && JSON.stringify(C.openProject(JSON.parse(raw))) !== JSON.stringify(project)) $('#restore').hidden = false; } catch { /* File saving remains available when browser storage is disabled. */ }
  }
  $('#restore').onclick = () => {
    if (!leave() || (dirty && !confirm('Replace this workspace with the browser recovery draft?'))) return;
    try { project = C.openProject(JSON.parse(localStorage.getItem(storageKey()))); selected = newDraft = null; $('#restore').hidden = true; changed(); render(); message('Browser draft restored. Save JSON to write it to a file.'); } catch (e) { message('Recovery failed: ' + e.message, true); }
  };
  window.addEventListener('beforeunload', e => { if (dirty || formDirty) { e.preventDefault(); e.returnValue = ''; } });
  $('#save-state').textContent = 'Project loaded · save changes to your local JSON file';
  render(); checkRecovery();
})();
