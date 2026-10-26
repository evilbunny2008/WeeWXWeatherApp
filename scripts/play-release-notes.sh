#!/usr/bin/env bash
#
# Writes Google Play release notes for every store listing translation, with the
# Changelog URL comparing the previous release tag to this one.
#
# Usage: scripts/play-release-notes.sh [VERSION [PREVIOUS]]
#
# VERSION defaults to versionName in app/build.gradle.kts, PREVIOUS to the
# highest v* tag below VERSION. The locales are the folders under
# fastlane/metadata/android/,
# and the translated Source/Changelog labels come from
# scripts/play-release-notes.labels.
# The result is saved to app/dist/release-notes-v<VERSION>.txt, ready to paste
# into the Play Console's release notes box (or upload).

set -euo pipefail

repo_url="https://github.com/evilbunny2008/WeeWXWeatherApp"

repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo"

case "${1:-}" in
	-h|--help) sed -n '4,5p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

# Accept versions with or without the leading v
version="${1:-$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' app/build.gradle.kts)}"
version="${version#v}"

if [[ ! "$version" =~ ^[0-9]+(\.[0-9]+)*$ ]]
then
	echo "Can't work out the version, got '$version'" >&2
	exit 1
fi

if [[ -n "${2:-}" ]]
then
	previous="${2#v}"
else
	# Highest tag that sorts below this version
	previous="$( (git tag -l 'v*'; echo "v$version") | sort -uV | grep -B1 -x "v$version" | head -1)"
	previous="${previous#v}"

	if [[ -z "$previous" || "$previous" == "$version" ]]
	then
		echo "No release tag found below v$version, pass PREVIOUS explicitly" >&2
		exit 1
	fi
fi

if ! git rev-parse -q --verify "refs/tags/v$previous" > /dev/null
then
	echo "Warning: tag v$previous doesn't exist, the Changelog link will be broken" >&2
fi

if ! git rev-parse -q --verify "refs/tags/v$version" > /dev/null
then
	echo "Note: tag v$version doesn't exist yet, push it before the notes go live" >&2
fi

locales=()
for dir in fastlane/metadata/android/*/
do
	locales+=("$(basename "$dir")")
done

if [[ ${#locales[@]} -eq 0 ]]
then
	echo "No locales found under fastlane/metadata/android/" >&2
	exit 1
fi

labels_file="scripts/play-release-notes.labels"
declare -A source_label=() changelog_label=()
while IFS='|' read -r locale source changelog
do
	[[ -z "$locale" || "$locale" == \#* ]] && continue
	source_label[$locale]="$source"
	changelog_label[$locale]="$changelog"
done < "$labels_file"

# Refuse to write notes with a locale left untranslated
missing=()
for locale in "${locales[@]}"
do
	if [[ -z "${source_label[$locale]:-}" || -z "${changelog_label[$locale]:-}" ]]
	then
		missing+=("$locale")
	fi
done

if [[ ${#missing[@]} -gt 0 ]]
then
	echo "No labels in $labels_file for: ${missing[*]}" >&2
	exit 1
fi

out="app/dist/release-notes-v$version.txt"
mkdir -p "$(dirname "$out")"

for locale in "${locales[@]}"
do
	printf '<%s>\n%s %s\n%s %s/compare/v%s...v%s\n</%s>\n' \
		"$locale" "${source_label[$locale]}" "$repo_url" \
		"${changelog_label[$locale]}" "$repo_url" "$previous" "$version" "$locale"
done > "$out"

echo "Wrote ${#locales[@]} locales to $out"
echo "Changelog: $repo_url/compare/v$previous...v$version"
