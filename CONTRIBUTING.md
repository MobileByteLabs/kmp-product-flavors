# Contributing to KMP Product Flavors

Thank you for your interest in contributing! This document provides guidelines and instructions for contributing.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Set up the development environment

### Prerequisites

- JDK 17 or higher
- Kotlin 2.0+
- Gradle 8.0+

### Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/kmp-product-flavors.git
cd kmp-product-flavors

# Set up git hooks (recommended)
./scripts/setup-hooks.sh

# Build the project
./gradlew :build-logic:flavor-plugin:assemble
```

## Development Workflow

### Branch Strategy

- `main` - Stable release branch
- `development` - Active development branch (PRs target this)
- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates

### Pre-Push Checks (IMPORTANT!)

**Always run pre-push checks before pushing to avoid CI failures:**

```bash
# Full CI check (recommended before pushing)
./ci-prepush.sh

# With auto-fix for formatting issues
./ci-prepush.sh --fix

# Quick check (skips sample builds)
./ci-prepush.sh --quick
```

The `ci-prepush.sh` script mirrors the exact checks that CI runs:

| Check | Command | Description |
|-------|---------|-------------|
| Spotless | `spotlessCheck` | Code formatting (ktlint) |
| Detekt | `detekt` | Static analysis |
| Build Plugin | `:build-logic:flavor-plugin:assemble` | Plugin compilation |
| Build Samples | `:samples:basic-flavors:assemble` | Sample project builds |
| Validate Flavors | `validateFlavors` | Flavor configuration validation |

### Code Style

This project uses:
- **Spotless** with ktlint for code formatting
- **Detekt** for static analysis

```bash
# Check formatting
./gradlew spotlessCheck

# Auto-fix formatting
./gradlew spotlessApply

# Run static analysis
./gradlew detekt
```

### Building & Testing

```bash
# Build the plugin
./gradlew :build-logic:flavor-plugin:assemble

# Build sample with specific flavor
./gradlew :samples:basic-flavors:assemble -PkmpFlavor=freeDev
./gradlew :samples:basic-flavors:assemble -PkmpFlavor=paidProd

# Validate flavor configuration
./gradlew :samples:basic-flavors:validateFlavors

# List all available flavors
./gradlew :samples:basic-flavors:listFlavors

# Publish to local Maven for testing
./MavenLocalRelease.sh
```

### Testing Plugin Changes Locally

1. Build and publish to local Maven:
   ```bash
   ./MavenLocalRelease.sh
   ```

2. In a test project, add `mavenLocal()` to repositories:
   ```kotlin
   // settings.gradle.kts
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
       }
   }
   ```

3. Apply the plugin:
   ```kotlin
   plugins {
       id("io.github.mobilebytelabs.kmp-product-flavors") version "1.0.0-alpha01"
   }
   ```

## Pull Request Process

1. Create a feature branch from `development`
2. Make your changes
3. Run `./ci-prepush.sh` to ensure all checks pass
4. Update documentation if needed
5. Submit a pull request to `development`

### PR Checklist

- [ ] Ran `./ci-prepush.sh` successfully
- [ ] Code follows the project style guidelines
- [ ] Documentation updated if needed
- [ ] Commit messages are clear and descriptive
- [ ] PR description explains the changes

## Project Structure

```
kmp-product-flavors/
├── build-logic/
│   └── flavor-plugin/       # The Gradle plugin source
│       └── src/main/kotlin/
├── samples/
│   ├── basic-flavors/       # Basic usage sample
│   └── kmp-template-integration/  # Full KMP project integration
├── .github/workflows/
│   ├── build.yml            # CI workflow
│   └── publish.yml          # Release workflow
├── ci-prepush.sh            # Pre-push CI check script
└── MavenLocalRelease.sh     # Local testing script
```

## Reporting Issues

When reporting issues, please include:

- Plugin version
- Kotlin version
- Gradle version
- Platform(s) affected
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs or stack traces

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 license.
