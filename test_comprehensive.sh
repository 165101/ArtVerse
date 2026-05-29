#!/bin/bash
BASE="http://localhost:8000"
PASS=0
FAIL=0

pass() { echo "  PASS: $1"; ((PASS++)); }
fail() { echo "  FAIL: $1 - $2"; ((FAIL++)); }

echo "=== Comprehensive Integration Test Suite ==="
echo ""

# Setup
RESP=$(curl -s -X POST "$BASE/api/stories" -H "Content-Type: application/json" -d '{"title":"Comprehensive Test","description":"Full test"}')
STORY_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
RESP=$(curl -s -X POST "$BASE/api/stories/$STORY_ID/chapters")
CHAPTER_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Setup: story=$STORY_ID, chapter=$CHAPTER_ID"

# --- Story Operations ---
echo "--- Story Operations ---"

# Update story
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/stories/$STORY_ID" -H "Content-Type: application/json" -d '{"title":"Updated Title","description":"Updated desc"}')
[ "$CODE" = "200" ] && pass "Update story" || fail "Update story" "HTTP $CODE"

# Get story details
RESP=$(curl -s "$BASE/api/stories/$STORY_ID")
if echo "$RESP" | grep -q "Updated Title"; then
  pass "Get story details"
else
  fail "Get story details" "Title not updated"
fi

# --- Chapter Operations ---
echo "--- Chapter Operations ---"

# Get chapter
RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID")
if echo "$RESP" | grep -q "chapter_number"; then
  pass "Get chapter details"
else
  fail "Get chapter details" "Missing fields"
fi

# Create second chapter
RESP=$(curl -s -X POST "$BASE/api/stories/$STORY_ID/chapters")
CHAPTER2_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$CHAPTER2_ID" ]; then
  pass "Create second chapter"
else
  fail "Create second chapter" "No ID"
fi

# List chapters
RESP=$(curl -s "$BASE/api/stories/$STORY_ID/chapters")
if echo "$RESP" | grep -q "$CHAPTER_ID" && echo "$RESP" | grep -q "$CHAPTER2_ID"; then
  pass "List chapters (both present)"
else
  fail "List chapters" "Missing chapters"
fi

# --- Color Mode ---
echo "--- Color Mode ---"

RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/color-mode")
if echo "$RESP" | grep -q "bw"; then
  pass "Get default color mode (bw)"
else
  fail "Get default color mode" "Expected bw"
fi

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/color-mode" -H "Content-Type: application/json" -d '{"color_mode":"color"}')
[ "$CODE" = "200" ] && pass "Set color mode to color" || fail "Set color mode" "HTTP $CODE"

RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/color-mode")
if echo "$RESP" | grep -q "color"; then
  pass "Verify color mode changed"
else
  fail "Verify color mode" "Still bw"
fi

# --- Image Count ---
echo "--- Image Count ---"

RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/image-count")
if echo "$RESP" | grep -q "image_count"; then
  pass "Get image count"
else
  fail "Get image count" "Missing field"
fi

for count in 4 6 8 10 12 15 20; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/image-count" -H "Content-Type: application/json" -d "{\"image_count\":$count}")
  [ "$CODE" = "200" ] && pass "Set image count=$count" || fail "Set image count=$count" "HTTP $CODE"
done

# Invalid counts
for count in 1 3 5 7 25 100; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/image-count" -H "Content-Type: application/json" -d "{\"image_count\":$count}")
  [ "$CODE" = "400" ] && pass "Reject invalid count=$count" || fail "Reject invalid count=$count" "Expected 400, got $CODE"
done

# --- Novel Import ---
echo "--- Novel Import ---"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/chapters/$CHAPTER_ID/import-novel" -H "Content-Type: application/json" -d '{"content":"Once upon a time in a magical land..."}')
[ "$CODE" = "200" ] && pass "Import novel" || fail "Import novel" "HTTP $CODE"

# --- Chat ---
echo "--- Chat ---"

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/chapters/$CHAPTER_ID/chat" -H "Content-Type: application/json" -d '{"message":"Hello, tell me about the story"}')
CODE=$(echo "$RESP" | tail -1)
[ "$CODE" = "200" ] && pass "Chat message" || fail "Chat message" "HTTP $CODE"

# --- Asset Groups ---
echo "--- Asset Groups ---"

# Create asset group
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/stories/$STORY_ID/asset-groups" -H "Content-Type: application/json" -d '{"name":"Main Characters","character_profiles":"Hero: brave warrior\nVillain: dark mage"}')
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
  GROUP_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  pass "Create asset group (id=$GROUP_ID)"
else
  fail "Create asset group" "HTTP $CODE"
fi

# List asset groups
RESP=$(curl -s "$BASE/api/stories/$STORY_ID/asset-groups")
if echo "$RESP" | grep -q "Main Characters"; then
  pass "List asset groups"
else
  fail "List asset groups" "Missing group"
fi

# Update asset group
if [ -n "$GROUP_ID" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/asset-groups/$GROUP_ID" -H "Content-Type: application/json" -d '{"name":"Updated Characters","characters":"New hero profile"}')
  [ "$CODE" = "200" ] && pass "Update asset group" || fail "Update asset group" "HTTP $CODE"
fi

# Set chapter asset group
if [ -n "$GROUP_ID" ]; then
  RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/asset-group" -H "Content-Type: application/json" -d "{\"group_id\":$GROUP_ID}")
  CODE=$(echo "$RESP" | tail -1)
  BODY=$(echo "$RESP" | sed '$d')
  if [ "$CODE" = "200" ] && echo "$BODY" | grep -q "selected_group_id"; then
    pass "Set chapter asset group"
  else
    fail "Set chapter asset group" "HTTP $CODE or missing field"
  fi
fi

# Unset chapter asset group
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/asset-group" -H "Content-Type: application/json" -d '{"group_id":null}')
[ "$CODE" = "200" ] && pass "Unset chapter asset group" || fail "Unset chapter asset group" "HTTP $CODE"

# Delete asset group
if [ -n "$GROUP_ID" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/asset-groups/$GROUP_ID")
  [ "$CODE" = "204" ] && pass "Delete asset group" || fail "Delete asset group" "HTTP $CODE"
fi

# --- Cleanup ---
echo "--- Cleanup ---"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/chapters/$CHAPTER_ID")
[ "$CODE" = "204" ] && pass "Delete chapter 1" || fail "Delete chapter 1" "HTTP $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/chapters/$CHAPTER2_ID")
[ "$CODE" = "204" ] && pass "Delete chapter 2" || fail "Delete chapter 2" "HTTP $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/stories/$STORY_ID")
[ "$CODE" = "204" ] && pass "Delete story" || fail "Delete story" "HTTP $CODE"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
