# EnrollEase

Academic web systems project using a Java HTTP backend with HTML, CSS, and JavaScript pages.

## Deployment branch status

The application source and deployment files have been pushed to the `deployment/neon-render` branch. The application has **not yet been deployed to Render**, and the live Neon connection still needs to be configured and tested.

The Java application is in `EnrollEaseJava/`. Its Dockerfile and Maven configuration are inside that directory, so configure Render's **Root Directory** as `EnrollEaseJava`, runtime as **Docker**, and branch as `deployment/neon-render`.

## Intended online architecture

- GitHub stores the project source.
- Render runs the Java application using Docker.
- Neon hosts PostgreSQL for accounts, subjects, and submitted enrollment slips.

Set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `ADMIN_USERNAME`, and `ADMIN_PASSWORD` through Render's environment settings. Do not commit actual credentials, local database files, or real student data.

Use only fictional demonstration records. This is an academic demo, not a production enrollment system. Demo account passwords in the seeded records are intentionally for demonstration only and must never be reused.
