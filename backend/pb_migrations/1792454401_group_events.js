// Add group sharing without changing existing private deadline records.
// Group owners are permanent implicit admins; membership rows grant additional access.
migrate((app) => {
  app.save(new Collection({
  "id": "tokigroups000001",
  "name": "groups",
  "type": "base",
  "fields": [
    {
      "type": "text",
      "name": "name",
      "required": true,
      "max": 120
    },
    {
      "type": "relation",
      "name": "owner",
      "collectionId": "_pb_users_auth_",
      "required": true,
      "maxSelect": 1,
      "cascadeDelete": true
    }
  ],
  "indexes": []
}));
  app.save(new Collection({
  "id": "tokimembers0001",
  "name": "group_members",
  "type": "base",
  "fields": [
    {
      "type": "relation",
      "name": "group",
      "collectionId": "tokigroups000001",
      "required": true,
      "maxSelect": 1,
      "cascadeDelete": true
    },
    {
      "type": "relation",
      "name": "user",
      "collectionId": "_pb_users_auth_",
      "required": true,
      "maxSelect": 1,
      "cascadeDelete": true
    },
    {
      "type": "select",
      "name": "role",
      "required": true,
      "maxSelect": 1,
      "values": [
        "admin",
        "member"
      ]
    }
  ],
  "indexes": [
    "CREATE UNIQUE INDEX idx_group_members_group_user ON group_members (`group`, `user`)"
  ]
}));
  app.save(new Collection({
  "id": "tokiprefs0000001",
  "name": "deadline_preferences",
  "type": "base",
  "fields": [
    {
      "type": "relation",
      "name": "user",
      "collectionId": "_pb_users_auth_",
      "required": true,
      "maxSelect": 1,
      "cascadeDelete": true
    },
    {
      "type": "relation",
      "name": "deadline",
      "collectionId": "pbc_740425175",
      "required": true,
      "maxSelect": 1,
      "cascadeDelete": true
    },
    {
      "type": "number",
      "name": "reminder_minutes",
      "min": 0,
      "max": 525600,
      "onlyInt": true
    },
    {
      "type": "bool",
      "name": "enabled"
    }
  ],
  "indexes": [
    "CREATE UNIQUE INDEX idx_deadline_preferences_user_deadline ON deadline_preferences (`user`, `deadline`)"
  ]
}));
  const deadlines = app.findCollectionByNameOrId("deadlines");
  deadlines.fields.add(new RelationField({"type": "relation", "name": "group", "collectionId": "tokigroups000001", "required": false, "maxSelect": 1, "cascadeDelete": true}));
  app.save(deadlines);
  {
    const collection = app.findCollectionByNameOrId("groups");
    collection.listRule = "@request.auth.id != \"\" && (owner = @request.auth.id || (@collection.group_members:access.group ?= id && @collection.group_members:access.user ?= @request.auth.id))";
    collection.viewRule = "@request.auth.id != \"\" && (owner = @request.auth.id || (@collection.group_members:access.group ?= id && @collection.group_members:access.user ?= @request.auth.id))";
    collection.createRule = "@request.auth.id != \"\" && @request.body.owner = @request.auth.id";
    collection.updateRule = "@request.auth.id != \"\" && (owner = @request.auth.id || ((@collection.group_members:admin.group ?= id && @collection.group_members:admin.user ?= @request.auth.id) && @collection.group_members:admin.role ?= \"admin\")) && @request.body.owner:changed = false";
    collection.deleteRule = "@request.auth.id != \"\" && owner = @request.auth.id";
    app.save(collection);
  }
  {
    const collection = app.findCollectionByNameOrId("group_members");
    collection.listRule = "@request.auth.id != \"\" && (group.owner = @request.auth.id || (@collection.group_members:access.group ?= group && @collection.group_members:access.user ?= @request.auth.id))";
    collection.viewRule = "@request.auth.id != \"\" && (group.owner = @request.auth.id || (@collection.group_members:access.group ?= group && @collection.group_members:access.user ?= @request.auth.id))";
    collection.createRule = "@request.auth.id != \"\" && ((@collection.groups:target.id ?= @request.body.group && @collection.groups:target.owner ?= @request.auth.id) || ((@collection.group_members:writer.group ?= @request.body.group && @collection.group_members:writer.user ?= @request.auth.id) && @collection.group_members:writer.role ?= \"admin\"))";
    collection.updateRule = "@request.auth.id != \"\" && (group.owner = @request.auth.id || ((@collection.group_members:admin.group ?= group && @collection.group_members:admin.user ?= @request.auth.id) && @collection.group_members:admin.role ?= \"admin\")) && @request.body.group:changed = false && @request.body.user:changed = false";
    collection.deleteRule = "@request.auth.id != \"\" && (group.owner = @request.auth.id || ((@collection.group_members:admin.group ?= group && @collection.group_members:admin.user ?= @request.auth.id) && @collection.group_members:admin.role ?= \"admin\"))";
    app.save(collection);
  }
  {
    const collection = app.findCollectionByNameOrId("deadline_preferences");
    collection.listRule = "@request.auth.id != \"\" && user = @request.auth.id && ((deadline.group = \"\" && deadline.owner = @request.auth.id) || (deadline.group != \"\" && (deadline.group.owner = @request.auth.id || (@collection.group_members:access.group ?= deadline.group && @collection.group_members:access.user ?= @request.auth.id))))";
    collection.viewRule = "@request.auth.id != \"\" && user = @request.auth.id && ((deadline.group = \"\" && deadline.owner = @request.auth.id) || (deadline.group != \"\" && (deadline.group.owner = @request.auth.id || (@collection.group_members:access.group ?= deadline.group && @collection.group_members:access.user ?= @request.auth.id))))";
    collection.createRule = "@request.auth.id != \"\" && user = @request.auth.id && ((deadline.group = \"\" && deadline.owner = @request.auth.id) || (deadline.group != \"\" && (deadline.group.owner = @request.auth.id || (@collection.group_members:access.group ?= deadline.group && @collection.group_members:access.user ?= @request.auth.id))))";
    collection.updateRule = "@request.auth.id != \"\" && user = @request.auth.id && ((deadline.group = \"\" && deadline.owner = @request.auth.id) || (deadline.group != \"\" && (deadline.group.owner = @request.auth.id || (@collection.group_members:access.group ?= deadline.group && @collection.group_members:access.user ?= @request.auth.id)))) && @request.body.user:changed = false && @request.body.deadline:changed = false";
    collection.deleteRule = "@request.auth.id != \"\" && user = @request.auth.id";
    app.save(collection);
  }
  {
    const collection = app.findCollectionByNameOrId("deadlines");
    collection.listRule = "@request.auth.id != \"\" && ((group = \"\" && owner = @request.auth.id) || (group != \"\" && (group.owner = @request.auth.id || (@collection.group_members:access.group ?= group && @collection.group_members:access.user ?= @request.auth.id))))";
    collection.viewRule = "@request.auth.id != \"\" && ((group = \"\" && owner = @request.auth.id) || (group != \"\" && (group.owner = @request.auth.id || (@collection.group_members:access.group ?= group && @collection.group_members:access.user ?= @request.auth.id))))";
    collection.createRule = "@request.auth.id != \"\" && @request.body.owner = @request.auth.id && (@request.body.group:isset = false || @request.body.group = \"\" || ((@collection.groups:target.id ?= @request.body.group && @collection.groups:target.owner ?= @request.auth.id) || ((@collection.group_members:writer.group ?= @request.body.group && @collection.group_members:writer.user ?= @request.auth.id) && @collection.group_members:writer.role ?= \"admin\")))";
    collection.updateRule = "@request.auth.id != \"\" && ((group = \"\" && owner = @request.auth.id) || (group != \"\" && (group.owner = @request.auth.id || ((@collection.group_members:admin.group ?= group && @collection.group_members:admin.user ?= @request.auth.id) && @collection.group_members:admin.role ?= \"admin\")))) && @request.body.owner:changed = false && @request.body.group:changed = false";
    collection.deleteRule = "@request.auth.id != \"\" && ((group = \"\" && owner = @request.auth.id) || (group != \"\" && (group.owner = @request.auth.id || ((@collection.group_members:admin.group ?= group && @collection.group_members:admin.user ?= @request.auth.id) && @collection.group_members:admin.role ?= \"admin\"))))";
    app.save(collection);
  }
});
