#!/bin/bash
set -euo pipefail

source_dir=$(cd -- "$(dirname -- "$0")" && pwd)
output_dir=${1:?output directory required}
java_home=${AIR_JAVA_HOME:-/Applications/Air.app/Contents/jbr/Contents/Home}

if [[ ! -x "$java_home/bin/javac" ]]; then
  echo "Air's bundled JDK is unavailable: $java_home" >&2
  exit 1
fi

mkdir -p "$output_dir/classes" "$output_dir/helper/airui2"
"$java_home/bin/javac" -d "$output_dir/classes" "$source_dir"/src/airui2/*.java
"$java_home/bin/jar" cfm "$output_dir/usage-agent.jar" "$source_dir/agent.mf" \
  -C "$output_dir/classes" airui2

for helper in UsageText UsageRow IconFallback GenericIconFallback SelectedIconFallback SelectedIconFallback2 DefaultName; do
  cp "$output_dir/classes/airui2/$helper.class" "$output_dir/helper/airui2/"
done
"$java_home/bin/jar" cf "$output_dir/usage-helper2.jar" -C "$output_dir/helper" airui2
