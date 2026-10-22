// Private, read-only imported calendar occurrences. Only the server importer writes.
migrate((app) => {
  const own = '@request.auth.id != "" && owner = @request.auth.id';
  app.save(new Collection({
    id: 'tokicalendar0001', name: 'calendar_events', type: 'base',
    listRule: own, viewRule: own, createRule: null, updateRule: null, deleteRule: null,
    fields: [
      {name: 'owner', type: 'relation', collectionId: '_pb_users_auth_', required: true, maxSelect: 1, cascadeDelete: true},
      {name: 'external_key', type: 'text', required: true, max: 64},
      {name: 'title', type: 'text', required: true, max: 500},
      {name: 'description', type: 'text', max: 20000},
      {name: 'location', type: 'text', max: 2000},
      {name: 'source_names', type: 'text', required: true, max: 1000},
      {name: 'start_at', type: 'date', required: true},
      {name: 'end_at', type: 'date', required: true},
      {name: 'timezone', type: 'text', required: true, max: 100},
      {name: 'all_day', type: 'bool'},
      {name: 'enabled', type: 'bool'},
      {name: 'reminder_minutes', type: 'number', min: 5, max: 5, onlyInt: true}
    ],
    indexes: ['CREATE UNIQUE INDEX idx_calendar_owner_key ON calendar_events (owner, external_key)']
  }));
}, (app) => {
  throw new Error('Calendar rollback is manual: restore a verified backup instead of dropping imported data.');
});
