# Hospital Appointment Booking System

A full-stack web application where **patients** book doctor appointments online, **doctors** publish their availability, and an **admin** approves doctors and watches the numbers.

The main engineering problem it solves: **two patients must never be able to book the same slot**, even when they click at the same millisecond.

**Stack:** Java 17+ (tested on 21), Spring Boot 3, Spring Security + JWT, Spring Data JPA / Hibernate, MySQL, Maven, Swagger (springdoc), plain HTML + CSS + JavaScript frontend, JUnit 5 + Mockito + H2 for tests, Docker.

## Features

| Role | What they can do |
|---|---|
| Patient | Register, search doctors (with pagination), see free slots by date, book, cancel, reschedule, view their appointments |
| Doctor | Register (waits for admin approval), create slots in bulk (e.g. 09:00-13:00 in 30-minute slots), delete free slots, see their appointments, mark them completed |
| Admin | Approve doctors, see dashboard numbers (users, doctors, appointments by status) |

Also: BCrypt password hashing, stateless JWT login, role-based access, validation, one consistent JSON error format, email notifications on booking / cancel / reschedule.

## Run locally

1. Install Java 17 or newer, Maven and MySQL (make sure MySQL is running).
2. Set your MySQL password, either in `src/main/resources/application.properties` or with the environment variables `DB_USERNAME` and `DB_PASSWORD`. The database `hospital_db` is created automatically.
   *Do not commit a real password to GitHub. Prefer the environment variables.*
3. Start the app from the project root:
   ```bash
   mvn spring-boot:run
   ```
4. Open the app: http://localhost:8080
   API documentation (Swagger): http://localhost:8080/swagger-ui.html

A default admin is created on first start: `admin@hospital.com` / `Admin@123`. Change this before any public deployment.

### Try the whole flow (5 minutes)

1. **Register a doctor** (Register tab, role Doctor, e.g. Cardiology).
2. **Log in as admin** and approve the doctor.
3. **Log in as the doctor** and create slots for tomorrow (09:00 to 13:00, 30 minutes).
4. **Register a patient**, log in, search "Cardiology", click *View slots*, pick a time and book.
5. As the patient, try *Reschedule* and *Cancel*. As the doctor, click *Mark completed*.
6. Watch the console: the confirmation emails are printed in the log while `app.mail.enabled=false`.

## API

Send `Authorization: Bearer <token>` on protected endpoints (the token comes from `/api/auth/login`).

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register a patient or doctor |
| POST | `/api/auth/login` | public | Log in, returns the JWT |
| GET | `/api/doctors?specialization=Cardiology&page=0&size=10` | public | Search approved doctors (paged) |
| GET | `/api/doctors/{doctorId}/slots?date=2026-11-20` | public | Free slots of a doctor on a date |
| POST | `/api/patient/appointments` | PATIENT | Book a slot `{ "slotId": 1, "reason": "..." }` |
| GET | `/api/patient/appointments/my` | PATIENT | My appointments |
| PUT | `/api/patient/appointments/{id}/cancel` | PATIENT | Cancel (frees the slot) |
| PUT | `/api/patient/appointments/{id}/reschedule` | PATIENT | Move to another slot `{ "newSlotId": 7 }` |
| GET | `/api/doctor/profile` | DOCTOR | My profile and approval status |
| POST | `/api/doctor/slots` | DOCTOR | Create one slot |
| POST | `/api/doctor/slots/bulk` | DOCTOR | Create many slots `{ "slotDate", "startTime", "endTime", "slotMinutes" }` |
| GET | `/api/doctor/slots?date=...` | DOCTOR | My slots (all upcoming, or one day) |
| DELETE | `/api/doctor/slots/{id}` | DOCTOR | Delete a free slot |
| GET | `/api/doctor/appointments?date=...` | DOCTOR | My appointments (all upcoming, or one day) |
| PUT | `/api/doctor/appointments/{id}/complete` | DOCTOR | Mark an appointment completed |
| GET | `/api/admin/doctors/pending` | ADMIN | Doctors waiting for approval |
| PUT | `/api/admin/doctors/{doctorId}/approve` | ADMIN | Approve a doctor |
| GET | `/api/admin/stats` | ADMIN | Dashboard numbers |

Errors always look like `{ "timestamp", "status", "error", "message" }` (validation errors add `fieldErrors`).
Status codes: 400 bad input, 401 not logged in, 403 wrong role or not your data, 404 not found, 409 conflict (slot already taken).

## How double-booking is prevented

`AvailabilitySlot` has a JPA `@Version` field (optimistic locking).

1. Patient A and patient B both read slot 5 as `AVAILABLE` (version 3).
2. A saves first: `UPDATE ... SET status='BOOKED', version=4 WHERE id=5 AND version=3` changes 1 row. A wins.
3. B tries the same statement with `version=3`, but the row is now version 4, so 0 rows change. Hibernate throws an optimistic-locking exception.
4. `AppointmentService` catches it, rolls the transaction back (no appointment is created) and returns **409 "slot was just booked by another patient"**.

Why optimistic and not pessimistic locking? Collisions on one slot are rare, so not locking rows on every read is cheaper and keeps the app scalable. When a collision does happen, the loser simply gets a clear message.

The booking flow is `@Transactional`: slot update, appointment insert and the "booked" event either all succeed or all roll back. Rescheduling books the new slot first, so if the new slot is taken, the old booking stays untouched.

## Email notifications

`AppointmentService` publishes an `AppointmentEvent`. `EmailService` listens with `@TransactionalEventListener(AFTER_COMMIT)` (the email is sent only if the booking really got saved) and `@Async` (the patient does not wait for the mail server). While `app.mail.enabled=false` the email is only printed in the log. To send real emails, set `MAIL_ENABLED=true` and uncomment the `spring.mail.*` lines in `application.properties`.

## Tests

```bash
mvn test
```

| Test | What it proves |
|---|---|
| `AppointmentServiceTest` (Mockito) | booking, taken slot, lost race, unapproved doctor, past slot, cancel, someone else's appointment, reschedule |
| `DoctorServiceTest` (Mockito) | bulk slot generation, partial last slot dropped, overlap, approval, slot deletion rules |
| `BookingConcurrencyTest` (H2) | 10 patients book the same slot at the same moment: exactly one succeeds |
| `JwtServiceTest` | valid, tampered and expired tokens |

## Docker

```bash
docker compose up --build
```
Starts MySQL and the app, then open http://localhost:8080.

## Deploying

Any host that runs a Docker image or a Java jar works (Render, Railway, ...). Set these environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (a new random Base64 string of at least 32 bytes) and change the default admin password in `DataInitializer`.

## Project structure

```
controller    REST endpoints (thin: receive request, call a service)
service       business rules and transactions
repository    database access (Spring Data JPA)
entity        database tables
dto           request / response objects
security      JWT service, request filter, role rules
exception     custom exceptions + one global JSON error handler
notification  appointment events and email sending
config        startup configuration (default admin, async)
resources/static   the frontend (index.html, css/style.css, js/app.js)
```

## Possible improvements

Flyway migrations instead of `ddl-auto=update`, refresh tokens, rate limiting on login, caching doctor search (Redis), a waiting list that auto-books a cancelled slot, reminders by a scheduled job.
