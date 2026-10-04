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

run_app() {
    local platform=$1 name=$2
    shift 2
    if [[ $platform == xcb ]]; then
        xvfb-run -a -s '-screen 0 1920x1080x24' env QT_QPA_PLATFORM=xcb QSG_RHI_BACKEND=opengl \
            timeout 60 "$app" --smoke-test --allow-multiple-instances --desktop --reduced-motion \
            --smoke-width 1920 --smoke-height 1080 --screenshot "$output/$name.png" "$@"
    else
        env QT_QPA_PLATFORM=offscreen timeout 60 "$app" --smoke-test --allow-multiple-instances \
            --desktop --reduced-motion --smoke-width 1920 --smoke-height 1080 \
            --screenshot "$output/$name.png" "$@"
    fi
}

capture() {
    local name=$1 platform status
    shift
    for platform in xcb offscreen; do
        rm -f "$output/$name.png"
        run_app "$platform" "$name" "$@" >"$output/$name.$platform.log" 2>&1
        status=$?
        printf 'exit status %s\n' "$status" >>"$output/$name.$platform.log"
        if [[ -s $output/$name.png ]]; then
            printf 'ok   %s (%s, exit %s)\n' "$name" "$platform" "$status"
            return
        fi
    done
    printf 'FAIL %s (exit %s, see %s.*.log)\n' "$name" "$status" "$name"
    failed=$((failed + 1))
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
