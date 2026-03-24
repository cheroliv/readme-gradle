#noinspection CucumberUndefinedStep
@cucumber @readme
Feature: Minimal ReadMe configuration

  Scenario: Canary
    Given a new ReadMe project
    When I am executing the task 'tasks'
    Then the build should succeed
