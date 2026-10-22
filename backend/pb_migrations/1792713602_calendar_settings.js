// Private, user-managed calendar connections and wildcard exclusion rules.
migrate((app) => {
  const own = '@request.auth.id != "" && owner = @request.auth.id';
  app.save(new Collection({
    id: 'tokicalsources1', name: 'calendar_sources', type: 'base',
    listRule: own, viewRule: own,
    createRule: own + ' && @request.body.last_success:isset = false && @request.body.last_error:isset = false',
    updateRule: own + ' && @request.body.owner:changed = false && @request.body.last_success:changed = false && @request.body.last_error:changed = false', deleteRule: own,
    fields: [
      {name:'owner',type:'relation',collectionId:'_pb_users_auth_',required:true,maxSelect:1,cascadeDelete:true},
      {name:'name',type:'text',required:true,max:120,pattern:'^[^·]+$'},
      {name:'url',type:'text',required:true,max:4096,pattern:'^https://'},
      {name:'enabled',type:'bool'},
      {name:'last_success',type:'date'},
      {name:'last_error',type:'text',max:500}
    ], indexes:['CREATE UNIQUE INDEX idx_calendar_source_owner_name ON calendar_sources (owner, name)']
  }));
  app.save(new Collection({
    id:'tokicalrules001',name:'calendar_exclusions',type:'base',
    listRule:own,viewRule:own,
    createRule:own + ' && (@request.body.source:isset = false || @request.body.source = "" || (@collection.calendar_sources:scope.id ?= @request.body.source && @collection.calendar_sources:scope.owner ?= @request.auth.id))',
    updateRule:own + ' && @request.body.owner:changed = false && (@request.body.source:isset = false || @request.body.source = "" || (@collection.calendar_sources:scope.id ?= @request.body.source && @collection.calendar_sources:scope.owner ?= @request.auth.id))',deleteRule:own,
    fields:[
      {name:'owner',type:'relation',collectionId:'_pb_users_auth_',required:true,maxSelect:1,cascadeDelete:true},
      {name:'pattern',type:'text',required:true,max:200},
      {name:'match_type',type:'select',required:true,maxSelect:1,values:['wildcard','exact']},
      {name:'source',type:'relation',collectionId:'tokicalsources1',maxSelect:1,cascadeDelete:true},
      {name:'enabled',type:'bool'}
    ]
  }));
}, (app) => { throw new Error('Restore a verified backup to roll back private calendar settings.'); });
