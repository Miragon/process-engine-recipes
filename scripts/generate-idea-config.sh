#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
idea_dir="$repo_root/.idea"

maven_projects() {
  cd "$repo_root" && ls recipes/*/*/pom.xml | sort
}

gradle_projects() {
  cd "$repo_root" && ls recipes/*/*/settings.gradle.kts | xargs -n1 dirname | sort
}

render_misc_xml() {
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<project version="4">'
  echo '  <component name="MavenProjectsManager">'
  echo '    <option name="originalFiles">'
  echo '      <list>'
  for pom in $(maven_projects); do
    echo "        <option value=\"\$PROJECT_DIR\$/$pom\" />"
  done
  echo '      </list>'
  echo '    </option>'
  echo '  </component>'
  echo '</project>'
}

render_gradle_xml() {
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<project version="4">'
  echo '  <component name="GradleSettings">'
  echo '    <option name="linkedExternalProjectsSettings">'
  for project in $(gradle_projects); do
    echo '      <GradleProjectSettings>'
    echo "        <option name=\"externalProjectPath\" value=\"\$PROJECT_DIR\$/$project\" />"
    echo '      </GradleProjectSettings>'
  done
  echo '    </option>'
  echo '  </component>'
  echo '</project>'
}

if [ "${1:-}" = "--check" ]; then
  diff <(render_misc_xml) "$idea_dir/misc.xml" && diff <(render_gradle_xml) "$idea_dir/gradle.xml" || {
    echo "The IntelliJ project files are out of date, run scripts/generate-idea-config.sh" >&2
    exit 1
  }
  echo "The IntelliJ project files are up to date."
else
  mkdir -p "$idea_dir"
  render_misc_xml > "$idea_dir/misc.xml"
  render_gradle_xml > "$idea_dir/gradle.xml"
  echo "Wrote .idea/misc.xml and .idea/gradle.xml."
fi
