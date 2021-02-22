#!/bin/bash

# Burden the container with chromium (re)start
chromium --remote-debugging-port=9222 --headless --no-sandbox --disable-setuid-sandbox --disable-notifications --disable-geolocation --disable-infobars --disable-session-crashed-bubble --disable-dev-shm-usage --disable-gpu --disable-translate --disable-extensions --disable-background-networking  --disable-sync --disable-default-apps --hide-scrollbars --metrics-recording-only --mute-audio --no-first-run --unlimited-storage --safebrowsing-disable-auto-update --font-render-hinting=none &
sleep 1s
./htmltopdf
