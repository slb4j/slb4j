# Releasing SLB4J

SLB4J publishes the slb4j-bom on every stable release. Major and minor releases
publish all libraries; patch releases publish only the libraries selected by the
prepared release plan.

## Prepare

Start from a clean branch that is synchronized with its upstream:

~~~bash
./scripts/prepare-release.sh --type patch
./scripts/prepare-release.sh --type minor
./scripts/prepare-release.sh --type major --version 1.0.0
~~~

The script first runs a dry run. It validates the release line, scoped Git history,
Maven Central coordinates, and module selection. It asks before writing and committing
gradle/prepared-release.toml, then asks separately before pushing the preparation
commit. A patch with no changed library is rejected.

Use --additional-modules module-a,module-b only after reviewing why an unchanged
dependent must publish a new minimum internal dependency version.

The preparation commit must remain the tip of the protected release branch while CI
publishes it.

## Verify and stage locally

These commands are useful for diagnosis or an approved local release:

~~~bash
./gradlew --no-configuration-cache verifyPreparedRelease checkReleaseCompatibility
./gradlew --no-configuration-cache stagePreparedRelease
./gradlew --no-configuration-cache publishPreparedRelease
~~~

stagePreparedRelease runs the checks and stages only the selected libraries plus the
BOM. checkReleaseCompatibility is mandatory for patch releases.

## CI publication

Normal CI builds and tests the prepared candidate, including the Linux/Xvfb test job,
and uploads the unsigned staging directory with its checksum manifest. It does not
receive signing or Maven Central credentials.

The protected Publish prepared release workflow downloads the bundle from that exact
successful CI run, verifies the plan, artifact set, and checksums, then signs and
deploys the bundle with JReleaser. It never rebuilds the artifacts being promoted.
It starts automatically after a successful Build run containing a committed prepared
plan. Manual dispatch is retained only as a controlled retry path and requires the
protected branch (`main`) and the successful Build run ID.

Required protected secrets are:

* SONATYPE_USERNAME
* SONATYPE_PASSWORD
* SIGNING_SECRET_KEY
* SIGNING_PASSWORD

## Finalize

After Maven Central exposes every expected artifact, finalization promotes the plan to
gradle/release-state.toml, removes the prepared plan, advances the development
version, commits the state, and creates the annotated release tag:

~~~bash
./gradlew --no-configuration-cache finalizeRelease -PconfirmFinalize=true
~~~

The protected workflow can also push the finalized commit and tag:

~~~bash
./gradlew --no-configuration-cache finalizeRelease -PconfirmFinalize=true -PpushReleaseTag=true -PreleaseBranch=main
~~~

If deployment succeeded but finalization failed, rerun finalization after checking
Maven Central; it is designed to avoid republishing. Never reuse a coordinate that
Maven Central accepted.

## Snapshots

Development continues with projectVersion in gradle/libs.versions.toml. To publish all
current snapshots locally:

~~~bash
./gradlew publishSnapshotsToMavenLocal
~~~
