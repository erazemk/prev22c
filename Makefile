phase = all
debug =
nregs = 8

all: help

build:
    ifdef file
		@$(MAKE) -s -C prev22 clean all
		@$(MAKE) -s -C prev22/prg "$(file)" PHASE="$(phase)" DEBUG="$(debug)" NREGS="$(nregs)"
    else
		@echo "Missing test file name!"
		@$(MAKE) -s help
    endif

clean:
	@$(MAKE) -s -C prev22 clean
	@rm -rf out prev22/gen prev22/src/prev/phase/**/.antlr

help:
	@echo "Usage: make [clean|build|run|zip] (arguments)"
	@echo
	@echo "Options:"
	@echo "    build file=<file>    Compile the program"
	@echo "      (nregs=x)          The number of registers to use"
	@echo "      (phase=<phase>)    The phase to compile to (default is 'all')"
	@echo "      (debug=true)       Enable debug mode"
	@echo "    clean                Delete artifacts"
	@echo "    help                 Print this message"
	@echo "    run file=<file>      Run the program"
	@echo "      (nregs=x)          The number of registers to use"
	@echo "    zip name=<name>      Create a zip archive (xxxxxxxx-yy format)"

run:
	@$(MAKE) -s build file="$(file)" nregs="$(nregs)"
	@mmixal prev22/prg/$(file).mms
	@mmix prev22/prg/$(file).mmo

zip:
    ifdef name
		@$(MAKE) -s -C prev22 clean
		@rm -rf out prev22/gen prev22/src/prev/phase/**/.antlr ./*.zip prev22/lib/antlr-*.jar
		@zip -r "$(name).zip" prev22 -x prev22/prev22.iml -x prev22/prg/*.p22
    else
		@echo "Zip name must be specified!"
		@$(MAKE) -s help
    endif
