# ADempiere Business Processors

<p align="center">
  <a href="https://adoptium.net/es/temurin/releases/?version=11">
    <img src="https://badgen.net/badge/Java/11/orange" alt="Java">
  </a>
  <a href="https://github.com/adempiere/adempiere-business-processors/actions/workflows/ci.yml">
    <img src="https://github.com/adempiere/adempiere-business-processors/actions/workflows/ci.yml/badge.svg" alt="Build GH Action">
  </a>
  <a href="https://github.com/adempiere/adempiere-business-processors/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/license-GNU/GPL%20(v2)-blue" alt="License">
  </a>
  <a href="https://github.com/adempiere/adempiere-business-processors/releases/latest">
    <img src="https://img.shields.io/github/release/adempiere/adempiere-business-processors.svg" alt="GitHub release">
  </a>
  <a href="https://discord.gg/T6eH6A7PJZ">
    <img src="https://badgen.net/badge/discord/join%20chat" alt="Discord">
  </a>
</p>

A project for convert all processors to process and allows run it as ADempiere Process.

The main scope for this project is allows run all processors with other scheduler instead ADempiere processor. Just now exists many schedulers that are created for run complex task and other executions.

I add a implementation for [dKron Processor](https://dkron.io/) because is very easy for install, very intuitive and simple, you are free of add more implementation for others task processors.

![dKron](docs/dKron.png)

See the follow menu

![Main Menu](docs/Main_Menu.png)

Also see a movie:

![Main Menu](docs/Run_Processor_From_Process.gif)

## Requirements
- [JDK 11 or later](https://adoptium.net/)
- [Gradle 8.0.1 or later](https://gradle.org/install/)
- Optional [dKron Processor](https://dkron.io/)


## Binary Project

You can get all binaries from github [here](https://central.sonatype.com/artifact/io.github.adempiere/adempiere-business-processors/1.0.0).

All contruction is from github actions


## Some XML's:

All dictionary changes are writing from XML and all XML's hare `xml/migration`


## How to add this library?

Is very easy.

- Gradle

```Java
implementation 'io.github.adempiere:adempiere-business-processors:1.0.1'
```

- SBT

```
libraryDependencies += "io.github.adempiere" % "adempiere-business-processors" % "1.0.1"
```

- Apache Maven

```
<dependency>
    <groupId>io.github.adempiere</groupId>
    <artifactId>adempiere-business-processors</artifactId>
    <version>1.0.1</version>
</dependency>
```

## Example for it

Here a example exporting processors from ADempiere to [dKron Processor](https://dkron.io/)

![Exporting processors](docs/Create_dKron_Setup.gif)


Using [dKron Processor](https://dkron.io/) after export processors from ADempiere look:

![Running with dKron](docs/dKron_Running.gif)


## References

- For install dKron you can follow these [indications](https://dkron.io/docs/basics/installation)
- This project just add dictionary changes and ADempiere changes for export processors and use it as process instead, you can look [adempiere-middleware](https://github.com/adempiere/adempiere-middleware) for ADempiere gRPC service based
- The gPRC service can be converted to Rest API service using the [adempiere_backend_rs](https://github.com/adempiere/adempiere_backend_rs) project, a Rust project!
