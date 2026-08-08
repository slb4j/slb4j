#!/usr/bin/env bash

set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)

die() {
  echo "ERROR: $1" >&2
  exit 1
}

# Create a true temporary file on disk for the init script
INIT_SCRIPT=$(mktemp "${TMPDIR:-/tmp}/gradle-init-XXXXXX.gradle")

# Define the cleanup function to delete the temp file when the script exits
cleanup() {
  rm -f "$INIT_SCRIPT"
}
trap cleanup EXIT

# Write the actual Groovy DSL directly into the temporary file
cat << 'EOF' > "$INIT_SCRIPT"
allprojects {
    tasks.register('resolveAllConfigurationsForLocking') {
        group = 'dependency locking'
        description = 'Resolves every resolvable configuration in this project.'

        doLast {
            project.configurations
                .findAll {
                    it.canBeResolved &&
                        // GraalVM creates this configuration without complete dependency versions.
                        it.name != 'nativeImageTestClasspath'
                }
                .sort { left, right -> left.name <=> right.name }
                .each { configuration ->
                    logger.lifecycle("Resolving ${project.path}:${configuration.name}")
                    configuration.resolve()
                }
        }
    }
}

gradle.projectsEvaluated {
    rootProject.tasks.register('resolveAllConfigurationsForLockingAllProjects') {
        group = 'dependency locking'
        description = 'Resolves every resolvable configuration in every project.'
        dependsOn(rootProject.allprojects.collect { project ->
            project.tasks.named('resolveAllConfigurationsForLocking')
        })
    }
}
EOF

echo "------------------------------------------------------------"
echo " Processing ..."
echo "------------------------------------------------------------"

(
  [[ -x "./gradlew" ]] || die "Gradle wrapper is missing or not executable in project"

  # Update every currently locked module so stale strict constraints can move
  # to the versions selected by the current build scripts and BOMs.
  LOCKED_MODULES=$(find . -type f -name 'gradle.lockfile' -not -path '*/build/*' -exec awk -F= '
      $1 !~ /^#/ && $1 != "empty" {
          count = split($1, coordinate, ":")
          if (count == 3) print coordinate[1] ":" coordinate[2]
      }
  ' {} + | sort -u | paste -sd, -)

  GRADLE_ARGS=(
      resolveAllConfigurationsForLockingAllProjects
      --write-locks
      --no-configuration-cache
      -I "$INIT_SCRIPT"
  )

  if [[ -n "$LOCKED_MODULES" ]]; then
      GRADLE_ARGS+=(--update-locks "$LOCKED_MODULES")
  fi

  ./gradlew "${GRADLE_ARGS[@]}" \
        || die "Lock file update failed for project"
) || exit 1

echo "==========================================="
echo " SUCCESS: All lockfiles updated successfully! "
echo "==========================================="
