## LSP4E Changelog and New and Noteworthy

### 0.19.0

📅 Release Date: August 17th, 2021

* Use newer LSP4J, bringing compatibility with LSP 3.16

### 0.18.0

📅 Release Date: March 2nd, 2021

* New Capailities
    * _"Bug 564491 - Replace deprecated editorInput variable in enabledWhen"_ allows to more easily use LSP4E in a TextViewer, outside of an editor
    * _"Enable a long running LS start() method"_ can allow slower language server to work with LSP4E, while this was previously impossible
* Bugfixes and other improvements about already existing capability
    * Ensure LS is valid when triggering didChangeWorkspaceFolder
    * [#571162] initialize folding capabilities properly.
    * Bug 570527: launch setBreakpoints and configurationDone after initialized
    * Bug 569714: delay projects watch in workspace job
    * Allow DSPDebugTarget to complete earlier for non-debug launch
    * Enable a long running LS start() method
    * Make `window/logMessage` to report to Eclipse log instead of Console
    * Bug 564491 - Replace deprecated editorInput variable in enabledWhen
    * Bug 569345 - duplicate entries in open declaration (F3 and CTRL click)
* LSP4E Development utilities
    * Bug 569270: Add .options to build.properties to make debug/tracing discoverable in PDE
* Dependencies, releng & build work
    * Update target platform to newer Eclipse release
    * Build with Tycho 2.2.0
    * Move lsp4e.debug BREE to Java 11.
    * Enable automated dependency IP check (dash license tool) in CI



### v0.17.0 (Nov 2020)

* Target Platform updated to:
    * SimRel 2020-09, including Eclipse Platform 4.17
    * Eclipse LSP4J 0.10.0
* Java 11 is required to build LSP4E and run tests
* Java 11 is required to run some of the bundles. For now just the org.eclipse.lsp4e.jdt and test bundles. In a future release it may encompass all the bundles. The Eclipse Platform now has bundles that require Java 11 and some of those bundles may be dependencies of LSP4E.
* Highlight.js is now being used for lsp4e's completion documentation. See [Bug 565496](https://bugs.eclipse.org/bugs/show_bug.cgi?id=565496)
* LSP4E's format menu item has been relocated Generic Editor Source submenu
* New icon for unit completion kind. See [Bug 567812 ](https://bugs.eclipse.org/bugs/show_bug.cgi?id=567812)
* [CommandExecutor](org.eclipse.lsp4e/src/org/eclipse/lsp4e/command/CommandExecutor.java) has been updated with the improvements that were originally developed in WildWebDeveloper. Migrating those changes back to LSP4E makes these improvements available for all consumers of LSP4E.

See also the [Bugzilla issues](https://bugs.eclipse.org/bugs/buglist.cgi?product=lsp4e&target_milestone=0.17.0) for details of bug fixes and other items changed in this release.

Breaking Changes:

* Eclipse LSP4J has some breaking API changes. While LSP4E's directly declared API did not change,
  places where LSP4J types are exposed to LSP4E's client code there may be some API change effects.
  Please consult [LSP4J v0.10.0](https://github.com/eclipse/lsp4j/releases/tag/v0.10.0) for more
  details.

---

### Older releases

In Release 0.17.0 The Changelog was migrated to live in the LSP4E git repo. For older
versions, please see the [release records on the PMI](https://projects.eclipse.org/projects/technology.lsp4e)