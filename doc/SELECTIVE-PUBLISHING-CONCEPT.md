# Selective Maven Publishing for SLB4J

## Goal

Patch releases publish only the SLB4J libraries whose owned inputs changed, together with
the org.slb4j:slb4j-bom artifact. Major and minor releases remain full releases.

This keeps patch releases small while the BOM continues to describe one coherent,
consumer-facing set of versions.

## Release model

Versions use major.minor.patch.

| Release | BOM | Selected libraries | Other libraries |
| --- | --- | --- | --- |
| Major X.0.0 | X.0.0 | all at X.0.0 | all published |
| Minor X.Y.0 | X.Y.0 | all at X.Y.0 | all published |
| Patch X.Y.Z | X.Y.Z | changed libraries at X.Y.Z | retain previous versions |

The BOM is always published. For example, a patch after 0.12.3 may contain:

~~~text
org.slb4j:slb4j-bom:0.12.4
org.slb4j:slb4j:0.12.4
org.slb4j:slb4j-config:0.12.3
org.slb4j:slb4j-ext:0.12.4
~~~

The BOM constraints use those exact versions; they never assume that every module has
the BOM version.

## Release metadata

gradle/release-state.toml is the authoritative record of successfully published
artifacts. It stores the BOM version and, for every library, its published version,
source revision, and owned paths.

gradle/prepared-release.toml is a committed candidate plan. It records the release
revision, every module version, selected modules, selection reasons, and the expected
artifact set. It is the only input used to select publications. It is removed only
after successful Maven Central publication and finalization.

The prepared plan records the source revision before the plan commit. Consequently, a
release-plan commit does not make unrelated modules look changed on the next release.

## Publishable modules

The publishable library set is:

* slb4j
* slb4j-ext
* slb4j-ext-layouts
* slb4j-ext-swing
* slb4j-ext-fx
* slb4j-config
* slb4j-config-json
* slb4j-config-yaml
* slb4j-config-xml
* slb4j-config-all

Samples, native-image tests, and benchmarks are never published. slb4j-bom is the
platform publication and is always included.

## Change detection

Each library is compared with its own publishedRevision using a path-scoped Git diff.
The planner does not compare every module with repository HEAD, because an unrelated
commit must not republish every artifact.

Shared inputs conservatively select all libraries:

* root build and settings scripts;
* buildSrc, Gradle properties, version catalog, wrapper, and dependency locks;
* shared publishing, signing, compiler, or toolchain configuration.

Repository documentation, release notes, CI-only files, and administration files do
not select a library. Documentation under a library source tree does, because it
changes the sources or Javadoc artifact. Changes to slb4j-bom itself select the BOM,
which is already mandatory, but do not select libraries.

The planner accepts -PadditionalReleaseModules=a,b for a reviewed case where a
dependent must publish a new minimum internal dependency version even though its own
owned paths did not change.

## Dependency propagation

An unchanged dependent is not republished solely because an internal dependency received
a compatible patch. Its existing POM and Gradle metadata continue to describe the
version against which that artifact was built; the new BOM aligns consumers with the
patched dependency.

An unchanged dependent is selected only when it changed itself or when a release operator
explicitly selects it to publish a new minimum internal dependency version. This rule
applies to api, implementation, optional, and transitive project dependencies.
Shaded or embedded dependencies require selection when the dependent artifact changes.

## Gradle behavior

The settings script reads the development version, published state, and optional
prepared plan before projects are configured:

1. normal development uses projectVersion for all libraries and the BOM;
2. a prepared plan assigns each selected library its candidate version;
3. retained libraries use their last published version;
4. the BOM receives the plan version and constrains every library to its effective
   version;
5. staging publication is limited to the selected libraries and the BOM.

Snapshots can still be published to the local Maven repository with
publishSnapshotsToMavenLocal; selective publication is only for stable releases.

## Release safety

The planner requires a clean working tree, valid and ancestor published revisions,
stable non-snapshot versions, and unused Maven Central coordinates. Patch plans are
checked for binary and source compatibility with japicmp.

Publishing is based on the committed plan. CI creates an unsigned, checksummed
publication bundle after the normal build and tests. A protected release workflow
verifies and promotes that exact bundle, signs it, and deploys it. It does not rebuild
or derive a new module selection from Git state.

If any Maven Central coordinate was accepted, its version is permanently consumed.
Retry the exact plan only when the deployment outcome is known to be safe; otherwise
prepare a new patch version. The final release tag is created only after publication
and state finalization succeed.
