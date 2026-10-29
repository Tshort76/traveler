# Traveler — build entry points. Same shape as BioDex: every target resolves JDK 17 and the
# Android SDK itself, so `make check` works in a fresh shell with nothing exported.

SHELL := /bin/bash
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 17 2>/dev/null)
SDK_DIR := $(shell sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)
GRADLE := JAVA_HOME="$(JAVA_HOME)" ANDROID_HOME="$(SDK_DIR)" ./gradlew
ADB := $(SDK_DIR)/platform-tools/adb
PKG := dev.tlong.traveler

.DEFAULT_GOAL := help
.PHONY: help doctor debug release install test check validate fixtures tools-test emulator emulator-stop screenshot push-examples clean

help: ## List the targets
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

doctor: ## Check the toolchain and say what is missing
	@ok=1; \
	if [ -z "$(JAVA_HOME)" ]; then echo "✗ JDK 17 not found"; ok=0; else echo "✓ JDK 17       $(JAVA_HOME)"; fi; \
	if [ ! -d "$(SDK_DIR)" ]; then echo "✗ local.properties needs sdk.dir=<android sdk>"; ok=0; else echo "✓ Android SDK  $(SDK_DIR)"; fi; \
	if [ -f keystore.properties ]; then echo "✓ release signing configured"; else echo "· no keystore.properties — release APK will be unsigned"; fi; \
	if [ -x "$(ADB)" ] && [ -n "$$($(ADB) devices | sed '1d;/^$$/d')" ]; then echo "✓ a device is attached"; else echo "· no device attached"; fi; \
	[ $$ok = 1 ]

debug: ## Build the debug APK
	@$(GRADLE) assembleDebug && echo "APK: app/build/outputs/apk/debug/app-debug.apk"

release: ## Build the release APK (unsigned without keystore.properties)
	@$(GRADLE) assembleRelease && echo "APK: app/build/outputs/apk/release/app-release.apk"

install: ## Build and install onto the attached phone or emulator
	@$(GRADLE) installDebug

test: ## JVM tests only (model, merge, editing, UI under Robolectric)
	@$(GRADLE) testDebugUnitTest

check: tools-test validate ## Everything runnable without a phone (the pre-commit gate)
	@$(GRADLE) testDebugUnitTest

validate: ## Validate every example trip file
	@python3 tools/validate_trip.py schema/examples/*.json

fixtures: ## Regenerate the derived example trips
	@python3 tools/make_fixtures.py

tools-test: ## The Python tools' own tests
	@cd tools && python3 -m unittest -q test_validate_trip test_stay_brief test_make_map

emulator: ## Start the 'traveler' emulator headless; stops when the starting Claude session exits unless KEEP=1
	@tools/emu/emulator.sh start

emulator-stop: ## Shut the emulator down (do this when you are done with it)
	@tools/emu/emulator.sh stop

push-examples: ## Copy the example trips to the device's Downloads for import testing
	@for f in schema/examples/*.json; do $(ADB) push "$$f" /sdcard/Download/ >/dev/null && echo "pushed $$f"; done

screenshot: ## Grab the device screen to shot.png
	@$(ADB) exec-out screencap -p > shot.png && echo "wrote shot.png"

clean: ## Delete build outputs
	@$(GRADLE) clean
