# FastR-GPU Compiler 

This is a fork of [FastR](https://github.com/oracle/fastr) with GPU support. The GPU backend is an extension of GraalVM and Graal Compiler with OpenCL code generation
and data management for GPU computing. 

The goal of the FastR-GPU compiler is to automatically execute R expressions on the GPU. It extends Truffle, Graal and a GPU backend for Graal (Marawacc) for compiling an R program into OpenCL. It profiles and specialises R input programs to OpenCL using the Graal Partial Evaluator and compiles the resulting intermediate representation to OpenCL at runtime.
This project is a research prototype. 

## Example

```R
> marawacc.deviceInfo()
NAME             : Hawaii
VENDOR           : Advanced Micro Devices, Inc.
TYPE             : GPU
DRIVER           : 1598.5 (VM)
MAX COMPUTE UNITS: 44
MAX FREQUENCY    : 1030
GLOBAL MEMORY    : 3221225472
LOCAL  MEMORY    : 32768
ENDIANESS        : LITTLE_ENDIAN


> marawacc.init()
> size <- 100000
> # It is executed on the GPU when the R program is JIT by Graal
> mapply(function(x, y) x * y, runif(size), runif(size)) 

```

## Installation

Get the `mx` tool:

```bash
$ mkdir ~/fastr-gpu/
$ cd fastr-gpu
$ git clone https://github.com/graalvm/mx
$ cd mx 
$ git checkout 900cc06  
$ cd -
$ export PATH=$PWD/mx:$PATH
```

Create source file: 

```bash
export PATH=/path/to/mx/mx/:$PATH
export JAVA_HOME=/path/to/jdk1.8x/
export DEFAULT_VM="server"
```


Get the R & Marawacc (OpenCL) JIT compilers:

```bash
$ mkdir fastr-gpu
$ cd fastr-gpu
$ git clone git@github.com:jjfumero/jvmci-marawacc.git jvmci     ## Download JVMCI dependency
$ git clone git@github.com:jjfumero/truffle-marawacc.git truffle ## Download Truffle dependency
$ git clone https://github.com/jjfumero/graal-marawacc graal     ## Download Graal dependency
$ git clone git@github.com:jjfumero/marawacc.git marawacc
$ export JAVA_HOME=/home/juan/bin/jdk1.8.0_91
$ export DEFAULT_VM="server"
$ export PATH=/home/juan/bin/gcc/gcc-5.4.0/bin/:$PATH
$ export LD_LIBRARY_PATH=/home/juan/bin/gcc/gcc-5.4.0/lib64/:$LD_LIBRARY_PATH 
$ export PATH=$PWD/mx:$PATH
$ cd marawacc
$ make 
```

Build fastr-gpu:

```bash
$ cd ..
$ git clone git@github.com:jjfumero/fastr-gpu.git
$ cd fastr-gpu
$ make 
$ . source.sh 
```

Build done!!! 


### Eclipse 

To generate the Eclipse files:

```bash
$ make eclipse 
```

Then import the projects into eclipse 

### Note

This compiler has been tested on Linux Fedora 21/22/23, CentOS 7.4 - CentOS 7.9 and OpenSuse 13 with OpenJDK >= 1.8_61.

**Current implementation with JDK 8 u91.**


## Publications 

- Juan Fumero. **Accelerating interpreted programming languages on GPUs with just-in-time compilation and runtime optimisations**. PhD Dissertation, 30/11/2017 - [Link](https://era.ed.ac.uk/handle/1842/28718)

- Juan Fumero, Michel Steuwer, Lukas Stadler, and Christophe Dubach.  **Just-In-Time GPU Compilation for Interpreted Languages with Partial Evaluation.** In Proceedings of the 13th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments (VEE '17). ACM, New York, NY, USA, 60-73. DOI: https://doi.org/10.1145/3050748.3050761 

- Juan Fumero, Michel Steuwer, Lukas Stadler, Christophe Dubach. **OpenCL JIT Compilation for Dynamic Programming Language.** MoreVMs 2017. [http://conf.researchr.org/event/MoreVMs-2017/morevms-2017-papers-opencl-jit-compilation-for-dynamic-programming-languages](http://conf.researchr.org/event/MoreVMs-2017/morevms-2017-papers-opencl-jit-compilation-for-dynamic-programming-languages)


## License

GPL V2

## Who do I talk to?

This project is a research prototype implemented at The University of Edinburgh. The project was partially funded by Oracle Labs.

## Main Developer

    Juan Fumero < juan.fumero @ manchester.ac.uk >

## Advisors

    Christophe Dubach < christophe.dubach @ ed.ac.uk >
    Michel Steuwer < michel.steuwer @ ed.ac.uk >
    Lukas Stadler < lukas.stadler @ oracle.com >


# FastR

FastR is an implementation of the [R Language](http://www.r-project.org/) in Java atop [Truffle and Graal](http://openjdk.java.net/projects/graal/).
Truffle is a framework for building self-optimizing AST interpreters.
Graal is a dynamic compiler that is used to generate efficient machine code from partially evaluated Truffle ASTs.

FastR is an open-source effort of Purdue University, Johannes Kepler University Linz, and Oracle Labs.

For more details and instructions for downloading and building the system, please visit the [FastR Wiki](https://github.com/oracle/fastr).
