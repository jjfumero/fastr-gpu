#!/bin/bash

# Run the largest data size we report in VEE paper

function saxpy {
	size=33554432
	./runbench benchmarks/fastR/saxpy/saxpySeq.R $size > saxpySeq.log
	./runbench benchmarks/fastR/saxpy/saxpyGPU.R $size > saxpyASTxFull.log
	./runbench benchmarks/fastR/saxpy/saxpyGPUPArrays.R $size > saxpyASTx.log
}

function bs {
	size=4194304
	./runbench benchmarks/fastR/blackscholes/blackscholesSeq.R $size > bsSeq.log
	./runbench benchmarks/fastR/blackscholes/blackscholesGPU.R $size > bsASTxFull.log
	./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R $size > bsASTx.log
}

function nbody {
	size=262144
	./runbench benchmarks/fastR/nbody/nbodySeq.R $size > nbodySeq.log
	./runbench benchmarks/fastR/nbody/nbodyGPU.R $size > nbodyASTxFull.log
	./runbench benchmarks/fastR/nbody/nbodyGPUPArrays.R $size > nbodyASTx.log
}

function dft() {
	size=32768
	./runbench benchmarks/fastR/dft/dftSeq.R $size > dftSeq.log
	./runbench benchmarks/fastR/dft/dftGPU.R $size > dftASTxFull.log
	./runbench benchmarks/fastR/dft/dftGPUPArrays.R $size > dftASTx.log
}

function mandelbrot() {
	size=4096
	./runbench benchmarks/fastR/mandelbrot/mandelbrotSeq.R $size > mandelbrotSeq.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotGPU.R $size > mandelbrotASTxFull.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotGPUPArrays.R $size > mandelbrotASTx.log
}

function kmeans() {
	size=8388608
	./runbench benchmarks/fastR/kmeans/kmeansSeq.R $size > kmeansSeq.log
	./runbench benchmarks/fastR/kmeans/kmeansGPU.R $size > kmeansASTxFull.log
	./runbench benchmarks/fastR/kmeans/kmeansGPUPArrays.R $size > kmeansASTx.log
}

function hilbert() {
	size=8192
	./runbench benchmarks/fastR/hilbert/hilbertSeq.R $size > hilbertSeq.log
	./runbench benchmarks/fastR/hilbert/hilbertGPU.R $size > hilbertASTxFull.log
	./runbench benchmarks/fastR/hilbert/hilbertGPUPArrays.R $size > hilbertASTx.log
}

function spectralNorm() {
	size=131072
	./run benchmarks/fastR/spectralNorm/spectralNormSeq.R $size > spectralNormSeq.log
	./run benchmarks/fastR/spectralNorm/spectralNormGPU.R $size > spectralNormASTxFull.log
	./run benchmarks/fastR/spectralNorm/spectralNormGPUPArrays.R $size > spectralNormASTx.log
}

## main
saxpy
bs
nbody
dft
mandelbrot
montecarlo
kmeans
hilbert
spectralNorm
