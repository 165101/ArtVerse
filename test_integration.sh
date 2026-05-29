#!/bin/bash
BASE="http://localhost:8000"
PASS=0
FAIL=0
TEST_ID=""

pass() { echo "  PASS: $1"; ((PASS++)); }
fail() { echo "  FAIL: $1 - $2"; ((FAIL++)); }

echo "=== ArtVerse Integration Test Suite ==="
echo ""

# --- Story CRUD ---
echo "--- Story CRUD ---"

# Create story
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/stories" -H "Content-Type: application/json" -d '{"title":"Integration Test","description":"Automated test"}')
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
  STORY_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  if [ -n "$STORY_ID" ]; then
    pass "Create story (id=$STORY_ID)"
  else
    fail "Create story" "No ID in response"
  fi
else
  fail "Create story" "HTTP $CODE"
fi

# List stories
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/stories")
[ "$CODE" = "200" ] && pass "List stories" || fail "List stories" "HTTP $CODE"

# Get story
if [ -n "$STORY_ID" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/stories/$STORY_ID")
  [ "$CODE" = "200" ] && pass "Get story" || fail "Get story" "HTTP $CODE"
fi

# --- Chapter CRUD ---
echo "--- Chapter CRUD ---"

if [ -n "$STORY_ID" ]; then
  # Create chapter
  RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/stories/$STORY_ID/chapters")
  CODE=$(echo "$RESP" | tail -1)
  BODY=$(echo "$RESP" | sed '$d')
  if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
    CHAPTER_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
    pass "Create chapter (id=$CHAPTER_ID)"
  else
    fail "Create chapter" "HTTP $CODE"
  fi

  # List chapters
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/stories/$STORY_ID/chapters")
  [ "$CODE" = "200" ] && pass "List chapters" || fail "List chapters" "HTTP $CODE"

  # Get chapter
  if [ -n "$CHAPTER_ID" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/chapters/$CHAPTER_ID")
    [ "$CODE" = "200" ] && pass "Get chapter" || fail "Get chapter" "HTTP $CODE"
  fi
fi

# --- Color Mode ---
echo "--- Color Mode ---"

if [ -n "$CHAPTER_ID" ]; then
  # Get color mode
  RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/color-mode")
  if echo "$RESP" | grep -q "color_mode"; then
    pass "Get color mode"
  else
    fail "Get color mode" "Missing field"
  fi

  # Update color mode
  RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/color-mode" -H "Content-Type: application/json" -d '{"color_mode":"color"}')
  CODE=$(echo "$RESP" | tail -1)
  if [ "$CODE" = "200" ]; then
    pass "Update color mode"
  else
    fail "Update color mode" "HTTP $CODE"
  fi
fi

# --- Image Count ---
echo "--- Image Count ---"

if [ -n "$CHAPTER_ID" ]; then
  # Get image count
  RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/image-count")
  if echo "$RESP" | grep -q "image_count"; then
    pass "Get image count"
  else
    fail "Get image count" "Missing field"
  fi

  # Update image count (valid)
  RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/image-count" -H "Content-Type: application/json" -d '{"image_count":8}')
  CODE=$(echo "$RESP" | tail -1)
  [ "$CODE" = "200" ] && pass "Update image count (valid)" || fail "Update image count (valid)" "HTTP $CODE"

  # Update image count (invalid - should return 400)
  RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/image-count" -H "Content-Type: application/json" -d '{"image_count":5}')
  CODE=$(echo "$RESP" | tail -1)
  [ "$CODE" = "400" ] && pass "Update image count (invalid=400)" || fail "Update image count (invalid)" "Expected 400, got $CODE"
fi

# --- Asset Groups ---
echo "--- Asset Groups ---"

if [ -n "$STORY_ID" ]; then
  # Create asset group
  RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/stories/$STORY_ID/asset-groups" -H "Content-Type: application/json" -d '{"name":"Test Group","character_profiles":"Hero: brave"}')
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
  if echo "$RESP" | grep -q "Test Group"; then
    pass "List asset groups"
  else
    fail "List asset groups" "Missing group"
  fi

  # Set chapter asset group
  if [ -n "$CHAPTER_ID" ] && [ -n "$GROUP_ID" ]; then
    RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/asset-group" -H "Content-Type: application/json" -d "{\"group_id\":$GROUP_ID}")
    CODE=$(echo "$RESP" | tail -1)
    BODY=$(echo "$RESP" | sed '$d')
    if [ "$CODE" = "200" ]; then
      if echo "$BODY" | grep -q "selected_group_id"; then
        pass "Set chapter asset group"
      else
        fail "Set chapter asset group" "Missing selected_group_id in response"
      fi
    else
      fail "Set chapter asset group" "HTTP $CODE"
    fi

    # Get chapter asset group
    RESP=$(curl -s "$BASE/api/chapters/$CHAPTER_ID/asset-group")
    if echo "$RESP" | grep -q "selected_group_id"; then
      pass "Get chapter asset group"
    else
      fail "Get chapter asset group" "Missing field"
    fi

    # Unset chapter asset group
    RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/chapters/$CHAPTER_ID/asset-group" -H "Content-Type: application/json" -d '{"group_id":null}')
    CODE=$(echo "$RESP" | tail -1)
    [ "$CODE" = "200" ] && pass "Unset chapter asset group" || fail "Unset chapter asset group" "HTTP $CODE"
  fi
fi

# --- Cleanup ---
echo "--- Cleanup ---"

if [ -n "$CHAPTER_ID" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/chapters/$CHAPTER_ID")
  [ "$CODE" = "204" ] && pass "Delete chapter" || fail "Delete chapter" "HTTP $CODE"
fi

if [ -n "$STORY_ID" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/stories/$STORY_ID")
  [ "$CODE" = "204" ] && pass "Delete story" || fail "Delete story" "HTTP $CODE"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
