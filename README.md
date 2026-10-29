# EnrollEase

Academic web systems project. The application uses a Java HTTP backend with HTML, CSS, and JavaScript pages.

## Deployment status

The source files and minimal deployment configuration have been prepared in the deployment bundle, but the application source has **not yet been pushed to this repository** and no live deployment exists yet.

Before connecting Render, extract the contents of the provided `EnrollEaseJava_Deployment.zip` so the repository root contains `Dockerfile`, `pom.xml`, `src/`, and `web/`. Use a separate branch for these deployment-related changes and keep the approved baseline recoverable.

## Online architecture

- GitHub: version control and source files
- Render: runs the Java web application from the Dockerfile
- Neon: hosts PostgreSQL data, including accounts, subjects, and submitted enrollment slips

Set database credentials and the online administrator password in Render's environment settings. Do not commit secrets, local database files, or real student data.

Use fictional demonstration accounts only. This is an academic demo, not a production enrollment system.
