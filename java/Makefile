JAVAC := javac
JAVA  := java
SRC   := $(shell find src -name "*.java")
OUT   := out

build: $(OUT)/.stamp

$(OUT)/.stamp: $(SRC)
	@mkdir -p $(OUT)
	$(JAVAC) -d $(OUT) $(SRC)
	@touch $(OUT)/.stamp

run: build
	$(JAVA) -Dtodo.data=../data/tasks.json -cp $(OUT) todo.Main $(ARGS)

clean:
	rm -rf $(OUT)

.PHONY: build run clean
