Feature: Generate README from truth sources

  Scenario: Process a single language README
#    Given a project with a "README_truth.adoc" source file
#    And the file contains a PlantUML diagram named "architecture"
#    When I run "processReadme"
#    Then "README.adoc" is generated
#    And ".github/workflows/readmes/images/en/architecture.png" exists
#    And "README.adoc" contains "image::...architecture.png"

  Scenario: Scaffold creates missing files
#    Given a fresh project with no configuration
#    When I run "scaffoldReadme"
#    Then "readme-truth.yml" is created with placeholder token
#    And ".github/workflows/readme_truth.yml" is created

  Scenario: Scaffold respects existing files
#    Given a project with an existing "readme-truth.yml"
#    When I run "scaffoldReadme"
#    Then "readme-truth.yml" is unchanged