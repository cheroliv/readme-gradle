@cucumber @readme @process
Feature: Process README truth sources

  Background:
    Given a new ReadMe project
    And a "readme-truth.yml" with the following yaml values:
      | key       | value             |
      | git.token | <YOUR_GITHUB_PAT> |
    And the git remote validator is mocked with result "TOKEN_PLACEHOLDER"

  # ── Empty project ─────────────────────────────────────────────────────────

  Scenario: processReadme succeeds with no README_truth files
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level | keyword      | value |
      | WARN  | README_truth | found |

  # ── PlantUML block delimiters ─────────────────────────────────────────────

  Scenario: processReadme replaces PlantUML block delimited with ----
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ----
      Alice -> Bob : hello
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "image::"
    And the file "README.adoc" should not contain "[plantuml"
    And the following files should exist:
      | file                                                  |
      | .github/workflows/readmes/images/en/architecture.png |

  Scenario: processReadme replaces PlantUML block delimited with ````
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ````
      Alice -> Bob : hello
      ````
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "image::"
    And the file "README.adoc" should not contain "[plantuml"

  Scenario: processReadme replaces PlantUML block with long delimiter ------
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ------
      Alice -> Bob : hello
      ------
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "image::"

  Scenario: processReadme does not match mismatched delimiters
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ----
      Alice -> Bob : hello
      ````
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should not contain "image::"
    And the file "README.adoc" should contain "[plantuml"

  # ── PlantUML body ─────────────────────────────────────────────────────────

  Scenario: processReadme wraps body without @startuml/@enduml
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, diagram, png]
      ----
      Alice -> Bob : hello
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the following files should exist:
      | file                                             |
      | .github/workflows/readmes/images/en/diagram.png  |

  Scenario: processReadme preserves body with explicit @startuml/@enduml
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, diagram, png]
      ----
      @startuml
      Alice -> Bob : hello
      @enduml
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the following files should exist:
      | file                                             |
      | .github/workflows/readmes/images/en/diagram.png  |

  # ── Image path ────────────────────────────────────────────────────────────

  Scenario: generated image:: path is relative to the source file
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ----
      Alice -> Bob : hello
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "image::.github/workflows/readmes/images/en/architecture.png"

  Scenario: generated image:: path is correct when project is nested under git root
    Given the project is nested under a git root
    And the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ----
      Alice -> Bob : hello
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "image::../.github/workflows/readmes/images/en/architecture.png"
    And the image file should exist at the git root ".github/workflows/readmes/images/en/architecture.png"

  # ── Inter-language links ──────────────────────────────────────────────────

  Scenario: processReadme rewrites href= inter-language links
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      ++++
      <a href="README_truth_fr.adoc">FR</a>
      ++++
      """
    And the file "README_truth_fr.adoc" exists with the following content:
      """
      = Français
      ++++
      <a href="README_truth.adoc">EN</a>
      ++++
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "href=\"README_fr.adoc\""
    And the file "README_fr.adoc" should contain "href=\"README.adoc\""

  Scenario: processReadme rewrites link: inter-language links
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      link:README_truth_fr.adoc[FR]
      """
    And the file "README_truth_fr.adoc" exists with the following content:
      """
      = Français
      link:README_truth.adoc[EN]
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "link:README_fr.adoc[FR]"
    And the file "README_fr.adoc" should contain "link:README.adoc[EN]"

  Scenario: processReadme rewrites xref: inter-language links
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      xref:README_truth_fr.adoc[FR]
      """
    And the file "README_truth_fr.adoc" exists with the following content:
      """
      = Français
      xref:README_truth.adoc[EN]
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "xref:README_fr.adoc[FR]"
    And the file "README_fr.adoc" should contain "xref:README.adoc[EN]"

  Scenario: processReadme does not rewrite links to non-truth files
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      link:CONTRIBUTING.adoc[Contributing]
      xref:docs/setup.adoc[Setup]
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "link:CONTRIBUTING.adoc[Contributing]"
    And the file "README.adoc" should contain "xref:docs/setup.adoc[Setup]"

  # ── Multi-language ────────────────────────────────────────────────────────

  Scenario: processReadme generates README.adoc and README_fr.adoc
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      """
    And the file "README_truth_fr.adoc" exists with the following content:
      """
      = Français
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the following files should exist:
      | file           |
      | README.adoc    |
      | README_fr.adoc |

  Scenario: cross-language links are consistent after generation
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      link:README_truth_fr.adoc[FR]
      """
    And the file "README_truth_fr.adoc" exists with the following content:
      """
      = Français
      link:README_truth.adoc[EN]
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should contain "link:README_fr.adoc"
    And the file "README_fr.adoc" should contain "link:README.adoc"
    And the file "README.adoc" should not contain "README_truth"
    And the file "README_fr.adoc" should not contain "README_truth"

  # ── Project nested under git root ─────────────────────────────────────────

  Scenario: processReadme works when project is nested under git root
    Given the project is nested under a git root
    And the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, architecture, png]
      ----
      Alice -> Bob : hello
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should exist
    And the image file should exist at the git root ".github/workflows/readmes/images/en/architecture.png"
    And the file "README.adoc" should contain "image::../"

  # ── Error cases ───────────────────────────────────────────────────────────

  Scenario: processReadme logs warning when no README_truth file found
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level | keyword      | value |
      | WARN  | README_truth | found |

  # ── Robustness ────────────────────────────────────────────────────────────

  Scenario: processReadme warns when inter-language link targets a non-existent truth file
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      link:README_truth_de.adoc[DE]
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level | keyword         | value |
      | WARN  | README_truth_de | exist |
    And the file "README.adoc" should contain "link:README_de.adoc[DE]"

  Scenario: processReadme warns when a diagram name is duplicated in the same file
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test

      [plantuml, architecture, png]
      ----
      Alice -> Bob : hello
      ----

      [plantuml, architecture, png]
      ----
      Bob -> Alice : world
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level | keyword      | value     |
      | WARN  | architecture | duplicate |

  Scenario: processReadme warns when PlantUML syntax is invalid
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test
      [plantuml, broken, png]
      ----
      @@@invalid syntax@@@
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level | keyword | value   |
      | WARN  | broken  | invalid |

  Scenario: processReadme logs each processed source file
    Given the file "README_truth.adoc" exists with the following content:
      """
      = English
      """
    And the file "README_truth_fr.adoc" exists with the following content:
      """
      = Français
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level     | keyword            | value |
      | ╔═    | README_truth.adoc | lang  |
      | ╔═    | README_truth_fr   | lang  |

  Scenario: processReadme logs diagram replacement count per file
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Test

      [plantuml, diagram1, png]
      ----
      Alice -> Bob : hello
      ----

      [plantuml, diagram2, png]
      ----
      Bob -> Alice : world
      ----
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the build log should contain the following entries:
      | level     | keyword | value   |
      | OUT   | 2       | diagram |

  Scenario: processReadme generates README.adoc even when source has no diagrams
    Given the file "README_truth.adoc" exists with the following content:
      """
      = Hello World

      Just some text, no diagrams.

      == Section

      More text here.
      """
    When I am executing the task "processReadme"
    Then the build should succeed
    And the file "README.adoc" should exist
    And the file "README.adoc" should contain "Just some text, no diagrams."
    And the build log should contain the following entries:
      | level     | keyword | value   |
      | OUT   | 0       | diagram |
