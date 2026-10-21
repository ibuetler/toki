# Lightweight tasks

Tasks capture short-lived thoughts without replacing JIRA. Android’s global search includes task titles and notes, including completed tasks, alongside events and birthdays. Each task belongs to one user; there are no assignments, workflows, subtasks, or shared tasks.

## Schema

The additive `1792627200_tasks.js` migration creates `tasks`:

| Field | Meaning |
| --- | --- |
| owner | Required user relation, immutable; deleting the owner cascades tasks |
| title | Required, up to 200 characters |
| notes | Optional, up to 5000 characters |
| priority | Required select: urgent, high, normal, low; Android defaults to normal |
| completed | Boolean, false for new tasks |
| remind_at | Optional UTC instant; empty means no reminder |
| created, updated | Server timestamps |

List/view/create/update/delete rules require authentication and ownership. Updates cannot transfer ownership. The safe schema export contains fields and API rules, never task records or account credentials.

## Android behaviour

Open Agenda → Tasks → + New task. Only a title is necessary; priority defaults to Normal. Active tasks sort Urgent → High → Normal → Low, newest first within each priority. Notes and reminders are optional. Reminders use the existing audible alarm and permission system; priority alone never schedules anything. Reminder times are one-off instants displayed in the phone's time zone.

Tasks remain active after their reminder. Checking a task moves it to Completed and cancels its pending alarm on this phone. Reopening restores an alarm only if its reminder is still in the future. A past reminder never repeats. Completed tasks are retained until manually deleted. Deletion requires confirmation. Editing preserves completion status. Task reminders participate in Next alarm; tasks stay separate from Next event and birthday summaries.

Viewing cached tasks works offline. Writes need internet; failed writes retain form text. The server response updates the local cache and rebuilds reminders after each successful mutation. Other devices need to sync to receive changes. Sign-out clears tasks alongside events and alarms. Room version 4 adds tasks without dropping event or birthday data. If the collection is missing, the rest of the app continues to work and asks you to sync after backend setup. Other task API failures abort sync so an error cannot silently replace cached tasks with an empty list.

## Deployment from macOS

```bash
./backend/scripts/apply-jupiter-tasks.sh --host root@jupiter
./backend/scripts/apply-jupiter-tasks.sh --host root@jupiter --apply
```

The first command rehearses only. The second repeats rehearsal, stops PocketBase, creates and verifies a backup, installs the migration with the data directory's mapped ownership, restarts the existing container, checks health, and verifies existing records/settings/rules were preserved. It reuses the reviewed additive-migration worker used for birthdays. Rollback is manual restoration of the verified backup; never drop a collection containing tasks. Once installed, do not rerun the deployment script; it deliberately refuses an existing migration.

Deployed on Jupiter on 2026-10-21. Retained backup:
`/opt/applic/pocketbase/backups-toki-tasks-20261021T100926Z-ixi77oeb`

## Isolated tests

Run `backend/pocketbase-tests/task_tests.py --container toki-tasks-validation --url http://127.0.0.1:8097` against a disposable PocketBase 0.40.5 container with all repository migrations applied. It refuses remote URLs and container names without `toki-`. Never point it at production data. `--update-schema` exports only the tasks schema and API rules into the repository snapshot.

The suite checks all priorities, text limits, owner-only CRUD, blocked ownership transfer, anonymous/other-user isolation, reminder set/clear, completion/reopening and filtering, deletion, and owner cascade deletion. Temporary users/tasks are cleaned up; remove the disposable container afterward. Android tests separately cover quick capture, edit preservation, priority ordering, completion callbacks, cache migration, API payloads, and one-off alarm planning.
