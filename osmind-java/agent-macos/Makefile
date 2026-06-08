SDK_PATH := $(shell xcrun --show-sdk-path 2>/dev/null)
BUILD_DIR := build
BIN := $(BUILD_DIR)/osmind-es-collector
SRC := src/osmind_es_collector.c

CFLAGS := -std=c11 -Wall -Wextra -Wpedantic -O2 -fblocks
LDFLAGS := -framework EndpointSecurity -framework Foundation -lbsm

.PHONY: all clean check-sdk

all: check-sdk $(BIN)

check-sdk:
	@if [ -z "$(SDK_PATH)" ]; then \
		echo "xcrun could not find a macOS SDK. Install Xcode or Command Line Tools."; \
		exit 1; \
	fi
	@if [ ! -d "$(SDK_PATH)/System/Library/Frameworks/EndpointSecurity.framework" ]; then \
		echo "EndpointSecurity.framework was not found in the active macOS SDK: $(SDK_PATH)"; \
		echo "Install/repair Xcode or Command Line Tools, then run: xcode-select -p"; \
		exit 1; \
	fi

$(BIN): $(SRC)
	@mkdir -p $(BUILD_DIR)
	$(CC) $(CFLAGS) -isysroot "$(SDK_PATH)" -o $@ $< $(LDFLAGS)

clean:
	rm -rf $(BUILD_DIR)
