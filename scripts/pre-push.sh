#!/bin/sh

# =============================================================================
# Pre-push Git Hook for KMP Product Flavors Plugin
# =============================================================================
# This hook runs before pushing to remote to ensure CI will pass.
# Install with: ./scripts/setup-hooks.sh
#
# For comprehensive checks, run: ./ci-prepush.sh
# =============================================================================

set -e

# Navigate to project root
cd "$(git rev-parse --show-toplevel)"

# Function to check the current branch
check_current_branch() {
    printf "\n🚀 Checking the current git branch...\n"
    CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
    if [ "$CURRENT_BRANCH" = "main" ] || [ "$CURRENT_BRANCH" = "master" ] || [ "$CURRENT_BRANCH" = "development" ]; then
        echo "⚠️  You're pushing to '$CURRENT_BRANCH' branch."
        echo "   Make sure you have the necessary permissions and approvals."
    else
        echo "✅ Pushing from '$CURRENT_BRANCH' branch."
    fi
}

# Function to run Spotless checks
run_spotless_checks() {
    printf "\n🎨 Running Spotless check...\n"

    if ! ./gradlew spotlessCheck --daemon > /tmp/spotless-result 2>&1; then
        cat /tmp/spotless-result
        rm -f /tmp/spotless-result
        printf "\n*********************************************************************************\n"
        echo "   💥 Spotless found formatting issues!"
        echo "   💡 Run './gradlew spotlessApply' to fix formatting."
        printf "*********************************************************************************\n"

        printf "\n🔧 Attempting to apply Spotless fixes...\n"
        if ./gradlew spotlessApply --daemon > /tmp/spotless-result 2>&1; then
            rm -f /tmp/spotless-result
            echo "✅ Formatting fixed! Please commit the changes and push again."
            exit 1
        else
            cat /tmp/spotless-result
            rm -f /tmp/spotless-result
            echo "❌ Could not auto-fix formatting. Please fix manually."
            exit 1
        fi
    else
        rm -f /tmp/spotless-result
        echo "✅ Code formatting check passed! ✨"
    fi
}

# Function to run Detekt checks
run_detekt_checks() {
    printf "\n🔍 Running Detekt static analysis...\n"

    if ! ./gradlew detekt --daemon > /tmp/detekt-result 2>&1; then
        cat /tmp/detekt-result
        rm -f /tmp/detekt-result
        printf "\n*********************************************************************************\n"
        echo "   💥 Detekt found code quality issues!"
        echo "   💡 Review the Detekt report and fix the issues."
        printf "*********************************************************************************\n"
        exit 1
    else
        rm -f /tmp/detekt-result
        echo "✅ Detekt analysis passed! 🎯"
    fi
}

# Function to build plugin (quick verification)
build_plugin() {
    printf "\n🔨 Building plugin...\n"

    if ! ./gradlew :build-logic:flavor-plugin:assemble --daemon > /tmp/build-result 2>&1; then
        cat /tmp/build-result
        rm -f /tmp/build-result
        printf "\n*********************************************************************************\n"
        echo "   💥 Plugin build failed!"
        echo "   💡 Fix the compilation errors before pushing."
        printf "*********************************************************************************\n"
        exit 1
    else
        rm -f /tmp/build-result
        echo "✅ Plugin build passed! 🔨"
    fi
}

# Function to build sample (quick verification)
build_sample() {
    printf "\n📦 Building sample project...\n"

    if ! ./gradlew :samples:basic-flavors:assemble -PkmpFlavor=freeDev --daemon > /tmp/sample-result 2>&1; then
        cat /tmp/sample-result
        rm -f /tmp/sample-result
        printf "\n*********************************************************************************\n"
        echo "   💥 Sample build failed!"
        echo "   💡 Fix the compilation errors before pushing."
        printf "*********************************************************************************\n"
        exit 1
    else
        rm -f /tmp/sample-result
        echo "✅ Sample build passed! 📦"
    fi
}

# Function to print success message
print_success_message() {
    GIT_USERNAME=$(git config user.name)
    printf "\n*********************************************************************************\n"
    echo "🚀🎉 Congratulations, $GIT_USERNAME!"
    echo "Your code has passed all pre-push checks!"
    echo ""
    echo "💡 For comprehensive CI checks, run: ./ci-prepush.sh"
    printf "*********************************************************************************\n\n"
}

# Main script execution
check_current_branch
run_spotless_checks
run_detekt_checks
build_plugin
build_sample
print_success_message

exit 0
