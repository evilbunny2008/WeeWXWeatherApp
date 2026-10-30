#!/usr/bin/env bash
#
# Builds signed release outputs, with a separate signing key for the APK and the AAB.
#
# Usage: scripts/build-signed.sh [--config FILE] [apk|aab|both]
#
# The signing details live outside the repo, by default in
#   ${XDG_CONFIG_HOME:-~/.config}/weewxapp/signing.conf
# or wherever WEEWXAPP_SIGNING_CONFIG / --config points. See
# scripts/signing.conf.example for the format. Leave a password blank to be
# prompted for it.
#
# The key is handed to Gradle as -Pandroid.injected.signing.* arguments, the
# same properties Android Studio's signing wizard uses. AGP ignores them when
# set through ORG_GRADLE_PROJECT_* environment variables, so while the build
# runs the passwords are visible in `ps` output to other users on this machine.

set -euo pipefail

usage()
{
	sed -n '4,5p' "$0" | sed 's/^# \{0,1\}//'
	exit "${1:-0}"
}

config="${WEEWXAPP_SIGNING_CONFIG:-${XDG_CONFIG_HOME:-$HOME/.config}/weewxapp/signing.conf}"
target="both"

while [[ $# -gt 0 ]]
do
	case "$1" in
		--config) config="${2:?--config needs a file}"; shift 2 ;;
		-h|--help) usage ;;
		apk|aab|both) target="$1"; shift ;;
		*) echo "Unknown argument: $1" >&2; usage 1 ;;
	esac
done

if [[ ! -f "$config" ]]
then
	echo "Signing config not found: $config" >&2
	echo "Copy scripts/signing.conf.example there and fill it in." >&2
	exit 1
fi

if [[ "$(stat -c %a "$config")" != 600 ]]
then
	echo "Warning: $config holds passwords, consider: chmod 600 '$config'" >&2
fi

# Read KEY=value lines without sourcing the file, so passwords may contain any
# character. Surrounding quotes around a value are optional and stripped.
declare -A conf=()
while IFS= read -r line || [[ -n "$line" ]]
do
	[[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue

	if [[ ! "$line" =~ ^[[:space:]]*(APK|AAB)_(STORE_FILE|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)[[:space:]]*=(.*)$ ]]
	then
		echo "Ignoring unrecognised line in $config: ${line%%=*}" >&2
		continue
	fi

	key="${BASH_REMATCH[1]}_${BASH_REMATCH[2]}"
	value="${BASH_REMATCH[3]}"
	if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]
	then
		value="${BASH_REMATCH[1]}"
	fi

	conf[$key]="$value"
done < "$config"

# Fill in and check one output's settings, prompting for blank passwords
prepare()
{
	local kind="$1" name

	for name in STORE_FILE KEY_ALIAS
	do
		if [[ -z "${conf[${kind}_$name]:-}" ]]
		then
			echo "${kind}_$name is not set in $config" >&2
			exit 1
		fi
	done

	# Allow ~ in the keystore path
	conf[${kind}_STORE_FILE]="${conf[${kind}_STORE_FILE]/#\~/$HOME}"
	if [[ ! -f "${conf[${kind}_STORE_FILE]}" ]]
	then
		echo "${kind} keystore not found: ${conf[${kind}_STORE_FILE]}" >&2
		exit 1
	fi

	if [[ -z "${conf[${kind}_STORE_PASSWORD]:-}" ]]
	then
		read -rsp "${kind} keystore password: " "conf[${kind}_STORE_PASSWORD]"
		echo
	fi

	# Most keys share the keystore's password, so default to it
	if [[ -z "${conf[${kind}_KEY_PASSWORD]:-}" ]]
	then
		read -rsp "${kind} key password (blank = same as keystore): " "conf[${kind}_KEY_PASSWORD]"
		echo
		conf[${kind}_KEY_PASSWORD]="${conf[${kind}_KEY_PASSWORD]:-${conf[${kind}_STORE_PASSWORD]}}"
	fi
}

repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo"

if [[ -x ./gradlew ]]
then
	gradle=(./gradlew)
else
	gradle=(gradle)
fi

# Edits to the Gradle files (like a version bump) are easy to forget to sync in
# Android Studio. Every sync rewrites its per-project model cache (the imported
# build model is only rewritten when the project's structure changes), so stop if
# a Gradle file is newer than the last sync.
check_gradle_synced()
{
	local name marker="" newest=0 f t
	local changed=()

	name="$(basename "$repo" | tr '[:upper:]' '[:lower:]')"
	for f in "$HOME"/.cache/Google/AndroidStudio*/projects/"$name".*/{project-model-cache/cache.data,external_build_system/project.dat}
	do
		[[ -f "$f" ]] || continue
		t="$(stat -c %Y "$f")"
		if (( t > newest ))
		then
			newest="$t"
			marker="$f"
		fi
	done

	if [[ -z "$marker" ]]
	then
		echo "Note: no Android Studio sync found for this project, skipping the Gradle sync check" >&2
		return
	fi

	for f in settings.gradle* build.gradle* app/build.gradle* gradle.properties gradle/*.toml gradle/wrapper/gradle-wrapper.properties
	do
		if [[ -f "$f" && "$f" -nt "$marker" ]]
		then
			changed+=("$f")
		fi
	done

	if (( ${#changed[@]} ))
	then
		echo "ERROR: Gradle files have changed since Android Studio last synced the project:" >&2
		printf '  %s\n' "${changed[@]}" >&2
		echo "Sync it (File > Sync Project with Gradle Files), then run this again." >&2
		exit 1
	fi
}

check_gradle_synced

# Run one Gradle task signed with the given output's key
build()
{
	local kind="$1" task="$2"
	local p="-Pandroid.injected.signing"

	echo "==> $task, signed with ${conf[${kind}_KEY_ALIAS]} from ${conf[${kind}_STORE_FILE]}"

	"${gradle[@]}" "$task" \
		"$p.store.file=${conf[${kind}_STORE_FILE]}" \
		"$p.store.password=${conf[${kind}_STORE_PASSWORD]}" \
		"$p.key.alias=${conf[${kind}_KEY_ALIAS]}" \
		"$p.key.password=${conf[${kind}_KEY_PASSWORD]}"
}

# SHA-256 fingerprint as lowercase hex without colons, so both tools' output compares
normalise()
{
	tr -d ': ' | tr '[:upper:]' '[:lower:]'
}

# Fail unless the file is signed with the configured key, so an unsigned or
# wrongly signed build can't slip through looking like a success
verify_signer()
{
	local kind="$1" file="$2" expected actual

	if [[ ! -f "$file" ]]
	then
		echo "Expected output not found: $file" >&2
		exit 1
	fi

	# :env keeps the password off keytool's command line
	expected="$(SIGNING_STORE_PASSWORD="${conf[${kind}_STORE_PASSWORD]}" keytool -list -v \
		-keystore "${conf[${kind}_STORE_FILE]}" -alias "${conf[${kind}_KEY_ALIAS]}" \
		-storepass:env SIGNING_STORE_PASSWORD | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | normalise)"

	if [[ "$file" == *.apk ]]
	then
		local sdk apksigner
		sdk="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)"
		apksigner="$(ls -1 "${sdk:-${ANDROID_HOME:-/nonexistent}}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
		if [[ -z "$apksigner" ]]
		then
			echo "apksigner not found, can't verify $file" >&2
			exit 1
		fi

		actual="$("$apksigner" verify --print-certs "$file" 2>&1 |
			sed -n 's/^Signer #1 certificate SHA-256 digest: //p; s/^V[0-9.]* Signer: certificate SHA-256 digest: //p' |
			head -1 | normalise)"
	else
		actual="$(keytool -printcert -jarfile "$file" 2>&1 | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | normalise)"
	fi

	if [[ -z "$actual" ]]
	then
		echo "FAILED: $file is not signed" >&2
		exit 1
	fi

	if [[ "$actual" != "$expected" ]]
	then
		echo "FAILED: $file is signed with $actual, expected $expected" >&2
		exit 1
	fi

	echo "$kind: $file"
	echo "     signed with ${conf[${kind}_KEY_ALIAS]}, SHA-256 $actual"
}

version="$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' app/build.gradle.kts)"

if [[ "$target" == apk || "$target" == both ]]
then
	prepare APK
fi

if [[ "$target" == aab || "$target" == both ]]
then
	prepare AAB
fi

if [[ "$target" == apk || "$target" == both ]]
then
	build APK assembleRelease
	verify_signer APK "app/dist/WeeWXApp-v$version.apk"
fi

if [[ "$target" == aab || "$target" == both ]]
then
	build AAB bundleRelease
	verify_signer AAB "app/dist/WeeWXApp-v$version.aab"
fi
