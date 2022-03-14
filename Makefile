phase = all

all: help

clean:
	$(MAKE) -C prev22 clean
	rm -rf prev22/gen prev22/src/prev/phase/**/.antlr

compile:
	$(MAKE) -C prev22 all

help:
	@echo "Usage: make [clean|compile|test|zip] (arguments)"
	@echo
	@echo "Options:"
	@echo "    clean                                   Delete artifacts"
	@echo "    compile                                 Compile the compiler"
	@echo "    help                                    Print this message"
	@echo "    test file=<file> (phase=<phase>)        Run the specified test"
	@echo "    zip name=<name>                         Create a zip archive"

test:
    ifdef file
		$(MAKE) -C prev22 clean all
		$(MAKE) -C prev22/prg "$(file)" PHASE="$(phase)"
    else
		@echo "Test file must be specified!"
		@echo "Usage: make test file=<file> (phase=<phase>)"
    endif

zip:
    ifdef name
		$(MAKE) -C prev22 clean
		rm -rf prev22/gen prev22/src/prev/phase/**/.antlr
		zip -r "$(name).zip" prev22 -x prev22/prev22.iml -x prev22/prg/*.p22
    else
		@echo "Zip name must be specified!"
		@echo "Usage: make zip name=<name>"
    endif
