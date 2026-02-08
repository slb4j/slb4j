Version 0.5
-----------

- Provide a mechanism for backend internal logging instead of simply writing to stderr
- Log4J2 configuration parser
  - support shorthand definition in log4j2.properties ("rootLogger=DEBUG, STDOUT")
  - supper apenderref
  - support setting the level for backend status messages
  - fix Rolling File Appender configuration parsing
  - Refactor tests, stricter validation of parsing results
  - fix wrong names being used for stdout and stderr in ConsoleHandler
  - fix incorrect warnings being displayed for valid configurations
- PatternLayout: fix extra newline when pattern includes exception but nothing was thrown
- Remove unused code

Version 0.4
-----------

- feature: extensible using plugins
- fix: IOOBE when logging messages that exceed the preallocated buffer size
- moved CsvLayout, XmlLayout, YamlLayout into org.slb4j.ext.layouts plugin

Version 0.3.1
-------------

- fix: exception thrown when formatting certain messages from PlatformLogger
- cleanp parallel benchmark code; include al backends

Version 0.3
-----------

- added YamlLayout
- rewrote properties parser for better log4j2.properties compatibility
- added rudimentary JUL logging.properties parser
- small fixes abd improvements

Version 0.2.1
-------------

- fix: location was not resolved
- replace synchronizedblocks with reentrant locks to avoid pinning virtual threads

Version 0.2
-----------

- Log4J2 compatible configuration:
  - fix exception on reading "layout"
  - support ThresholdFilter 
- layouts:
  - SimpleLayout
  - CsVLayout
  - XmlLayout
  - JsonLayout
- small bug fixes and improvements

- Version 0.1
-----------

Initial release.

Version 0.1-rc*
---------------

- added localized pattern support for Log4J log patterns
- added JUL pattern support
- changed configuration resolution: try to load log4j2-test.properties before log4j2.properties using Log4J2 properties
  format, then logging.properties using java.util.logging format
- auto-lod if main module uses JCL, Log4J, or SLF4J.
- minor enhancements and bugfixes
- performance: reduced object allocations

Version 0.1-beta2
-----------------

- Use default configuration when no logging.properties file is found: console handler with log level INFO
- Add LoggingConfiguration.setRootFilter()/getRootFilter()/getHandler()/addHandler().

Version 0.1-beta
----------------

- Initial release
