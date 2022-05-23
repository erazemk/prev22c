phase = all
debug =
nregs = 8
nolink = false

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
    ifdef file
		@$(MAKE) -s -C prev22 clean all
		@cp prev22/prg/"$(file)".p22 prev22/prg/temp_"$(file)".p22

        ifneq ($(nolink), true)
			@cat prev22/prg/stdlib.p22 >> prev22/prg/temp_"$(file)".p22
        endif

		@$(MAKE) -s -C prev22/prg temp_"$(file)" PHASE="$(phase)" DEBUG="$(debug)" NREGS="$(nregs)"
		@mmixal -x prev22/prg/temp_"$(file)".mms
		@mmix prev22/prg/temp_"$(file)".mmo
    else
		@echo "Missing file name!"
		@$(MAKE) -s help
    endif

zip:
    ifdef name
		@$(MAKE) -s -C prev22 clean
		@rm -rf out prev22/gen prev22/src/prev/phase/**/.antlr ./*.zip prev22/lib/antlr-*.jar
		@zip -r "$(name).zip" prev22 -x prev22/prev22.iml -x prev22/prg/*.p22
    else
		@echo "Zip name must be specified!"
		@$(MAKE) -s help
    endif
