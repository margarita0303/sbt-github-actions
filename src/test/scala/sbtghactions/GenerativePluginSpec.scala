/*
 * Copyright 2020 Daniel Spiewak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sbtghactions

import org.specs2.mutable.Specification

class GenerativePluginSpec extends Specification {
  import GenerativePlugin._

  val header = """# This file was automatically generated by sbt-github-actions using the
# githubWorkflowGenerate task. You should add and commit this file to
# your git repository. It goes without saying that you shouldn't edit
# this file by hand! Instead, if you wish to make changes, you should
# change your sbt build configuration to revise the workflow description
# to meet your needs, then regenerate this file.
"""

  "workflow compilation" should {
    "produce the appropriate skeleton around a zero-job workflow" in {
      val expected = header + """
        |name: test
        |
        |on:
        |  pull_request:
        |    branches: [master]
        |  push:
        |    branches: [master]
        |
        |jobs:
        |  """.stripMargin

      compileWorkflow("test", List("master"), PREventType.Defaults, Map(), Nil, "sbt") mustEqual expected
    }

    "respect non-default pr types" in {
      val expected = header + """
        |name: test
        |
        |on:
        |  pull_request:
        |    branches: [master]
        |    types: [ready_for_review, review_requested, opened]
        |  push:
        |    branches: [master]
        |
        |jobs:
        |  """.stripMargin

      compileWorkflow("test", List("master"), List(PREventType.ReadyForReview, PREventType.ReviewRequested, PREventType.Opened), Map(), Nil, "sbt") mustEqual expected
    }

    "compile a one-job workflow targeting multiple branch patterns with an environment" in {
      val expected = header + s"""
        |name: test2
        |
        |on:
        |  pull_request:
        |    branches: [master, backport/v*]
        |  push:
        |    branches: [master, backport/v*]
        |
        |env:
        |  GITHUB_TOKEN: $${{ secrets.GITHUB_TOKEN }}
        |
        |jobs:
        |  build:
        |    name: Build and Test
        |    strategy:
        |      matrix:
        |        os: [ubuntu-latest]
        |        scala: [2.13.1]
        |        java: [adopt@1.8]
        |    runs-on: $${{ matrix.os }}
        |    steps:
        |      - run: echo Hello World""".stripMargin

      compileWorkflow(
        "test2",
        List("master", "backport/v*"),
        PREventType.Defaults,
        Map(
          "GITHUB_TOKEN" -> s"$${{ secrets.GITHUB_TOKEN }}"),
        List(
          WorkflowJob(
            "build",
            "Build and Test",
            List(WorkflowStep.Run(List("echo Hello World"))))),
        "sbt") mustEqual expected
    }

    "compile a workflow with two jobs" in {
      val expected = header + s"""
        |name: test3
        |
        |on:
        |  pull_request:
        |    branches: [master]
        |  push:
        |    branches: [master]
        |
        |jobs:
        |  build:
        |    name: Build and Test
        |    strategy:
        |      matrix:
        |        os: [ubuntu-latest]
        |        scala: [2.13.1]
        |        java: [adopt@1.8]
        |    runs-on: $${{ matrix.os }}
        |    steps:
        |      - run: echo yikes
        |
        |  what:
        |    name: If we just didn't
        |    strategy:
        |      matrix:
        |        os: [ubuntu-latest]
        |        scala: [2.13.1]
        |        java: [adopt@1.8]
        |    runs-on: $${{ matrix.os }}
        |    steps:
        |      - run: whoami""".stripMargin

      compileWorkflow(
        "test3",
        List("master"),
        PREventType.Defaults,
        Map(),
        List(
          WorkflowJob(
            "build",
            "Build and Test",
            List(WorkflowStep.Run(List("echo yikes")))),

          WorkflowJob(
            "what",
            "If we just didn't",
            List(WorkflowStep.Run(List("whoami"))))),
        "") mustEqual expected
    }
  }

  "step compilation" should {
    import WorkflowStep._

    "compile a simple run without a name" in {
      compileStep(Run(List("echo hi")), "") mustEqual "- run: echo hi"
    }

    "compile a simple run with a name" in {
      compileStep(
        Run(
          List("echo hi"),
          name = Some("nomenclature")),
        "") mustEqual "- name: nomenclature\n  run: echo hi"
    }

    "compile a simple run with a name declaring the shell" in {
      compileStep(
        Run(
          List("echo hi"),
          name = Some("nomenclature")),
        "",
        true) mustEqual "- name: nomenclature\n  shell: bash\n  run: echo hi"
    }

    "omit shell declaration on Use step" in {
      compileStep(
        Use(
          "repo",
          "slug",
          0),
        "",
        true) mustEqual "- uses: repo/slug@v0"
    }

    "compile sbt using the command provided" in {
      compileStep(
        Sbt(List("show scalaVersion", "compile", "test")),
        "$SBT") mustEqual s"- run: $$SBT ++$${{ matrix.scala }} 'show scalaVersion' compile test"
    }

    "compile use without parameters" in {
      compileStep(
        Use("olafurpg", "setup-scala", 5),
        "") mustEqual "- uses: olafurpg/setup-scala@v5"
    }

    "compile use with two parameters" in {
      compileStep(
        Use("olafurpg", "setup-scala", 5, params = Map("abc" -> "def", "cafe" -> "@42")),
        "") mustEqual "- uses: olafurpg/setup-scala@v5\n  with:\n    abc: def\n    cafe: '@42'"
    }

    "compile use with two parameters and an environment" in {
      compileStep(
        Use(
          "derp",
          "nope",
          0,
          params = Map("teh" -> "schizzle", "think" -> "positive"),
          env = Map("hi" -> "there")),
        "") mustEqual "- env:\n    hi: there\n  uses: derp/nope@v0\n  with:\n    teh: schizzle\n    think: positive"
    }

    "compile a run step with multiple commands" in {
      compileStep(Run(List("whoami", "echo yo")), "") mustEqual "- run: |\n    whoami\n    echo yo"
    }

    "compile a run step with a conditional" in {
      compileStep(
        Run(List("users"), cond = Some("true")),
        "") mustEqual "- if: true\n  run: users"
    }
  }

  "job compilation" should {
    "compile a simple job with two steps" in {
      val results = compileJob(
        WorkflowJob(
          "bippy",
          "Bippity Bop Around the Clock",
          List(
            WorkflowStep.Run(List("echo hello")),
            WorkflowStep.Checkout)),
        "")

      results mustEqual s"""bippy:
  name: Bippity Bop Around the Clock
  strategy:
    matrix:
      os: [ubuntu-latest]
      scala: [2.13.1]
      java: [adopt@1.8]
  runs-on: $${{ matrix.os }}
  steps:
    - run: echo hello

    - name: Checkout current branch (fast)
      uses: actions/checkout@v2"""
    }

    "compile a job with one step and three oses" in {
      val results = compileJob(
        WorkflowJob(
          "derp",
          "Derples",
          List(
            WorkflowStep.Run(List("echo hello"))),
          oses = List("ubuntu-latest", "windows-latest", "macos-latest")),
        "")

      results mustEqual s"""derp:
  name: Derples
  strategy:
    matrix:
      os: [ubuntu-latest, windows-latest, macos-latest]
      scala: [2.13.1]
      java: [adopt@1.8]
  runs-on: $${{ matrix.os }}
  steps:
    - shell: bash
      run: echo hello"""
    }

    "compile a job with java setup, two JVMs and two Scalas" in {
      val results = compileJob(
        WorkflowJob(
          "abc",
          "How to get to...",
          List(
            WorkflowStep.SetupScala),
          scalas = List("2.12.10", "2.13.1"),
          javas = List("adopt@1.8", "graal@20.0.0")),
        "")

      results mustEqual s"""abc:
  name: How to get to...
  strategy:
    matrix:
      os: [ubuntu-latest]
      scala: [2.12.10, 2.13.1]
      java: [adopt@1.8, graal@20.0.0]
  runs-on: $${{ matrix.os }}
  steps:
    - name: Setup Java and Scala
      uses: olafurpg/setup-scala@v5
      with:
        java-version: $${{ matrix.java }}"""
    }

    "compile a job environment, conditional, and needs with an sbt step" in {
      val results = compileJob(
        WorkflowJob(
          "nada",
          "Moooo",
          List(
            WorkflowStep.Sbt(List("+compile"))),
          env = Map("not" -> "now"),
          cond = Some("boy != girl"),
          needs = List("unmet")),
        "csbt")

      results mustEqual s"""nada:
  name: Moooo
  needs: [unmet]
  if: boy != girl
  strategy:
    matrix:
      os: [ubuntu-latest]
      scala: [2.13.1]
      java: [adopt@1.8]
  runs-on: $${{ matrix.os }}
  env:
    not: now
  steps:
    - run: csbt ++$${{ matrix.scala }} +compile"""
    }


    "compile a job with additional matrix components" in {
      val results = compileJob(
        WorkflowJob(
          "bippy",
          "Bippity Bop Around the Clock",
          List(
            WorkflowStep.Run(List("echo ${{ matrix.test }}")),
            WorkflowStep.Checkout),
          matrixAdds = Map("test" -> List("1", "2"))),
        "")

      results mustEqual s"""bippy:
  name: Bippity Bop Around the Clock
  strategy:
    matrix:
      os: [ubuntu-latest]
      scala: [2.13.1]
      java: [adopt@1.8]
      test: [1, 2]
  runs-on: $${{ matrix.os }}
  steps:
    - run: echo $${{ matrix.test }}

    - name: Checkout current branch (fast)
      uses: actions/checkout@v2"""
    }
  }

  "predicate compilation" >> {
    import RefPredicate._
    import Ref._

    "equals" >> {
      compileBranchPredicate("thingy", Equals(Branch("other"))) mustEqual "thingy == 'refs/heads/other'"
    }

    "contains" >> {
      compileBranchPredicate("thingy", Contains(Tag("other"))) mustEqual "(startsWith(thingy, 'refs/tags/') && contains(thingy, 'other'))"
    }

    "startsWith" >> {
      compileBranchPredicate("thingy", StartsWith(Branch("other"))) mustEqual "startsWith(thingy, 'refs/heads/other')"
    }

    "endsWith" >> {
      compileBranchPredicate("thingy", EndsWith(Branch("other"))) mustEqual "(startsWith(thingy, 'refs/heads/') && endsWith(thingy, 'other'))"
    }
  }

  "pr event type compilation" >> {
    import PREventType._

    "assigned" >> (compilePREventType(Assigned) mustEqual "assigned")
    "unassigned" >> (compilePREventType(Unassigned) mustEqual "unassigned")
    "labeled" >> (compilePREventType(Labeled) mustEqual "labeled")
    "unlabeled" >> (compilePREventType(Unlabeled) mustEqual "unlabeled")
    "opened" >> (compilePREventType(Opened) mustEqual "opened")
    "edited" >> (compilePREventType(Edited) mustEqual "edited")
    "closed" >> (compilePREventType(Closed) mustEqual "closed")
    "reopened" >> (compilePREventType(Reopened) mustEqual "reopened")
    "synchronize" >> (compilePREventType(Synchronize) mustEqual "synchronize")
    "ready_for_review" >> (compilePREventType(ReadyForReview) mustEqual "ready_for_review")
    "locked" >> (compilePREventType(Locked) mustEqual "locked")
    "unlocked" >> (compilePREventType(Unlocked) mustEqual "unlocked")
    "review_requested" >> (compilePREventType(ReviewRequested) mustEqual "review_requested")
    "review_request_removed" >> (compilePREventType(ReviewRequestRemoved) mustEqual "review_request_removed")
  }
}
