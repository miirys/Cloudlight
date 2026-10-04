#!/usr/bin/env bash
# Renders the main desktop surfaces at 1920x1080 (the home theatre target) so
# UI changes can be reviewed as images. Failed captures keep their log and the
# run continues, so one broken surface does not hide the rest.
set -uo pipefail

if [[ $# -ne 2 ]]; then
    printf 'Usage: bash %s <built-opennow-qt> <output-directory>\n' "$0" >&2
    exit 2
fi

app=$(realpath "$1")
mkdir -p "$2"
output=$(realpath "$2")
failed=0

capture() {
    local name=$1
    shift
    if xvfb-run -a -s '-screen 0 1920x1080x24' env QT_QPA_PLATFORM=xcb QSG_RHI_BACKEND=opengl \
        timeout 60 "$app" --smoke-test --allow-multiple-instances --desktop --reduced-motion \
        --smoke-width 1920 --smoke-height 1080 --screenshot "$output/$name.png" "$@" \
        >"$output/$name.log" 2>&1 && test -s "$output/$name.png"; then
        printf 'ok   %s\n' "$name"
    else
        printf 'FAIL %s (see %s.log)\n' "$name" "$name"
        failed=$((failed + 1))
    fi
}

capture home --route home --smoke-paper-design
capture library --route library --smoke-collections
capture game-detail --route home --smoke-game-details-layout --details-owned --details-scale 1
capture store --route store --smoke-store-paging --smoke-store-appearance
capture stream-stats --route stream --smoke-stream-stats --smoke-stats-expanded
for page in stream network audio controls appearance account about; do
    capture "settings-$page" --route settings --smoke-paper-design --smoke-settings-layout \
        --smoke-settings-page "$page"
done

printf '%d capture(s) failed\n' "$failed"
