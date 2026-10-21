// Lightweight, private capture tasks. Priority does not imply an alarm.
migrate((app) => {
  const own = '@request.auth.id != "" && owner = @request.auth.id';
  app.save(new Collection({
    id: 'tokitasks0000001', name: 'tasks', type: 'base',
    listRule: own, viewRule: own, createRule: own,
    updateRule: own + ' && @request.body.owner:changed = false', deleteRule: own,
    fields: [
      {name: 'owner', type: 'relation', collectionId: '_pb_users_auth_', required: true, maxSelect: 1, cascadeDelete: true},
      {name: 'title', type: 'text', required: true, max: 200},
      {name: 'notes', type: 'text', max: 5000},
      {name: 'priority', type: 'select', required: true, maxSelect: 1, values: ['urgent', 'high', 'normal', 'low']},
      {name: 'completed', type: 'bool'},
      {name: 'remind_at', type: 'date'},
      {name: 'created', type: 'autodate', onCreate: true, onUpdate: false},
      {name: 'updated', type: 'autodate', onCreate: true, onUpdate: true}
    ]
  }));
}, (app) => {
  throw new Error('Task rollback is manual: restore a verified backup instead of dropping user data.');
});
