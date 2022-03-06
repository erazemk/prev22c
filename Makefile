stage = all

all: help

clean:
	$(MAKE) -C prev22 clean

compile: clean
	$(MAKE) -C prev22 all

help:
	@echo "Usage: make [clean|test|zip] (arguments)"
	@echo
	@echo "Options:"
	@echo "    clean                                 Delete artifacts"
	@echo "    compile                               Compile the compiler"
	@echo "    help                                  Print this message"
	@echo "    test phase=[phase] file=[filename]    Run the specified test"
	@echo "    zip name=[name]                       Create a zip archive"
	@echo
	@echo "Available phases:"
	@echo "    lexan    Lexical analysis (PrevLexer)"
	@echo "    synan    Syntactical analysis (PrevParser)"

test: compile
	$(MAKE) -C prev22/prg "$(file)" PHASE="$(phase)"

zip: compile
	zip -r "$(name).zip" prev22 -x prev22/prev22.iml
