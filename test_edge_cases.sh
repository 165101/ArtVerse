#!/bin/bash
BASE="http://localhost:8000"
PASS=0
FAIL=0

pass() { echo "  PASS: $1"; ((PASS++)); }
fail() { echo "  FAIL: $1 - $2"; ((FAIL++)); }

echo "=== Edge Case Tests ==="

# Test invalid story ID
echo "--- Invalid IDs ---"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/stories/99999")
[ "$CODE" = "404" ] && pass "Get nonexistent story (404)" || fail "Get nonexistent story" "Expected 404, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/chapters/99999")
[ "$CODE" = "404" ] && pass "Get nonexistent chapter (404)" || fail "Get nonexistent chapter" "Expected 404, got $CODE"

# Test invalid color mode
echo "--- Invalid Color Mode ---"
# Create test story + chapter
RESP=$(curl -s -X POST "$BASE/api/stories" -H "Content-Type: application/json" -d '{"title":"Edge Test","description":"test"}')
STORY_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
RESP=$(curl -s -X POST "$BASE/api/stories/$STORY_ID/chapters")
CHAPTER_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/color-mode" -H "Content-Type: application/json" -d '{"color_mode":"invalid"}')
[ "$CODE" = "400" ] || [ "$CODE" = "500" ] && pass "Invalid color mode returns error" || fail "Invalid color mode" "Expected 400/500, got $CODE"

# Test image count with scenes
echo "--- Image Count After Scenes ---"
# Import novel first
curl -s -X POST "$BASE/api/chapters/$CHAPTER_ID/import-novel" -H "Content-Type: application/json" -d '{"content":"Test novel content for scenes"}' > /dev/null

# Test image count update before scenes (should work)
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/image-count" -H "Content-Type: application/json" -d '{"image_count":6}')
[ "$CODE" = "200" ] && pass "Update image count before scenes" || fail "Update image count before scenes" "HTTP $CODE"

# Cleanup
curl -s -X DELETE "$BASE/api/chapters/$CHAPTER_ID" > /dev/null
curl -s -X DELETE "$BASE/api/stories/$STORY_ID" > /dev/null

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
