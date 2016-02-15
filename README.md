# ASTx

This is a clone of [FastR] (https://bitbucket.org/allr/fastr/) with GPU support. 
The GPU backend is an extension of GraalVM and Graal Compiler with OpenCL code generation
and data management for GPU computing. 

The goal of ASTx is to execute potential R level expressions on the GPU by using Graal and the GPU backend. 

## Example


```
#!R

> gpu.deviceInfo()
NAME:         Intel(R) Core(TM) i5-3470 CPU @ 3.20GHz
VENDOR: Intel(R) Corporation
TYPE: CPU
DRIVER: 1.2.0.57

> gpu.parallelMap( 1:100000, function(x, y) x * y, 1:100000) 
```

# Installation


```
#!bash

$ mkdir astx-compiler
$ cd astx-compiler
$ mx clone ssh://hg@bitbucket.org/juanfumero/astx
$ cd astx
$ make 
```


# FastR

FastR is an implementation of the [R Language](http://www.r-project.org/) in Java atop [Truffle and Graal](http://openjdk.java.net/projects/graal/).
Truffle is a framework for building self-optimizing AST interpreters.
Graal is a dynamic compiler that is used to generate efficient machine code from partially evaluated Truffle ASTs.

FastR is an open-source effort of Purdue University, Johannes Kepler University Linz, and Oracle Labs.

For more details and instructions for downloading and building the system, please visit the [FastR Wiki](https://bitbucket.org/allr/fastr/wiki/Home).