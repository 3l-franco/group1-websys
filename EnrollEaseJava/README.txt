EnrollEase Java Web App
=======================

This project preserves the existing HTML/CSS/JavaScript frontend and Java backend.

Run locally in Eclipse:
1. Import this folder as an Existing Maven Project (Eclipse m2e required).
2. Allow Maven to resolve dependencies from pom.xml.
3. Run src/enrollease/web/WebApp.java, or run the packaged app with Maven.
4. Open http://localhost:8080.

Run with Maven:
  mvn package
  java -jar target/enrollease-1.0.0.jar

Database modes:
- Local: when DATABASE_URL is not set, uses SQLite at enrollease.db.
- Hosted: when DATABASE_URL is set, uses PostgreSQL. Set DATABASE_USER and
  DATABASE_PASSWORD as environment variables. The app creates needed tables
  automatically, including persistent enrollment slips.

Demo student: 2026-00123 / demo123
Demo admin (local only, no external database): admin / admin
For an online PostgreSQL deployment, set ADMIN_USERNAME and a unique
ADMIN_PASSWORD in the host's environment settings. The local default is disabled
in hosted mode when ADMIN_PASSWORD has not been configured.

This is an academic demonstration. Use only fictional student data. Password
hashing and additional production protections are outside the current demo scope.
