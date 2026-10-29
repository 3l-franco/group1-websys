# EnrollEase

Academic web systems project. The application uses a Java HTTP backend with HTML, CSS, and JavaScript pages.

## Deployment branch status

The deployment changes have been prepared in a separate downloadable source bundle, but the application source has **not yet been committed to this branch** and no live deployment exists yet.

Before deploying, extract the contents of the provided `EnrollEaseJava_Deployment.zip` so this repository root contains `Dockerfile`, `pom.xml`, `src/`, and `web/`. Preserve a copy of the approved original before replacing or moving files.

## Intended online architecture

- GitHub stores project source.
- Render runs the Java application using Docker.
- Neon hosts PostgreSQL for accounts, subjects, and submitted enrollment slips.

Configure database credentials and the online administrator password through Render's environment settings. Never commit secrets, local database files, or real student data. Use fictional demonstration records only.
