// Preferences use stable occurrence keys so imports may be deleted/recreated safely.
migrate((app) => {
  const own = '@request.auth.id != "" && owner = @request.auth.id';
  app.save(new Collection({
    id: 'tokicalprefs0001', name: 'calendar_preferences', type: 'base',
    listRule: own, viewRule: own,
    createRule: own + ' && (@collection.calendar_events:target.owner ?= @request.auth.id && @collection.calendar_events:target.external_key ?= @request.body.external_key)',
    updateRule: own + ' && @request.body.owner:changed = false && @request.body.external_key:changed = false',
    deleteRule: own,
    fields: [
      {name: 'owner', type: 'relation', collectionId: '_pb_users_auth_', required: true, maxSelect: 1, cascadeDelete: true},
      {name: 'external_key', type: 'text', required: true, min: 64, max: 64, pattern: '^[a-f0-9]{64}$'},
      {name: 'hidden', type: 'bool'}
    ],
    indexes: ['CREATE UNIQUE INDEX idx_calendar_preferences_owner_key ON calendar_preferences (owner, external_key)']
  }));
}, (app) => {
  throw new Error('Calendar preference rollback requires restoring a verified backup.');
});
