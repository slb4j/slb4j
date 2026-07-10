Version 0.12.2
--------------

- update Gradle version locking
- fixed an incompatibility with Log4J pattern layout
- added new methods for checking availability of SLB4J extensions for Swing and JavaFX
- build script refactorings and fixes for Gradle 10 compatibility warnings
- some minor code and documentation fixes; plugin and dependency uopdates

Version 0.12.1
--------------

- fix race condition when setting logger levels

Version 0.12.0
--------------

- enable Gradle dependency locking
- minor fixes and i,provements
- use numerical values for log levels in LevelMap
- rename Dispatcher.setLevel(LogLevel) to Dispatcher.setRootLevel(LogLevel)

Version 0.11.0
--------------

- update dependencies and plugins
- fix separator layout in FxLogPane
- fix exception in SwingLogPane
- add -Xlint:unchecked, -Xlint:deprecation compiler flags

Version 0.10.0
--------------

- update dependencies and plugins
- improve level and filter handling
- fix optional locale argument not being applied to custom layout patterns in some cases

Version 0.9.0
-------------

- improved status logger configuration
- support `file:` and `classpath:` prefixes for log configuration properties file
- fix configuration files not being loaded from jar resources
- improved logging

Version 0.8.1
-------------

- update dependencies, plugins
- add setDarkode(), getLogPane() to LogWindow and the implementing classes

Version 0.8.0
-------------

- fix DST not being taken into account when the current timestamp is before/after the DST transition when program starts
- add configuration parsers for JSON, XML, and YAML config files; implemented in new modules slb4j-config-* to avoid 
  adding unneeded dependencies for projects that use properties files for configuration
- add JUL configuration parser
- migrate to Jackson 3
- update plugins and dependencies

Version 0.7.2
-------------

- fix: UniversalDispatcher.fiterAndDispatch() reported wrong parameter names in exception messages.

Version 0.7.1
-------------

- fix: combining an allpass/nonepass filter with another filter should not create a combined filter.

Version 0.7.0
-------------

- Implemented garbage free logging when using Log4J2 API; Improve performance for parametrized logging
  statements using Log4J2 API.
- Reduce lock contention for file based loggers.
- Refactor UniversalDispatcher to only use a single filter instance for both root level and pacjage level filtering.
- Replace volatile fields with varhandles to avoid cache flushes.
- implement Log4J2 compatible file index strategies `min`and `max` in RotatingFileHandler
- refactored RotatingFileHandler to fix possible race conditions and be more lenient when I/O errors occurr
- refactored checking log rotation conditions to check the size before writing the next entry
- fix re-entrant logging calls from the same thread
- fix default highlight pattern to reset color before the line break
- small fixes and performance improvements

Version 0.6.0
-------------

- Reduce (bound lambda) allocations by replacing enhanced for loops with indexed loops on hot paths
- Add SLB4J.getConfiguration(), SLB4J.setConfiguration()

Version 0.5.1
-------------

- When defining the configuration file location using environment variables or system settings,
  load from file system, not resource.
- Change the default layout for the detail view in both SwingLogPane and FxLogPane; make the layout configurable.

Version 0.5
-----------

- Provide a mechanism for backend internal logging instead of simply writing to stderr
- Log4J2 configuration parser:
  - support configuring the properties file using system property or environment variable
  - support shorthand definition in log4j2.properties ("rootLogger=DEBUG, STDOUT")
  - support apenderref
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
