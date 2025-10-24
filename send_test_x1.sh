#!/usr/bin/env bash
#
# send_test_x1.sh - Helper script to broadcast a debug x1 notification to your app.
#
# This script automates base64 encoding of the curl command and sends a broadcast
# intent via ADB to the application's DebugBroadcastReceiver.  It eliminates
# the need to manually encode commands that contain flags beginning with '-'.
#
# Usage:
#   ./send_test_x1.sh "<curl command>" "<endpoint URL>"
#
# Example:
#   ./send_test_x1.sh "curl -I https://www.example.com" "https://webhook.site/your-endpoint"
#
# Make sure ADB is in your PATH and your device/emulator is connected.

set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 \"<curl command>\" \"<endpoint URL>\""
  exit 1
fi

CURL_COMMAND="$1"
ENDPOINT_URL="$2"

# Encode the curl command to Base64 and remove any newlines.  Using 'base64'
# without options works on most systems but outputs with newlines by default,
# so we pipe through tr to strip them off.
BASE64_CMD=$(echo -n "$CURL_COMMAND" | base64 | tr -d '\n')

echo "Broadcasting debug notification..."
adb shell am broadcast \
  -a com.example.pushapp.DEBUG_NOTIFICATION \
  -n com.example.pushapp/.DebugBroadcastReceiver \
  --es type x1 \
  --es curlCommandBase64 "$BASE64_CMD" \
  --es endpointURL "$ENDPOINT_URL"