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
