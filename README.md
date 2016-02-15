# ASTx

This is a clone of [FastR] (https://bitbucket.org/allr/fastr/) with GPU support. 
The GPU backend is an extension of GraalVM and Graal Compiler with OpenCL code generation
and data management for GPU computing. 

The goal of ASTx is to execute potential R level expressions on the GPU by using Graal and the GPU backend. 


## Example



# FastR

FastR is an implementation of the [R Language](http://www.r-project.org/) in Java atop [Truffle and Graal](http://openjdk.java.net/projects/graal/).
Truffle is a framework for building self-optimizing AST interpreters.
Graal is a dynamic compiler that is used to generate efficient machine code from partially evaluated Truffle ASTs.

FastR is an open-source effort of Purdue University, Johannes Kepler University Linz, and Oracle Labs.

For more details and instructions for downloading and building the system, please visit the [FastR Wiki](https://bitbucket.org/allr/fastr/wiki/Home).
