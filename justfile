set shell := ["bash", "-uc"]

default:
    @just --list

# Build the shaded plugin jar
build:
    ./gradlew build

# Run a Paper dev server on localhost:25000 with FAWE
run:
    ./gradlew runServer

# Run the JUnit test suite
test:
    ./gradlew test

# Format Kotlin sources with ktlint
format:
    ./gradlew ktlintFormat

# Check formatting without changing files
format-check:
    ./gradlew ktlintCheck

# Clean build outputs
clean:
    ./gradlew clean
