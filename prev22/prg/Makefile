JAVA = java --enable-preview
ANTLRDIR = ../lib/antlr-4.9.3-complete.jar
XSLTPROC = xsltproc
RM = 'rm' -fr

PHASE = all
LOG = all
NREGS = 8

.PHONY: %
%: %.p22
	$(RM) -fr $@.*.xml $@.*.html
    ifeq ($(DEBUG), true)
		if $(JAVA) -cp ../bin:../src:../lib/antlr-4.9.3-complete.jar prev.Compiler --debug --xsl=../lib/xsl/ --logged-phase=$(LOG) --target-phase=$(PHASE) --nregs=$(NREGS) $< ; then for XML in `'ls' -1 $@.*.xml` ; do $(XSLTPROC) $$XML -o `basename $$XML xml`html; done ; fi
    else
		if $(JAVA) -cp ../bin:../src:../lib/antlr-4.9.3-complete.jar prev.Compiler --xsl=../lib/xsl/ --logged-phase=$(LOG) --target-phase=$(PHASE) --nregs=$(NREGS) $< ; then for XML in `'ls' -1 $@.*.xml` ; do $(XSLTPROC) $$XML -o `basename $$XML xml`html; done ; fi
    endif

.PHONY: clean
clean:
	$(RM) *~
	$(RM) *xml *html
	$(RM) *.mmo *.mms temp_*.p22

