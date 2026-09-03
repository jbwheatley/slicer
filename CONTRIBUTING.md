# Contributing

You can try out both the mill and sbt entry points in the example project. This requires first a local publish i.e. `sbt publishLocal`. 

Before running the tests you need to create the test corpuses - Scala projects that aim to cover all possible Scala code and build
configurations. Do this by running `sbt buildCorpuses`. The you can run `sbt commitCheck` to run the linting checks and tests.

## Style/formatting

FP preferred. `scalafmt` and `scalafix` are in place. There must be strong justification for `scalafix` rules to be overridden. 
`sbt scalafmtAll` and `sbt scalafixAll` to apply formatting and linting. 

## Using Claude Code / other LLMs

I have included the hooks and CLAUDE.md I use for local development using Claude, but there are no settings enabled by default. 
These are just guidelines, copy `settings.example.json` into your `settings.local.json` if you want to use them. I find them useful
and that they produce better, less annoying output and workflows when asking the robot to do something. 

There are two hard rules that I will ask to be fixed in PRs if they aren't followed - code comments and co-authoring. 
Claude loves to add overly verbose comments everywhere it goes and I think they add little if anything to readability, and
often times create confusion when they ultimately drift. Any comments written should be intended for humans to read first and foremost, 
and as such should be written by humans. Comments should be used only to inform human maintainers about decision points, gotchas, 
and obscure information about how the code works. 
If you contribute to this repository there should be no misunderstanding that you are 100% responsible for the code you are submitting. 
I don't care if you used an LLM, but co-authorship passes some of the buck to the robot. We don't tag commits with all the other tools
that we use to produce code, LLMs should not be put on a pedestal. To my mind, authorship==ownership. Instead feel free to write some 
attribution of the tools used in the PR description. 

