SLB4J
=====

SLB4J is a Simple Logging Backend for Java.

Goal
----

Create a lightweight, zero-dependencies logging backend for Java.

- Easy to use (one dependency, one line of code).
- Compatible with the four common frontends: JUL, Log4J2, SLF4J, JCL.
- Zero dependencies, no bridge modules.
- Not noticeably slower than other backends.

Status
------

### Done

- [x] Backend
  - [x] Console handler
  - [x] File handler
  - [x] Logfile rotation
- [x] Frontend support
  - [x] JUL (Java Util Logging)
  - [x] Log4J2
  - [x] SLF4J
  - [x] JCL (Jakarta Commons Logging / Apache Commons Logging)
- [x] Log4J2 compatible message pattern
  - [x] standard patterns
  - [x] MDC support
  - [x] Marker support
  - [x] Location support
- [x] logging.properties file
- [x] Filters
- [x] UI components for live monitoring
- [x] Benchmarks

### Todo

- [ ] Setup CI
- [ ] Write README
- [ ] Publish to Maven Central
- [ ] Publish Javadoc
- [ ] Add benchmark results

### Later
- [ ] JSON output format
- [ ] Read back JSON logs for later analysis

### Not planned

- Async loggers
