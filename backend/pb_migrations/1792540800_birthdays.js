// Private date-only birthdays. Existing deadlines, records and rules are untouched.
migrate((app) => {
  const own = '@request.auth.id != "" && owner = @request.auth.id';
  const validDate = '(month != 2 || day <= 29) && ((month != 4 && month != 6 && month != 9 && month != 11) || day <= 30)';
  const value = (field, operator, number) => '((@request.body.' + field + ':isset = false && ' + field + ' ' + operator + ' ' + number + ') || (@request.body.' + field + ':isset = true && @request.body.' + field + ' ' + operator + ' ' + number + '))';
  const validUpdate = '(' + value('month', '!=', 2) + ' || ' + value('day', '<=', 29) + ') && ((' + [4, 6, 9, 11].map(m => value('month', '!=', m)).join(' && ') + ') || ' + value('day', '<=', 30) + ')';
  app.save(new Collection({
    id: 'tokibirthday001', name: 'birthdays', type: 'base',
    listRule: own, viewRule: own,
    createRule: own + ' && ' + validDate,
    updateRule: own + ' && @request.body.owner:changed = false && ' + validUpdate,
    deleteRule: own,
    fields: [
      {name: 'owner', type: 'relation', collectionId: '_pb_users_auth_', required: true, maxSelect: 1, cascadeDelete: true},
      {name: 'name', type: 'text', required: true, max: 120},
      {name: 'description', type: 'text', max: 5000},
      {name: 'month', type: 'number', required: true, onlyInt: true, min: 1, max: 12},
      {name: 'day', type: 'number', required: true, onlyInt: true, min: 1, max: 31},
      {name: 'birth_year', type: 'number', onlyInt: true, min: 0, max: 9999},
      {name: 'reminder_hour', type: 'number', onlyInt: true, min: 0, max: 23},
      {name: 'reminder_minute', type: 'number', onlyInt: true, min: 0, max: 59},
      {name: 'leap_day_policy', type: 'select', required: true, maxSelect: 1, values: ['feb28', 'mar1']},
      {name: 'enabled', type: 'bool'},
      {name: 'created', type: 'autodate', onCreate: true, onUpdate: false},
      {name: 'updated', type: 'autodate', onCreate: true, onUpdate: true}
    ]
  }));
}, (app) => {
  throw new Error('Birthday rollback is intentionally manual: restore a verified backup instead of dropping user data.');
});
