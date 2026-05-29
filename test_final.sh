#!/bin/bash
BASE="http://localhost:8000"
PASS=0
FAIL=0

pass() { echo "  PASS: $1"; ((PASS++)); }
fail() { echo "  FAIL: $1 - $2"; ((FAIL++)); }

echo "=== Final Verification Test ==="
echo ""

# Setup
RESP=$(curl -s -X POST "$BASE/api/stories" -H "Content-Type: application/json" -d '{"title":"Final Test","description":"Verification"}')
STORY_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
RESP=$(curl -s -X POST "$BASE/api/stories/$STORY_ID/chapters")
CHAPTER_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

# Test ref-images format
echo "--- Ref Images Format ---"
RESP=$(curl -s "$BASE/api/stories/$STORY_ID/ref-images")
if echo "$RESP" | grep -q '"max"'; then
  pass "Story ref-images has max field"
else
  fail "Story ref-images" "Missing max field"
fi

RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/ref-images")
if echo "$RESP" | grep -q '"max"'; then
  pass "Chapter ref-images has max field"
else
  fail "Chapter ref-images" "Missing max field"
fi

# Test asset group operations
echo "--- Asset Group Operations ---"
RESP=$(curl -s -X POST "$BASE/api/stories/$STORY_ID/asset-groups" -H "Content-Type: application/json" -d '{"name":"Test Group"}')
GROUP_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/asset-group" -H "Content-Type: application/json" -d "{\"group_id\":$GROUP_ID}")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$CODE" = "200" ] && echo "$BODY" | grep -q "selected_group_id"; then
  pass "Set/unset asset group works"
else
  fail "Set/unset asset group" "HTTP $CODE"
fi

# Cleanup
curl -s -X DELETE "$BASE/api/chapters/$CHAPTER_ID" > /dev/null
curl -s -X DELETE "$BASE/api/stories/$STORY_ID" > /dev/null

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
