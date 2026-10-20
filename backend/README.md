# PocketBase backend

The configured remote endpoint is `https://pocketbase.hacking-lab.com`. The regression suite verifies normal user access; it does not export server configuration or authenticate as a superuser.

## Deployment capture: pending

The discussion describes a Docker deployment on Jupiter under `/opt/applic/pocketbase`, behind Traefik on its external `transit` network, with HTTPS and authenticated SMTP. The exact running version, image build, collection schema, and deployment files have not yet been captured in this repository. Do not treat this document as a deployable configuration.

Copy the following non-secret artifacts from that deployment into this directory:

| Artifact | Target path | What to capture |
| --- | --- | --- |
| Docker configuration | `deployment/compose.yaml`, `deployment/Dockerfile` | Actual service, build, volumes, network, Traefik labels, and runtime user settings; replace secrets with environment references |
| Environment template | `deployment/.env.example` | Required variable names and safe examples only |
| Version information | `deployment/VERSION.md` | Running PocketBase version, image tag/digest, target architecture, and build arguments |
| Collection export | `schema/collections.json` | Export from the deployed admin UI, including field constraints, indexes, relations, auth configuration, and API rules; inspect for secrets before committing |
| Existing migrations/hooks | `pb_migrations/`, `pb_hooks/` | Existing server sources if present; review for embedded secrets |

Keep `pb_data`, backups, account records, real environment files, SMTP credentials, and tokens out of Git. Collection exports and migrations must also be inspected for embedded credentials or seed records.

Once the files are available:

1. Pin the build to the deployed version rather than assuming the newest release is compatible.
2. Convert or preserve the schema as migrations appropriate to that version.
3. Start a separate instance with an empty data directory and apply the migrations.
4. Provision two disposable users locally, configure the regression suite for that instance, and run `testing.py -all`.
5. Document the verified setup, backup/restore procedure, and upgrade process. Do not import a schema into the running server just to test reproducibility.

## Access contract

The intended `deadlines` behavior is:

- Authenticated owners can list, view, update, and delete their records.
- Creation requires the submitted owner to match the authenticated user.
- Updating a record cannot change its owner.
- Cross-user requests and anonymous clients cannot access deadline records.

The deployed rule expressions still need to be exported. Passing API tests confirms tested behavior, not the complete rule definitions. PocketBase can return HTTP 200 with an empty list when a list rule filters out all records; anonymous list tests therefore check that the known record is absent rather than requiring an error status.

References: [PocketBase API rules](https://pocketbase.io/docs/api-rules-and-filters/) and [deployment guidance](https://pocketbase.io/docs/going-to-production/).
