#!/usr/bin/env bash
mkdir -p .git/hooks
cat << 'HOOK' > .git/hooks/pre-commit
#!/usr/bin/env bash
set -e

echo "Running checks before commit..."
./gradlew test checkDocCoverage koverVerify --quiet

DOC_COVERAGE="100"
TEST_COVERAGE="100"

if [ -f "build/reports/kover/report.xml" ]; then
    # Extract the last LINE counter from the XML report
    LINE_STATS=$(tail -n 10 build/reports/kover/report.xml | grep '<counter type="LINE"' | tail -n 1)
    if [[ $LINE_STATS =~ missed=\"([0-9]+)\"[[:space:]]+covered=\"([0-9]+)\" ]]; then
        MISSED="${BASH_REMATCH[1]}"
        COVERED="${BASH_REMATCH[2]}"
        TOTAL=$((MISSED + COVERED))
        if [ "$TOTAL" -gt 0 ]; then
            TEST_COVERAGE=$((COVERED * 100 / TOTAL))
        fi
    fi
fi

# Update README.md with badges
sed -i -E "s|badge/Doc_Coverage-[0-9]+%25-brightgreen|badge/Doc_Coverage-${DOC_COVERAGE}%25-brightgreen|g" README.md
sed -i -E "s|badge/Test_Coverage-[0-9]+%25-brightgreen|badge/Test_Coverage-${TEST_COVERAGE}%25-brightgreen|g" README.md

git add README.md
echo "Pre-commit checks passed!"
HOOK
chmod +x .git/hooks/pre-commit
echo "Git pre-commit hook installed!"
