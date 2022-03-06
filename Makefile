all: help

zip: clean
	zip -r "$(name).zip" prev22 -x prev22/prev22.iml

clean:
	cd prev22 && $(MAKE) clean all

help:
	@echo "Usage: make [clean|zip] (arguments)"
	@echo
	@echo "Options:"
	@echo "    clean         Delete artifacts"
	@echo "    help          Print this message"
	@echo "    zip [name]    Create a zip archive"
